/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.network.internal;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.config.VxModConfig;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.network.VxPacketHandler;
import net.xmx.velthoric.network.VxPacketUtils;
import net.xmx.velthoric.physics.body.manager.VxBodyManager;
import net.xmx.velthoric.physics.body.manager.VxServerBodyDataStore;
import net.xmx.velthoric.physics.body.network.internal.packet.S2CRemoveBodyBatchPacket;
import net.xmx.velthoric.physics.body.network.internal.packet.S2CSpawnBodyBatchPacket;
import net.xmx.velthoric.physics.body.network.internal.packet.S2CUpdateBodyStateBatchPacket;
import net.xmx.velthoric.physics.body.network.internal.packet.S2CUpdateVerticesBatchPacket;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.body.util.VxChunkUtil;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The central controller for physics body network synchronization.
 * <p>
 * <b>Scalability Architecture:</b>
 * 1. <b>Grouping:</b> Dirty bodies are grouped by the chunk they reside in.
 * 2. <b>Serialization:</b> Data for each chunk is serialized into a raw binary stream once.
 * 3. <b>Compression:</b> The binary stream is compressed once per chunk using Zstd.
 * 4. <b>Broadcasting:</b> The resulting compressed payload is sent to all players watching that chunk.
 * <p>
 * This architecture shifts the O(Players * Bodies) complexity to O(Chunks + Players),
 * drastically reducing CPU and GC overhead.
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
    private final VxBodyManager manager;

    /**
     * The optimized Structure-of-Arrays data store for body properties.
     */
    private final VxServerBodyDataStore dataStore;

    /**
     * Frequency of the network synchronization thread in milliseconds.
     */
    private final int NETWORK_THREAD_TICK_RATE_MS;

    /**
     * Maximum allowed bytes for a single packet payload to prevent network overflow.
     */
    private final int MAX_PACKET_PAYLOAD_SIZE;

    /**
     * Maps player UUIDs to the set of body network IDs they are currently tracking.
     */
    private final Map<UUID, IntSet> playerTrackedBodies = new ConcurrentHashMap<>();

    /**
     * Bodies waiting to be spawned for specific players, pending chunk readiness.
     */
    private final Map<ServerPlayer, ObjectArrayList<VxBody>> pendingSpawns = new HashMap<>();

    /**
     * Network IDs waiting to be removed from specific players' clients.
     */
    private final Map<ServerPlayer, IntArrayList> pendingRemovals = new HashMap<>();

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
     * Thread-local buffer for raw binary serialization to avoid per-tick allocations.
     */
    private static final ThreadLocal<VxByteBuf> SERIALIZATION_BUFFER = ThreadLocal.withInitial(() ->
            new VxByteBuf(Unpooled.buffer(65536)));

    /**
     * Thread-local byte array for compression output, effectively acting as a scratchpad.
     */
    private static final ThreadLocal<byte[]> COMPRESSION_BUFFER = ThreadLocal.withInitial(() -> new byte[65536]);

    /**
     * Constructs a new dispatcher and initializes network tuning parameters from config.
     *
     * @param level   The server level.
     * @param manager The physics body manager.
     */
    public VxNetworkDispatcher(ServerLevel level, VxBodyManager manager) {
        this.level = level;
        this.manager = manager;
        this.dataStore = manager.getDataStore();

        this.NETWORK_THREAD_TICK_RATE_MS = VxModConfig.NETWORK.networkTickRate.get();
        this.MAX_PACKET_PAYLOAD_SIZE = VxModConfig.NETWORK.maxPayloadSize.get();
    }

    /**
     * @return The associated body manager.
     */
    public VxBodyManager getManager() {
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

                // Phase 3: Dispatching (must happen on main thread for vanilla visibility checks)
                if (!broadcastTasks.isEmpty()) {
                    level.getServer().execute(() -> dispatchBroadcasts(broadcastTasks));
                }

                // Sync custom data
                this.manager.getServerSyncManager().sendSynchronizedDataUpdates(this);

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
        dirtyBodiesByChunk.clear();
        dirtyVerticesByChunk.clear();

        synchronized (dataStore) {
            for (int i = 0; i < dataStore.getCapacity(); i++) {
                if (dataStore.networkId[i] == -1) continue;

                boolean transformDirty = dataStore.isTransformDirty[i];
                boolean vertexDirty = dataStore.isVertexDataDirty[i];

                if (transformDirty || vertexDirty) {
                    long chunkPosLong = manager.getChunkManager().getBodyChunkPosLong(i);

                    if (transformDirty) {
                        dirtyBodiesByChunk.computeIfAbsent(chunkPosLong, k -> new IntArrayList()).add(i);
                        dataStore.isTransformDirty[i] = false;
                    }
                    if (vertexDirty) {
                        dirtyVerticesByChunk.computeIfAbsent(chunkPosLong, k -> new IntArrayList()).add(i);
                        dataStore.isVertexDataDirty[i] = false;
                    }
                }
            }
        }
    }

    /**
     * Iterates over grouped dirty bodies and creates compressed binary packets for each chunk.
     *
     * @return A list of tasks containing the chunk coordinate and its corresponding pre-built packet.
     */
    private List<BroadcastTask> serializeBatches() {
        List<BroadcastTask> tasks = new ArrayList<>(dirtyBodiesByChunk.size() + dirtyVerticesByChunk.size());

        for (Long2ObjectMap.Entry<IntArrayList> entry : dirtyBodiesByChunk.long2ObjectEntrySet()) {
            long chunkPosLong = entry.getLongKey();
            try {
                tasks.add(new BroadcastTask(chunkPosLong, createChunkStatePacket(chunkPosLong, entry.getValue())));
            } catch (IOException e) {
                VxMainClass.LOGGER.error("Failed to serialize state batch for chunk {}", chunkPosLong, e);
            }
        }

        for (Long2ObjectMap.Entry<IntArrayList> entry : dirtyVerticesByChunk.long2ObjectEntrySet()) {
            long chunkPosLong = entry.getLongKey();
            try {
                tasks.add(new BroadcastTask(chunkPosLong, createChunkVertexPacket(chunkPosLong, entry.getValue())));
            } catch (IOException e) {
                VxMainClass.LOGGER.error("Failed to serialize vertex batch for chunk {}", chunkPosLong, e);
            }
        }

        return tasks;
    }

    /**
     * Finalizes the process by sending the pre-built packets to all players watching the respective chunks.
     * This method executes on the server's main thread to safely access the player list and ChunkMap.
     *
     * @param tasks The broadcast tasks generated by the network thread.
     */
    private void dispatchBroadcasts(List<BroadcastTask> tasks) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        for (BroadcastTask task : tasks) {
            ChunkPos pos = new ChunkPos(task.chunkPos);
            List<ServerPlayer> players = chunkMap.getPlayers(pos, false);

            if (!players.isEmpty()) {
                for (ServerPlayer player : players) {
                    VxPacketHandler.sendToPlayer(task.packet, player);
                }
            }
        }
    }

    /**
     * Serializes transform and state data for a specific chunk into a binary stream.
     *
     * @param chunkPosLong The chunk coordinate.
     * @param indices      The indices of bodies within this chunk that changed.
     * @return A packet containing the compressed binary data.
     * @throws IOException If compression fails.
     */
    private S2CUpdateBodyStateBatchPacket createChunkStatePacket(long chunkPosLong, IntArrayList indices) throws IOException {
        VxByteBuf buf = SERIALIZATION_BUFFER.get();
        buf.clear();

        ChunkPos chunkPos = new ChunkPos(chunkPosLong);
        double chunkBaseX = chunkPos.getMinBlockX();
        double chunkBaseY = level.getMinBuildHeight();
        double chunkBaseZ = chunkPos.getMinBlockZ();

        buf.writeVarInt(indices.size());
        buf.writeLong(System.nanoTime());
        buf.writeLong(chunkPosLong);

        for (int i = 0; i < indices.size(); i++) {
            int idx = indices.getInt(i);
            buf.writeVarInt(dataStore.networkId[idx]);
            buf.writeFloat((float) (dataStore.posX[idx] - chunkBaseX));
            buf.writeFloat((float) (dataStore.posY[idx] - chunkBaseY));
            buf.writeFloat((float) (dataStore.posZ[idx] - chunkBaseZ));
            buf.writeFloat(dataStore.rotX[idx]);
            buf.writeFloat(dataStore.rotY[idx]);
            buf.writeFloat(dataStore.rotZ[idx]);
            buf.writeFloat(dataStore.rotW[idx]);

            boolean active = dataStore.isActive[idx];
            buf.writeBoolean(active);
            if (active) {
                buf.writeFloat(dataStore.velX[idx]);
                buf.writeFloat(dataStore.velY[idx]);
                buf.writeFloat(dataStore.velZ[idx]);
            }
        }

        return new S2CUpdateBodyStateBatchPacket(compressBuffer(buf));
    }

    /**
     * Serializes vertex data for soft bodies within a specific chunk into a binary stream.
     *
     * @param chunkPosLong The chunk coordinate.
     * @param indices      The indices of bodies with dirty vertex data.
     * @return A packet containing the compressed vertex data.
     * @throws IOException If compression fails.
     */
    private S2CUpdateVerticesBatchPacket createChunkVertexPacket(long chunkPosLong, IntArrayList indices) throws IOException {
        VxByteBuf buf = SERIALIZATION_BUFFER.get();
        buf.clear();

        buf.writeVarInt(indices.size());
        buf.writeLong(chunkPosLong);

        for (int i = 0; i < indices.size(); i++) {
            int idx = indices.getInt(i);
            buf.writeVarInt(dataStore.networkId[idx]);
            float[] vData = dataStore.vertexData[idx];
            if (vData != null && vData.length > 0) {
                buf.writeBoolean(true);
                buf.writeVarInt(vData.length);
                for (float v : vData) buf.writeFloat(v);
            } else {
                buf.writeBoolean(false);
            }
        }

        return new S2CUpdateVerticesBatchPacket(compressBuffer(buf));
    }

    /**
     * Compresses the contents of a VxByteBuf into a byte array using the reusable compression buffer.
     *
     * @param buf The buffer to compress.
     * @return A new byte array containing compressed data.
     * @throws IOException If compression fails.
     */
    private byte[] compressBuffer(VxByteBuf buf) throws IOException {
        int uncompressedSize = buf.readableBytes();
        byte[] compBuf = COMPRESSION_BUFFER.get();
        int maxBound = VxPacketUtils.getCompressBound(uncompressedSize);

        if (compBuf.length < maxBound) {
            compBuf = new byte[maxBound];
            COMPRESSION_BUFFER.set(compBuf);
        }

        byte[] srcData = new byte[uncompressedSize];
        buf.readBytes(srcData);

        int compressedSize = VxPacketUtils.compressInto(srcData, 0, uncompressedSize, compBuf, 0);
        return Arrays.copyOf(compBuf, compressedSize);
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
        ChunkPos bodyChunk = manager.getChunkManager().getBodyChunkPos(index);

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
     * <p>
     * Unlike the previous implementation, this uses the chunk map to efficiently find observers
     * rather than maintaining a separate set of trackers per body, which scales poorly.
     *
     * @param body The body instance being removed.
     */
    public void onBodyRemoved(VxBody body) {
        int index = body.getDataStoreIndex();
        // Even if the body is about to be removed, the DataStore index should still be valid
        // at this point in the lifecycle (called before cleanup).
        if (index == -1) return;

        ChunkPos chunkPos = manager.getChunkManager().getBodyChunkPos(index);
        // Use the vanilla ChunkMap to find players who are currently watching this chunk.
        // These are the only players who could possibly have the body spawned on their client.
        List<ServerPlayer> players = level.getChunkSource().chunkMap.getPlayers(chunkPos, false);

        if (!players.isEmpty()) {
            int networkId = body.getNetworkId();
            for (ServerPlayer player : players) {
                // We attempt to untrack/remove the body for each player.
                // The untrack method handles the logic of checking if the player was actually tracking it.
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
     * Queues spawns for all bodies in that chunk.
     *
     * @param player   The player.
     * @param chunkPos The chunk.
     */
    public void trackBodiesInChunkForPlayer(ServerPlayer player, ChunkPos chunkPos) {
        manager.getChunkManager().forEachBodyInChunk(chunkPos, body -> trackBodyForPlayer(player, body));
    }

    /**
     * Bulk tracking update for a player leaving a chunk.
     * Queues removals for all bodies in that chunk.
     *
     * @param player   The player.
     * @param chunkPos The chunk.
     */
    public void untrackBodiesInChunkForPlayer(ServerPlayer player, ChunkPos chunkPos) {
        manager.getChunkManager().forEachBodyInChunk(chunkPos, body -> untrackBodyForPlayer(player, body.getNetworkId()));
    }

    /**
     * Starts tracking a specific body for a player.
     * Registers the tracking relationship and queues a spawn packet.
     *
     * @param player The player.
     * @param body   The body.
     */
    public void trackBodyForPlayer(ServerPlayer player, VxBody body) {
        IntSet tracked = playerTrackedBodies.computeIfAbsent(player.getUUID(), k -> IntSets.synchronize(new IntOpenHashSet()));
        if (tracked.add(body.getNetworkId())) {
            synchronized (pendingSpawns) {
                IntArrayList removals = pendingRemovals.get(player);
                // cancellation check: if removal is pending, cancel it instead of spawning
                if (removals != null && removals.rem(body.getNetworkId())) return;
                pendingSpawns.computeIfAbsent(player, k -> new ObjectArrayList<>()).add(body);
            }
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
            synchronized (pendingSpawns) {
                ObjectArrayList<VxBody> spawns = pendingSpawns.get(player);
                // cancellation check: if spawn is pending, cancel it instead of removing
                if (spawns != null && spawns.removeIf(b -> b.getNetworkId() == networkId)) return;
                pendingRemovals.computeIfAbsent(player, k -> new IntArrayList()).add(networkId);
            }
        }
    }

    /**
     * Processes batched removal requests on the game tick.
     * Sends compressed removal packets to players.
     */
    private void processPendingRemovals() {
        if (pendingRemovals.isEmpty()) return;
        synchronized (pendingRemovals) {
            var it = pendingRemovals.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                if (!entry.getValue().isEmpty()) {
                    VxPacketHandler.sendToPlayer(new S2CRemoveBodyBatchPacket(entry.getValue()), entry.getKey());
                }
                it.remove();
            }
        }
    }

    /**
     * Processes batched spawn requests on the game tick.
     * Checks if the chunk is ready on the client before sending.
     */
    private void processPendingSpawns() {
        if (pendingSpawns.isEmpty()) return;
        synchronized (pendingSpawns) {
            Iterator<Map.Entry<ServerPlayer, ObjectArrayList<VxBody>>> it = pendingSpawns.entrySet().iterator();
            ChunkMap chunkMap = this.level.getChunkSource().chunkMap;
            VxByteBuf spawnBuf = SERIALIZATION_BUFFER.get();

            while (it.hasNext()) {
                Map.Entry<ServerPlayer, ObjectArrayList<VxBody>> entry = it.next();
                ServerPlayer player = entry.getKey();
                ObjectArrayList<VxBody> bodies = entry.getValue();
                if (bodies.isEmpty()) {
                    it.remove();
                    continue;
                }

                ObjectArrayList<VxBody> toKeep = new ObjectArrayList<>();
                spawnBuf.clear();
                int count = 0;

                for (VxBody body : bodies) {
                    if (body.getDataStoreIndex() == -1) continue;
                    // Only spawn if the player has received the chunk (not pending send)
                    if (chunkMap.getPlayers(manager.getChunkManager().getBodyChunkPos(body.getDataStoreIndex()), false).contains(player)) {
                        VxSpawnData.writeRaw(spawnBuf, body, System.nanoTime());
                        count++;

                        // Check payload limit
                        if (spawnBuf.readableBytes() > MAX_PACKET_PAYLOAD_SIZE) {
                            dispatchSpawnPacket(player, spawnBuf, count);
                            spawnBuf.clear();
                            count = 0;
                        }
                    } else {
                        toKeep.add(body);
                    }
                }

                // Flush remaining
                if (count > 0) dispatchSpawnPacket(player, spawnBuf, count);

                if (toKeep.isEmpty()) it.remove();
                else entry.setValue(toKeep);
            }
        }
    }

    /**
     * Compresses and sends a spawn batch to a specific player.
     *
     * @param player  The recipient.
     * @param rawData The serialized spawn data buffer.
     * @param count   Number of bodies in the batch.
     */
    private void dispatchSpawnPacket(ServerPlayer player, VxByteBuf rawData, int count) {
        try {
            VxPacketHandler.sendToPlayer(new S2CSpawnBodyBatchPacket(count, compressBuffer(rawData)), player);
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to compress spawn packet", e);
        }
    }

    /**
     * Cleanup tracking data on player disconnect to prevent leaks.
     *
     * @param player The player who disconnected.
     */
    public void onPlayerDisconnect(ServerPlayer player) {
        playerTrackedBodies.remove(player.getUUID());
        synchronized (pendingSpawns) {
            pendingSpawns.remove(player);
        }
        synchronized (pendingRemovals) {
            pendingRemovals.remove(player);
        }
    }

    /**
     * Retrieves the set of players currently tracking the body with the given network ID.
     * <p>
     * In this chunk-centric architecture, this delegates to the vanilla ChunkMap to find
     * players watching the chunk the body resides in, avoiding redundant tracking maps.
     *
     * @param networkId The network ID of the body.
     * @return A set of players watching the body.
     */
    public Set<ServerPlayer> getTrackersForBody(int networkId) {
        UUID id = dataStore.getIdForNetworkId(networkId);
        if (id == null) return Collections.emptySet();

        VxBody body = manager.getVxBody(id);
        if (body == null || body.getDataStoreIndex() == -1) return Collections.emptySet();

        ChunkPos pos = manager.getChunkManager().getBodyChunkPos(body.getDataStoreIndex());
        List<ServerPlayer> players = level.getChunkSource().chunkMap.getPlayers(pos, false);

        // Wrap in HashSet to satisfy Set<ServerPlayer> return type expected by SyncManager
        return !players.isEmpty() ? new HashSet<>(players) : Collections.emptySet();
    }

    /**
     * Internal record for tracking broadcast requirements.
     */
    private record BroadcastTask(long chunkPos, Object packet) {
    }
}