/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.network.internal;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.core.network.internal.behavior.VxNetSyncBehavior;
import net.xmx.velthoric.core.network.synchronization.behavior.VxSyncBehavior;
import net.xmx.velthoric.core.body.server.VxServerBodyDataStore;
import net.xmx.velthoric.core.body.server.VxServerBodyDataContainer;
import net.xmx.velthoric.core.body.server.VxServerBodyManager;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.network.internal.packet.S2CRemoveBodyBatchPacket;
import net.xmx.velthoric.core.network.internal.packet.S2CSpawnBodyBatchPacket;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.IVxNetPacket;
import net.xmx.velthoric.network.VxNetworking;
import net.xmx.velthoric.util.VxChunkUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The central controller for physics body network synchronization.
 * <p>
 * <b>Scalability Architecture:</b>
 * 1. <b>Grouping:</b> Dirty bodies are grouped by the chunk they reside in.
 * 2. <b>Serialization:</b> Data for each chunk is serialized into a raw binary stream once.
 * 3. <b>Compression:</b> The binary stream is compressed once per chunk using Zstd via {@link VxPacketFactory}.
 * 4. <b>Broadcasting:</b> The resulting compressed payload is sent to all players watching that chunk.
 * <p>
 * This architecture shifts the O(Players * Bodies) complexity to O(Chunks + Players),
 * drastically reducing CPU and GC overhead. The implementation uses Netty's PooledByteBuf
 * to eliminate virtually all allocations during the sync loop.
 *
 * @author xI-Mx-Ix
 */
public class VxNetworkDispatcher {

    /**
     * The Minecraft server level associated with this dispatcher.
     */
    private final ServerLevel level;

    /**
     * The manager handling physics body logic.
     */
    private final VxServerBodyManager manager;

    /**
     * The optimized Structure-of-Arrays data store for body properties.
     */
    private final VxServerBodyDataStore dataStore;

    /**
     * Factory used to generate zero-allocation packets.
     */
    private final VxPacketFactory packetFactory;

    /**
     * Frequency of the network synchronization thread in milliseconds.
     */
    private final int NETWORK_THREAD_TICK_RATE_MS = 10;

    /**
     * Maximum allowed bytes for a single packet payload to prevent network overflow.
     */
    private final int MAX_PACKET_PAYLOAD_SIZE = 128 * 1024;

    /**
     * Maps player UUIDs to the set of body network IDs they are currently tracking.
     */
    private final Map<UUID, IntSet> playerTrackedBodies = new ConcurrentHashMap<>();

    /**
     * Maps chunk position keys to the set of player UUIDs watching that chunk.
     * Maintained by the game thread via track/untrack calls, read by the network thread
     * to dispatch packets without touching the vanilla ChunkMap.
     */
    private final Map<Long, Set<UUID>> chunkWatchers = new ConcurrentHashMap<>();

    /**
     * Maps player UUIDs to their ServerPlayer instances for thread-safe resolution
     * from the network thread. Updated on the game thread when players join/leave.
     */
    private final Map<UUID, ServerPlayer> knownPlayers = new ConcurrentHashMap<>();

    /**
     * Reverse index: maps player UUIDs to the set of chunk positions they are watching.
     * Enables O(watched_chunks) disconnect cleanup instead of O(total_chunks).
     */
    private final Map<UUID, Set<Long>> playerToChunks = new ConcurrentHashMap<>();

    /**
     * Bodies waiting to be spawned for specific players, pending chunk readiness.
     */
    private final Map<UUID, ConcurrentLinkedQueue<VxBody>> pendingSpawns = new ConcurrentHashMap<>();

    /**
     * Network IDs waiting to be removed from specific players' clients.
     */
    private final Map<UUID, ConcurrentLinkedQueue<Integer>> pendingRemovals = new ConcurrentHashMap<>();

    /**
     * Dedicated thread executor for off-loading serialization and compression from the main thread.
     */
    private ExecutorService networkSyncExecutor;

    /**
     * Reusable map for grouping transform updates by chunk, cleared every sync cycle.
     */
    private final Long2ObjectMap<IntArrayList> dirtyBodiesByChunk = new Long2ObjectOpenHashMap<>();

    /**
     * Reusable map for grouping vertex updates by chunk, cleared every sync cycle.
     */
    private final Long2ObjectMap<IntArrayList> dirtyVerticesByChunk = new Long2ObjectOpenHashMap<>();

    /**
     * Reusable map for grouping shape updates by chunk, cleared every sync cycle.
     */
    private final Long2ObjectMap<IntArrayList> dirtyShapesByChunk = new Long2ObjectOpenHashMap<>();

    /**
     * Internal cache of indices to minimize the time spent inside the dataStore lock.
     */
    private final IntArrayList dirtyIndicesSnapshot = new IntArrayList(4096);

    /**
     * Pool of reusable IntArrayLists to prevent garbage collector pressure during grouping.
     */
    private final ObjectArrayList<IntArrayList> listPool = new ObjectArrayList<>();

    /**
     * Constructs a new dispatcher and initializes network tuning parameters from config.
     *
     * @param level   The server level.
     * @param manager The physics body manager.
     */
    public VxNetworkDispatcher(ServerLevel level, VxServerBodyManager manager) {
        this.level = level;
        this.manager = manager;
        this.dataStore = manager.getDataStore();
        this.packetFactory = new VxPacketFactory(manager);
    }

    /**
     * @return The associated body manager.
     */
    public VxServerBodyManager getManager() {
        return this.manager;
    }

    /**
     * Initializes and starts the dedicated network synchronization thread.
     */
    public void start() {
        this.networkSyncExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Velthoric-Network-Sync-Thread"));
        this.networkSyncExecutor.submit(this::runSyncLoop);
    }

    /**
     * Shuts down the network synchronization thread immediately.
     */
    public void stop() {
        if (this.networkSyncExecutor != null) {
            this.networkSyncExecutor.shutdownNow();
        }
    }

    /**
     * Entry point for game-thread synchronized tasks, called every server tick.
     * Processes event-driven updates like spawns and removals.
     */
    public void onGameTick() {
        processPendingRemovals();
        processPendingSpawns();
    }

    /**
     * The main execution loop for the asynchronous network thread.
     * Handles heavy-duty scanning, serialization, and compression.
     */
    private void runSyncLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                long start = System.nanoTime();

                // Phase 1: Identification & Grouping
                prepareUpdateBatches();

                // Phase 2: Serialization & Compression (the expensive part)
                List<BroadcastTask> broadcastTasks = serializeBatches();

                // Phase 3: Dispatching (directly on network thread using our own chunk→player tracking)
                if (!broadcastTasks.isEmpty()) {
                    dispatchBroadcasts(broadcastTasks);
                }

                // Sync custom data
                VxSyncBehavior behavior = this.manager.getBehaviorManager().getBehavior(VxSyncBehavior.ID);
                if (behavior != null) {
                    behavior.broadcastS2CUpdates(this.manager, this);
                }

                // Clean up grouping buffers and return them to the pool
                recycleLists();

                long durationMs = (System.nanoTime() - start) / 1_000_000;
                Thread.sleep(Math.max(0, NETWORK_THREAD_TICK_RATE_MS - durationMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Exception in Network Sync Loop", e);
            }
        }
    }

    /**
     * Scans the global data store for dirty flags and groups the indices of bodies
     * needing updates by their chunk coordinate.
     */
    private void prepareUpdateBatches() {
        dirtyIndicesSnapshot.clear();

        // Atomically retrieve dirty indices to minimize lock duration
        VxServerBodyDataContainer c = dataStore.serverCurrent();
        synchronized (dataStore) {
            if (c.dirtyIndices.isEmpty()) return;
            IntIterator it = c.dirtyIndices.iterator();
            while (it.hasNext()) {
                dirtyIndicesSnapshot.add(it.nextInt());
            }
            c.dirtyIndices.clear();
        }

        // Group indices by chunk outside the lock
        for (int i = 0; i < dirtyIndicesSnapshot.size(); i++) {
            int idx = dirtyIndicesSnapshot.getInt(i);
            if (idx >= c.getCapacity() || c.networkId[idx] == -1) continue;
            if (!VxNetSyncBehavior.ID.isSet(c.behaviorBits[idx])) continue;

            long chunkPosLong = c.chunkKey[idx];

            if (c.isTransformDirty[idx]) {
                getOrCreateList(dirtyBodiesByChunk, chunkPosLong).add(idx);
                c.isTransformDirty[idx] = false;
            }

            if (c.isVertexDataDirty[idx]) {
                getOrCreateList(dirtyVerticesByChunk, chunkPosLong).add(idx);
                c.isVertexDataDirty[idx] = false;
            }

            if (c.isShapeDirty[idx]) {
                getOrCreateList(dirtyShapesByChunk, chunkPosLong).add(idx);
                c.isShapeDirty[idx] = false;
            }
        }
    }

    /**
     * Helper to retrieve or create an IntArrayList for a specific chunk, utilizing the object pool.
     */
    private IntArrayList getOrCreateList(Long2ObjectMap<IntArrayList> map, long key) {
        IntArrayList list = map.get(key);
        if (list == null) {
            if (listPool.isEmpty()) {
                list = new IntArrayList(16);
            } else {
                list = listPool.remove(listPool.size() - 1);
                list.clear();
            }
            map.put(key, list);
        }
        return list;
    }

    /**
     * Returns all used lists to the pool and clears the grouping maps.
     */
    private void recycleLists() {
        for (IntArrayList list : dirtyBodiesByChunk.values()) {
            listPool.add(list);
        }
        for (IntArrayList list : dirtyVerticesByChunk.values()) {
            listPool.add(list);
        }
        for (IntArrayList list : dirtyShapesByChunk.values()) {
            listPool.add(list);
        }
        dirtyBodiesByChunk.clear();
        dirtyVerticesByChunk.clear();
        dirtyShapesByChunk.clear();
    }

    /**
     * Iterates over grouped dirty bodies and creates compressed binary packets for each chunk.
     * Delegates entirely to the VxPacketFactory for zero-allocation creation.
     *
     * @return A list of tasks containing the chunk coordinate and its corresponding pre-built packet.
     */
    private List<BroadcastTask> serializeBatches() {
        List<BroadcastTask> tasks = new ArrayList<>(dirtyBodiesByChunk.size() + dirtyVerticesByChunk.size());

        for (Long2ObjectMap.Entry<IntArrayList> entry : dirtyBodiesByChunk.long2ObjectEntrySet()) {
            tasks.add(new BroadcastTask(entry.getLongKey(), packetFactory.createStatePacket(entry.getLongKey(), entry.getValue(), level)));
        }

        for (Long2ObjectMap.Entry<IntArrayList> entry : dirtyVerticesByChunk.long2ObjectEntrySet()) {
            tasks.add(new BroadcastTask(entry.getLongKey(), packetFactory.createVertexPacket(entry.getLongKey(), entry.getValue())));
        }

        for (Long2ObjectMap.Entry<IntArrayList> entry : dirtyShapesByChunk.long2ObjectEntrySet()) {
            tasks.add(new BroadcastTask(entry.getLongKey(), packetFactory.createShapePacket(entry.getLongKey(), entry.getValue())));
        }

        return tasks;
    }

    /**
     * Sends pre-built packets to all players watching the respective chunks.
     * Runs directly on the network thread using the internal {@link #chunkWatchers} map
     * to avoid blocking on the server main thread. {@link VxNetworking#sendToPlayer} is
     * thread-safe (Netty pipeline), so no main-thread dispatch is needed.
     *
     * @param tasks The broadcast tasks generated by the network thread.
     */
    private void dispatchBroadcasts(List<BroadcastTask> tasks) {
        for (BroadcastTask task : tasks) {
            Set<UUID> watchers = chunkWatchers.get(task.chunkPos);

            if (watchers != null && !watchers.isEmpty()) {
                for (UUID uuid : watchers) {
                    ServerPlayer player = knownPlayers.get(uuid);
                    if (player != null) {
                        VxNetworking.sendToPlayer(player, task.packet);
                    }
                }
            }
            // Always release the pooled buffer after processing the task,
            // even if no players were watching the chunk.
            task.packet.release();
        }
    }

    /**
     * Called when a new body is added to the level.
     * Identifies all players watching the body's chunk and starts tracking it for them.
     *
     * @param body The body instance.
     */
    public void onBodyAdded(VxBody body) {
        int index = body.getDataStoreIndex();
        if (index == -1) return;
        VxServerBodyDataContainer c = dataStore.serverCurrent();
        ChunkPos bodyChunk = new ChunkPos(c.chunkKey[index]);

        // Iterate all players to see who needs to be notified of this new body
        for (ServerPlayer player : this.level.players()) {
            if (VxChunkUtil.isPlayerWatchingChunk(player, bodyChunk)) {
                trackBodyForPlayer(player, body);
            }
        }
    }

    /**
     * Called when a body is removed from the physics world.
     * Notifies all relevant players to remove the body from their clients.
     *
     * @param body The body instance being removed.
     */
    public void onBodyRemoved(VxBody body) {
        int index = body.getDataStoreIndex();
        // Even if the body is about to be removed, the DataStore index should still be valid
        // at this point in the lifecycle (called before cleanup).
        if (index == -1) return;

        VxServerBodyDataContainer c = dataStore.serverCurrent();
        ChunkPos chunkPos = new ChunkPos(c.chunkKey[index]);
        // Use the vanilla ChunkMap to find players who are currently watching this chunk.
        List<ServerPlayer> players = level.getChunkSource().chunkMap.getPlayers(chunkPos, false);

        if (!players.isEmpty()) {
            int networkId = body.getNetworkId();
            for (ServerPlayer player : players) {
                // We attempt to untrack/remove the body for each player.
                untrackBodyForPlayer(player, networkId);
            }
        }
    }

    /**
     * Handles visibility logic when a body moves across chunk boundaries.
     *
     * @param body The body instance.
     * @param from The previous chunk.
     * @param to   The new chunk.
     */
    public void onBodyMoved(VxBody body, ChunkPos from, ChunkPos to) {
        if (from.equals(to)) return;
        int networkId = body.getNetworkId();

        // Check visibility for all players to update tracking status
        for (ServerPlayer player : this.level.players()) {
            boolean seesTo = VxChunkUtil.isPlayerWatchingChunk(player, to);
            boolean seesFrom = VxChunkUtil.isPlayerWatchingChunk(player, from);

            if (seesTo && !seesFrom) {
                // Player entered range: Start tracking.
                trackBodyForPlayer(player, body);
            } else if (!seesTo && seesFrom) {
                // Player left range: Stop tracking.
                untrackBodyForPlayer(player, networkId);
            }
        }
    }

    /**
     * Bulk tracking update for a player entering a new chunk.
     * Registers the player as a chunk watcher and queues spawns for all bodies in that chunk.
     *
     * @param player   The player.
     * @param chunkPos The chunk.
     */
    public void trackBodiesInChunkForPlayer(ServerPlayer player, ChunkPos chunkPos) {
        UUID uuid = player.getUUID();
        long chunkKey = chunkPos.toLong();
        knownPlayers.put(uuid, player);
        chunkWatchers.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet()).add(uuid);
        playerToChunks.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(chunkKey);
        manager.getSpatialManager().forEachInChunk(chunkKey, body -> trackBodyForPlayer(player, body));
    }

    /**
     * Bulk tracking update for a player leaving a chunk.
     * Removes the player from chunk watchers and queues removals for all bodies in that chunk.
     *
     * @param player   The player.
     * @param chunkPos The chunk.
     */
    public void untrackBodiesInChunkForPlayer(ServerPlayer player, ChunkPos chunkPos) {
        UUID uuid = player.getUUID();
        long chunkKey = chunkPos.toLong();
        Set<UUID> watchers = chunkWatchers.get(chunkKey);
        if (watchers != null) {
            watchers.remove(uuid);
            if (watchers.isEmpty()) {
                chunkWatchers.remove(chunkKey);
            }
        }
        Set<Long> chunks = playerToChunks.get(uuid);
        if (chunks != null) {
            chunks.remove(chunkKey);
        }
        manager.getSpatialManager().forEachInChunk(chunkKey, body -> untrackBodyForPlayer(player, body.getNetworkId()));
    }

    /**
     * Starts tracking a specific body for a player.
     * Registers the tracking relationship and queues a spawn packet.
     *
     * @param player The player.
     * @param body   The body.
     */
    public void trackBodyForPlayer(ServerPlayer player, VxBody body) {
        // Prevent tracking a body if it doesn't want any network synchronization at all
        int index = body.getDataStoreIndex();
        if (index == -1) return;

        VxServerBodyDataContainer c = dataStore.serverCurrent();
        long behaviorBits = c.behaviorBits[index];
        if (!VxNetSyncBehavior.ID.isSet(behaviorBits) && !VxSyncBehavior.ID.isSet(behaviorBits)) return;

        IntSet tracked = playerTrackedBodies.computeIfAbsent(player.getUUID(), k -> IntSets.synchronize(new IntOpenHashSet()));
        if (tracked.add(body.getNetworkId())) {
            // Cancellation check: if removal is pending, cancel it instead of spawning
            ConcurrentLinkedQueue<Integer> removals = pendingRemovals.get(player.getUUID());
            if (removals != null && removals.remove(body.getNetworkId())) return;

            pendingSpawns.computeIfAbsent(player.getUUID(), k -> new ConcurrentLinkedQueue<>()).add(body);
        }
    }

    /**
     * Stops tracking a specific body for a player.
     * Removes the tracking relationship and queues a removal packet.
     *
     * @param player    The player.
     * @param networkId The body's network ID.
     */
    public void untrackBodyForPlayer(ServerPlayer player, int networkId) {
        IntSet tracked = playerTrackedBodies.get(player.getUUID());
        if (tracked != null && tracked.remove(networkId)) {
            // Cancellation check: if spawn is pending, cancel it instead of removing
            ConcurrentLinkedQueue<VxBody> spawns = pendingSpawns.get(player.getUUID());
            if (spawns != null && spawns.removeIf(b -> b.getNetworkId() == networkId)) return;

            pendingRemovals.computeIfAbsent(player.getUUID(), k -> new ConcurrentLinkedQueue<>()).add(networkId);
        }
    }

    /**
     * Processes batched removal requests on the game tick.
     * Sends compressed removal packets to players.
     */
    private void processPendingRemovals() {
        if (pendingRemovals.isEmpty()) return;

        for (Map.Entry<UUID, ConcurrentLinkedQueue<Integer>> entry : pendingRemovals.entrySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                entry.getValue().clear();
                continue;
            }

            ConcurrentLinkedQueue<Integer> ids = entry.getValue();
            if (!ids.isEmpty()) {
                IntArrayList toRemove = new IntArrayList();
                Integer id;
                while ((id = ids.poll()) != null) {
                    toRemove.add((int) id);
                }
                if (!toRemove.isEmpty()) {
                    VxNetworking.sendToPlayer(player, new S2CRemoveBodyBatchPacket(toRemove));
                }
            }
        }
    }

    /**
     * Processes batched spawn requests on the game tick.
     * Checks if the chunk is ready on the client before sending.
     * Uses pooled ByteBufs to avoid allocation during serialization.
     */
    private void processPendingSpawns() {
        if (pendingSpawns.isEmpty()) return;

        ChunkMap chunkMap = this.level.getChunkSource().chunkMap;
        // Reusable pooled buffer for spawn serialization (64KB initial size)
        ByteBuf spawnBuf = PooledByteBufAllocator.DEFAULT.directBuffer(65536);

        try {
            for (Map.Entry<UUID, ConcurrentLinkedQueue<VxBody>> entry : pendingSpawns.entrySet()) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
                if (player == null) {
                    entry.getValue().clear();
                    continue;
                }

                ConcurrentLinkedQueue<VxBody> bodies = entry.getValue();
                if (bodies.isEmpty()) continue;

                int count = 0;
                spawnBuf.clear();

                // Snapshot size to avoid infinite loop if new bodies are added during processing
                int size = bodies.size();
                for (int i = 0; i < size; i++) {
                    VxBody body = bodies.poll();
                    if (body == null) break;

                    int index = body.getDataStoreIndex();
                    if (index == -1) continue;

                    VxServerBodyDataContainer c = dataStore.serverCurrent();
                    // Retrieve the cached chunk key and convert to ChunkPos for the vanilla visibility check
                    ChunkPos bodyChunk = new ChunkPos(c.chunkKey[index]);

                    // Only spawn if the player has received the chunk
                    if (chunkMap.getPlayers(bodyChunk, false).contains(player)) {
                        VxSpawnData.writeRaw(spawnBuf, body, System.nanoTime());
                        count++;

                        // Check payload limit
                        if (spawnBuf.readableBytes() > MAX_PACKET_PAYLOAD_SIZE) {
                            dispatchSpawnPacket(player, spawnBuf, count);
                            spawnBuf.clear();
                            count = 0;
                        }
                    } else {
                        // Re-add to queue for next tick
                        bodies.add(body);
                    }
                }

                // Flush remaining
                if (count > 0) {
                    dispatchSpawnPacket(player, spawnBuf, count);
                }
            }
        } finally {
            spawnBuf.release();
        }
    }

    /**
     * Compresses and sends a spawn batch to a specific player.
     * Uses direct Zstd compression.
     *
     * @param player  The recipient.
     * @param rawData The serialized spawn data buffer.
     * @param count   Number of bodies in the batch.
     */
    private void dispatchSpawnPacket(ServerPlayer player, ByteBuf rawData, int count) {
        int readable = rawData.readableBytes();
        int maxCompressed = (int) com.github.luben.zstd.Zstd.compressBound(readable);

        // Allocate direct buffer for compressed data
        ByteBuf compressed = PooledByteBufAllocator.DEFAULT.directBuffer(maxCompressed);

        try {
            ByteBuffer src = rawData.nioBuffer(0, readable);
            ByteBuffer dst = compressed.nioBuffer(0, maxCompressed);

            // Compress direct
            long len = com.github.luben.zstd.Zstd.compressDirectByteBuffer(dst, 0, maxCompressed, src, 0, readable, 3);

            if (com.github.luben.zstd.Zstd.isError(len)) {
                throw new RuntimeException("Spawn compression failed: " + com.github.luben.zstd.Zstd.getErrorName(len));
            }

            compressed.writerIndex((int) len);

            // The packet takes ownership of the 'compressed' buffer (should release it after write)
            IVxNetPacket packet = new S2CSpawnBodyBatchPacket(count, compressed);
            VxNetworking.sendToPlayer(player, packet);

            // Release the pooled buffer
            packet.release();

        } catch (Exception e) {
            // Release the buffer if an exception prevented packet creation/sending
            if (compressed.refCnt() > 0) {
                compressed.release();
            }
            VxMainClass.LOGGER.error("Failed to compress spawn packet", e);
        }
    }

    /**
     * Cleanup tracking data on player disconnect to prevent leaks.
     * Uses the {@link #playerToChunks} reverse index for O(watched_chunks) cleanup
     * instead of iterating all tracked chunks.
     *
     * @param player The player who disconnected.
     */
    public void onPlayerDisconnect(ServerPlayer player) {
        UUID uuid = player.getUUID();
        playerTrackedBodies.remove(uuid);
        pendingSpawns.remove(uuid);
        pendingRemovals.remove(uuid);
        knownPlayers.remove(uuid);
        // Use reverse index for efficient cleanup — only touch chunks the player was watching
        Set<Long> watchedChunks = playerToChunks.remove(uuid);
        if (watchedChunks != null) {
            for (long chunkKey : watchedChunks) {
                Set<UUID> watchers = chunkWatchers.get(chunkKey);
                if (watchers != null) {
                    watchers.remove(uuid);
                    if (watchers.isEmpty()) {
                        chunkWatchers.remove(chunkKey);
                    }
                }
            }
        }
    }

    /**
     * Zero-allocation method that invokes a consumer for each player tracking the body
     * with the given network ID. Avoids collection allocation on hot paths
     * with thousands of bodies per tick.
     *
     * @param networkId The network ID of the body.
     * @param action    The action to perform for each tracking player.
     */
    public void forEachTrackerForBody(int networkId, java.util.function.Consumer<ServerPlayer> action) {
        UUID id = dataStore.getIdForNetworkId(networkId);
        if (id == null) return;

        VxBody body = manager.getVxBody(id);
        if (body == null || body.getDataStoreIndex() == -1) return;

        long chunkKey = dataStore.serverCurrent().chunkKey[body.getDataStoreIndex()];
        Set<UUID> watchers = chunkWatchers.get(chunkKey);
        if (watchers == null || watchers.isEmpty()) return;

        for (UUID uuid : watchers) {
            ServerPlayer player = knownPlayers.get(uuid);
            if (player != null) {
                action.accept(player);
            }
        }
    }

    /**
     * Internal record for tracking broadcast requirements.
     */
    private record BroadcastTask(long chunkPos, IVxNetPacket packet) {
    }
}