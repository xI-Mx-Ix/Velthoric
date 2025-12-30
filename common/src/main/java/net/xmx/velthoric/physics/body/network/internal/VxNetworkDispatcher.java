/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.network.internal;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.network.VxPacketHandler;
import net.xmx.velthoric.physics.body.manager.VxServerBodyDataStore;
import net.xmx.velthoric.physics.body.manager.VxBodyManager;
import net.xmx.velthoric.physics.body.manager.chunk.VxChunkUtil;
import net.xmx.velthoric.physics.body.network.internal.packet.S2CRemoveBodyBatchPacket;
import net.xmx.velthoric.physics.body.network.internal.packet.S2CSpawnBodyBatchPacket;
import net.xmx.velthoric.physics.body.network.internal.packet.S2CUpdateBodyStateBatchPacket;
import net.xmx.velthoric.physics.body.network.internal.packet.S2CUpdateVerticesBatchPacket;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.vehicle.sync.VxVehicleNetworkDispatcher;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages network synchronization of physics bodies.
 * <p>
 * This class handles the visibility of physics bodies for players, manages the lifecycle of
 * spawn and removal packets, and synchronizes state updates (transform, velocity, custom data).
 * It utilizes a dedicated thread for state synchronization to offload work from the main server thread.
 *
 * @author xI-Mx-Ix
 */
public class VxNetworkDispatcher {

    private final ServerLevel level;
    private final VxBodyManager manager;
    private final VxServerBodyDataStore dataStore;
    private final VxVehicleNetworkDispatcher vehicleDispatcher;

    // --- Constants for network tuning ---
    private static final int NETWORK_THREAD_TICK_RATE_MS = 10;
    private static final int MAX_STATES_PER_PACKET = 50;
    private static final int MAX_VERTICES_PER_PACKET = 50;
    private static final int MAX_PACKET_PAYLOAD_SIZE = 128 * 1024;
    private static final int MAX_REMOVALS_PER_PACKET = 512;

    // --- Player tracking data structures ---

    /**
     * Maps a player's UUID to a set of primitive network IDs of the bodies they are tracking.
     * Using IntSet avoids Integer object allocation (autoboxing).
     */
    private final Map<UUID, IntSet> playerTrackedBodies = new ConcurrentHashMap<>();

    /**
     * Maps a primitive network ID to the set of players tracking that body.
     * This allows efficient broadcasting of updates to specific observers.
     */
    private final Int2ObjectMap<Set<ServerPlayer>> bodyTrackers = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

    /**
     * Stores the list of bodies that need to be spawned for a player.
     * <p>
     * We store the {@link VxBody} reference directly instead of pre-calculating {@link VxSpawnData}.
     * This ensures that if the body moves while the chunk is pending (waiting to be sent to the client),
     * the spawn packet we eventually send will contain the <b>current</b> position, not the old one.
     */
    private final Map<ServerPlayer, ObjectArrayList<VxBody>> pendingSpawns = new HashMap<>();

    /**
     * Stores the list of network IDs that need to be removed for a player.
     */
    private final Map<ServerPlayer, IntArrayList> pendingRemovals = new HashMap<>();

    private ExecutorService networkSyncExecutor;

    // A reusable buffer for the network thread to avoid allocations in tight loops.
    // Package-private so other network components can reuse the strategy if needed, though logically isolated.
    private static final ThreadLocal<VxByteBuf> THREAD_LOCAL_BYTE_BUF = ThreadLocal.withInitial(() -> new VxByteBuf(Unpooled.buffer(1024)));

    /**
     * Constructs a new network dispatcher.
     *
     * @param level   The server level.
     * @param manager The body manager.
     */
    public VxNetworkDispatcher(ServerLevel level, VxBodyManager manager) {
        this.level = level;
        this.manager = manager;
        this.dataStore = manager.getDataStore();
        this.vehicleDispatcher = new VxVehicleNetworkDispatcher();
    }

    /**
     * Retrieves the body manager associated with this dispatcher.
     *
     * @return The {@link VxBodyManager} instance.
     */
    public VxBodyManager getManager() {
        return this.manager;
    }

    /**
     * Starts the dedicated network synchronization thread.
     */
    public void start() {
        this.networkSyncExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Velthoric-Network-Sync-Thread"));
        this.networkSyncExecutor.submit(this::runSyncLoop);
    }

    /**
     * Stops the network synchronization thread gracefully.
     */
    public void stop() {
        if (this.networkSyncExecutor != null) {
            this.networkSyncExecutor.shutdownNow();
        }
    }

    /**
     * Called every game tick from the main server thread to process pending spawns and removals.
     */
    public void onGameTick() {
        processPendingRemovals();
        processPendingSpawns();
    }

    /**
     * The main loop for the network synchronization thread.
     */
    private void runSyncLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                long cycleStartTime = System.nanoTime();

                sendStateUpdates();

                // Delegate synchronized data updates to the dedicated manager
                // This scans for custom data changes and uses the dispatcher to find recipients
                this.manager.getServerSyncManager().sendSynchronizedDataUpdates(this);

                this.vehicleDispatcher.dispatchUpdates(this.level, this.manager, this.bodyTrackers);

                long cycleEndTime = System.nanoTime();
                long cycleDurationMs = (cycleEndTime - cycleStartTime) / 1_000_000;
                long sleepTime = Math.max(0, NETWORK_THREAD_TICK_RATE_MS - cycleDurationMs);
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Exception in Velthoric Network Sync Thread", e);
            }
        }
    }

    /**
     * Collects all dirty body states and sends them to the appropriate players.
     */
    private void sendStateUpdates() {
        IntArrayList dirtyTransformIndices = new IntArrayList();
        IntArrayList dirtyVertexIndices = new IntArrayList();
        synchronized (dataStore) {
            for (int i = 0; i < dataStore.getCapacity(); i++) {
                if (dataStore.isTransformDirty[i]) {
                    dirtyTransformIndices.add(i);
                    dataStore.isTransformDirty[i] = false;
                }
                if (dataStore.isVertexDataDirty[i]) {
                    dirtyVertexIndices.add(i);
                    dataStore.isVertexDataDirty[i] = false;
                }
            }
        }

        if (dirtyTransformIndices.isEmpty() && dirtyVertexIndices.isEmpty()) {
            return;
        }

        Map<ServerPlayer, IntArrayList> transformUpdatesByPlayer = new Object2ObjectOpenHashMap<>();
        Map<ServerPlayer, IntArrayList> vertexUpdatesByPlayer = new Object2ObjectOpenHashMap<>();
        groupUpdatesByPlayer(dirtyTransformIndices, transformUpdatesByPlayer);
        groupUpdatesByPlayer(dirtyVertexIndices, vertexUpdatesByPlayer);

        if (!transformUpdatesByPlayer.isEmpty() || !vertexUpdatesByPlayer.isEmpty()) {
            level.getServer().execute(() -> {
                transformUpdatesByPlayer.forEach(this::sendBodyStatePackets);
                vertexUpdatesByPlayer.forEach(this::sendVertexDataPackets);
            });
        }
    }

    /**
     * Groups a list of dirty body indices by the players who are tracking them.
     *
     * @param dirtyIndices    A list of indices from the data store that are marked as dirty.
     * @param updatesByPlayer A map to populate, where each player is mapped to a list of dirty indices.
     */
    private void groupUpdatesByPlayer(IntArrayList dirtyIndices, Map<ServerPlayer, IntArrayList> updatesByPlayer) {
        for (int dirtyIndex : dirtyIndices) {
            int networkId = dataStore.networkId[dirtyIndex];
            if (networkId == -1) continue;

            Set<ServerPlayer> trackers = bodyTrackers.get(networkId);
            if (trackers != null) {
                for (ServerPlayer player : trackers) {
                    updatesByPlayer.computeIfAbsent(player, k -> new IntArrayList()).add(dirtyIndex);
                }
            }
        }
    }

    /**
     * Sends batched body state (transform and velocity) updates to a specific player.
     * The data is split into multiple packets if it exceeds {@code MAX_STATES_PER_PACKET}.
     * <p>
     * This method collects absolute positions and the simulation timestamp. The created
     * packet will handle relative positioning encoding internally.
     *
     * @param player  The player to send the update packets to.
     * @param indices The list of data store indices for the bodies to be updated.
     */
    private void sendBodyStatePackets(ServerPlayer player, IntArrayList indices) {
        if (indices.isEmpty()) return;

        // Arrays to hold batch data. Note that we do not need a timestamp array anymore,
        // as we send a single timestamp per batch.
        int[] networkIds = new int[MAX_STATES_PER_PACKET];
        double[] posX = new double[MAX_STATES_PER_PACKET], posY = new double[MAX_STATES_PER_PACKET], posZ = new double[MAX_STATES_PER_PACKET];
        float[] rotX = new float[MAX_STATES_PER_PACKET], rotY = new float[MAX_STATES_PER_PACKET], rotZ = new float[MAX_STATES_PER_PACKET], rotW = new float[MAX_STATES_PER_PACKET];
        float[] velX = new float[MAX_STATES_PER_PACKET], velY = new float[MAX_STATES_PER_PACKET], velZ = new float[MAX_STATES_PER_PACKET];
        boolean[] isActive = new boolean[MAX_STATES_PER_PACKET];

        int currentBatchCount = 0;
        long batchTimestamp = 0L;

        for (int index : indices) {
            int networkId = dataStore.networkId[index];
            if (networkId == -1) continue;

            // Initialize the batch timestamp using the first element.
            // We assume all bodies in this update cycle share the same simulation tick.
            if (currentBatchCount == 0) {
                batchTimestamp = dataStore.lastUpdateTimestamp[index];
            }

            networkIds[currentBatchCount] = networkId;

            // Retrieve absolute positions (double).
            // The packet encoding will convert these to relative floats.
            posX[currentBatchCount] = dataStore.posX[index];
            posY[currentBatchCount] = dataStore.posY[index];
            posZ[currentBatchCount] = dataStore.posZ[index];

            rotX[currentBatchCount] = dataStore.rotX[index];
            rotY[currentBatchCount] = dataStore.rotY[index];
            rotZ[currentBatchCount] = dataStore.rotZ[index];
            rotW[currentBatchCount] = dataStore.rotW[index];

            isActive[currentBatchCount] = dataStore.isActive[index];

            if (isActive[currentBatchCount]) {
                velX[currentBatchCount] = dataStore.velX[index];
                velY[currentBatchCount] = dataStore.velY[index];
                velZ[currentBatchCount] = dataStore.velZ[index];
            } else {
                velX[currentBatchCount] = 0;
                velY[currentBatchCount] = 0;
                velZ[currentBatchCount] = 0;
            }

            currentBatchCount++;

            // If the batch is full, instantiate the packet with the single timestamp and send it.
            if (currentBatchCount == MAX_STATES_PER_PACKET) {
                S2CUpdateBodyStateBatchPacket packet = new S2CUpdateBodyStateBatchPacket(
                        currentBatchCount,
                        batchTimestamp,
                        networkIds,
                        posX, posY, posZ,
                        rotX, rotY, rotZ, rotW,
                        velX, velY, velZ,
                        isActive
                );
                VxPacketHandler.sendToPlayer(packet, player);
                currentBatchCount = 0;
            }
        }

        // Send any remaining bodies in the final partial batch.
        if (currentBatchCount > 0) {
            S2CUpdateBodyStateBatchPacket packet = new S2CUpdateBodyStateBatchPacket(
                    currentBatchCount,
                    batchTimestamp,
                    networkIds,
                    posX, posY, posZ,
                    rotX, rotY, rotZ, rotW,
                    velX, velY, velZ,
                    isActive
            );
            VxPacketHandler.sendToPlayer(packet, player);
        }
    }

    /**
     * Sends batched vertex data updates to a specific player.
     * The data is split into multiple packets if the number of bodies exceeds {@code MAX_VERTICES_PER_PACKET}.
     *
     * @param player  The player to send the vertex data packets to.
     * @param indices The list of data store indices for the bodies whose vertex data has changed.
     */
    private void sendVertexDataPackets(ServerPlayer player, IntArrayList indices) {
        if (indices.isEmpty()) return;

        for (int i = 0; i < indices.size(); i += MAX_VERTICES_PER_PACKET) {
            int end = Math.min(i + MAX_VERTICES_PER_PACKET, indices.size());
            List<Integer> sublist = indices.subList(i, end);
            IntArrayList networkIdList = new IntArrayList();
            ObjectArrayList<float[]> vertexList = new ObjectArrayList<>();

            for (int index : sublist) {
                int networkId = dataStore.networkId[index];
                if (networkId != -1) {
                    networkIdList.add(networkId);
                    vertexList.add(dataStore.vertexData[index]);
                }
            }

            if (!networkIdList.isEmpty()) {
                S2CUpdateVerticesBatchPacket packet = new S2CUpdateVerticesBatchPacket(networkIdList.size(), networkIdList.toIntArray(), vertexList.toArray(new float[0][]));
                VxPacketHandler.sendToPlayer(packet, player);
            }
        }
    }

    /**
     * Called by the BodyManager when a body is added to the world.
     * <p>
     * This method iterates over all players on the server and checks if the body's chunk
     * is within their calculated view distance. This works regardless of whether the chunk
     * is currently loaded, pending, or sent, ensuring no race conditions occur during tracking.
     *
     * @param body The physics body that was added.
     */
    public void onBodyAdded(VxBody body) {
        int index = body.getDataStoreIndex();
        if (index == -1) return;
        ChunkPos bodyChunk = manager.getBodyChunkPos(index);

        // Iterate over all players to find who should see this body.
        for (ServerPlayer player : this.level.players()) {
            if (VxChunkUtil.isPlayerWatchingChunk(player, bodyChunk)) {
                trackBodyForPlayer(player, body);
            }
        }
    }

    /**
     * Called by the BodyManager when a body is removed from the physics world.
     * It ensures that all players who were tracking the body receive a removal packet.
     *
     * @param body The physics body that was removed.
     */
    public void onBodyRemoved(VxBody body) {
        Set<ServerPlayer> trackers = bodyTrackers.get(body.getNetworkId());
        if (trackers != null) {
            for (ServerPlayer player : new ArrayList<>(trackers)) {
                untrackBodyForPlayer(player, body.getNetworkId());
            }
        }
    }

    /**
     * Called by the VxChunkMap when a body has moved across a chunk boundary.
     * <p>
     * This method determines which players need to start or stop tracking the body based on
     * the calculated visibility of the 'from' and 'to' chunks relative to the player's position.
     *
     * @param body The body that moved.
     * @param from The chunk the body moved from.
     * @param to   The chunk the body moved to.
     */
    public void onBodyMoved(VxBody body, ChunkPos from, ChunkPos to) {
        if (from.equals(to)) return;

        int networkId = body.getNetworkId();

        // Iterate all players to handle tracking updates based on their view distance.
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
     * Called by a mixin when a player starts tracking a new chunk.
     * Queues spawn packets for all physics bodies within that chunk for the player.
     *
     * @param player   The player who started tracking the chunk.
     * @param chunkPos The position of the newly tracked chunk.
     */
    public void trackBodiesInChunkForPlayer(ServerPlayer player, ChunkPos chunkPos) {
        manager.getChunkManager().forEachBodyInChunk(chunkPos, body -> {
            trackBodyForPlayer(player, body);
        });
    }

    /**
     * Called by a mixin when a player stops tracking a chunk.
     * Queues removal packets for all physics bodies within that chunk for the player.
     *
     * @param player   The player who stopped tracking the chunk.
     * @param chunkPos The position of the untracked chunk.
     */
    public void untrackBodiesInChunkForPlayer(ServerPlayer player, ChunkPos chunkPos) {
        manager.getChunkManager().forEachBodyInChunk(chunkPos, body -> {
            untrackBodyForPlayer(player, body.getNetworkId());
        });
    }

    /**
     * Instructs the dispatcher to track a physics body for a player.
     * <p>
     * This method updates tracking data structures and queues the body for spawning.
     * It atomically handles cases where a 'remove' request for the same body is already pending,
     * cancelling them out to prevent redundant network traffic.
     *
     * @param player The player who will start tracking the body.
     * @param body   The body to be tracked.
     */
    public void trackBodyForPlayer(ServerPlayer player, VxBody body) {
        IntSet trackedByPlayer = playerTrackedBodies.computeIfAbsent(player.getUUID(), k -> IntSets.synchronize(new IntOpenHashSet()));
        if (trackedByPlayer.add(body.getNetworkId())) {
            // computeIfAbsent on an Int2ObjectMap is free of autoboxing.
            bodyTrackers.computeIfAbsent(body.getNetworkId(), k -> ConcurrentHashMap.newKeySet()).add(player);
            synchronized (pendingSpawns) {
                IntArrayList removals = pendingRemovals.get(player);
                // The check and removal from the pending removals list is an important optimization.
                if (removals != null && removals.rem(body.getNetworkId())) {
                    return; // The spawn cancels out the pending removal.
                }
                // Store the body object directly. We will create the VxSpawnData later.
                pendingSpawns.computeIfAbsent(player, k -> new ObjectArrayList<>()).add(body);
            }
        }
    }

    /**
     * Instructs the dispatcher to stop tracking a physics body for a player.
     * <p>
     * This method updates tracking data structures and queues a removal packet.
     * It atomically handles cases where a 'spawn' request is already pending, cancelling them out
     * to prevent a body from being spawned and immediately removed on the client.
     *
     * @param player    The player who will stop tracking the body.
     * @param networkId The network ID of the body to stop tracking.
     */
    public void untrackBodyForPlayer(ServerPlayer player, int networkId) {
        IntSet trackedByPlayer = playerTrackedBodies.get(player.getUUID());
        if (trackedByPlayer != null && trackedByPlayer.remove(networkId)) {
            Set<ServerPlayer> trackers = bodyTrackers.get(networkId);
            if (trackers != null) {
                trackers.remove(player);
                if (trackers.isEmpty()) {
                    bodyTrackers.remove(networkId);
                }
            }
            synchronized (pendingSpawns) {
                ObjectArrayList<VxBody> spawns = pendingSpawns.get(player);
                // Remove the body from the pending list by checking its network ID
                if (spawns != null && spawns.removeIf(b -> b.getNetworkId() == networkId)) {
                    return; // The removal cancels out the pending spawn.
                }
                pendingRemovals.computeIfAbsent(player, k -> new IntArrayList()).add(networkId);
            }
        }
    }

    /**
     * Returns the set of players currently tracking the body with the given network ID.
     *
     * @param networkId The network ID of the body.
     * @return A set of players, or null if no one is tracking it.
     */
    public Set<ServerPlayer> getTrackersForBody(int networkId) {
        return bodyTrackers.get(networkId);
    }

    /**
     * Handles a player disconnecting. Cleans up all associated tracking data.
     *
     * @param player The player who disconnected.
     */
    public void onPlayerDisconnect(ServerPlayer player) {
        UUID playerId = player.getUUID();
        IntSet trackedBodies = playerTrackedBodies.remove(playerId);
        if (trackedBodies != null) {
            final IntIterator iterator = trackedBodies.iterator();
            while (iterator.hasNext()) {
                int networkId = iterator.nextInt();
                Set<ServerPlayer> trackers = bodyTrackers.get(networkId);
                if (trackers != null) {
                    trackers.remove(player);
                    if (trackers.isEmpty()) {
                        bodyTrackers.remove(networkId);
                    }
                }
            }
        }
        synchronized (pendingSpawns) {
            pendingSpawns.remove(player);
            pendingRemovals.remove(player);
        }
    }

    /**
     * Processes the queue of pending body removals for all players.
     * <p>
     * This method iterates through the pending removals map, batches the IDs into packets,
     * and sends them to the respective players. It uses an iterator to clear the map efficiently.
     */
    private void processPendingRemovals() {
        if (pendingRemovals.isEmpty()) return;

        synchronized (pendingRemovals) {
            Iterator<Map.Entry<ServerPlayer, IntArrayList>> iterator = pendingRemovals.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<ServerPlayer, IntArrayList> entry = iterator.next();
                ServerPlayer player = entry.getKey();
                IntArrayList removalList = entry.getValue();

                if (!removalList.isEmpty()) {
                    for (int i = 0; i < removalList.size(); i += MAX_REMOVALS_PER_PACKET) {
                        int end = Math.min(i + MAX_REMOVALS_PER_PACKET, removalList.size());
                        VxPacketHandler.sendToPlayer(new S2CRemoveBodyBatchPacket(removalList.subList(i, end)), player);
                    }
                }
                iterator.remove();
            }
        }
    }

    /**
     * Processes the queue of pending body spawns for all players.
     * <p>
     * This method acts as a gatekeeper. It checks if the chunk containing the body has
     * actually been sent to the player (via the vanilla ChunkMap). If the chunk is fully sent,
     * the spawn packet is created and dispatched. If the chunk is still pending (throttled),
     * the spawn request remains in the queue for the next tick.
     */
    private void processPendingSpawns() {
        if (pendingSpawns.isEmpty()) return;

        synchronized (pendingSpawns) {
            Iterator<Map.Entry<ServerPlayer, ObjectArrayList<VxBody>>> it = pendingSpawns.entrySet().iterator();
            ChunkMap chunkMap = this.level.getChunkSource().chunkMap;

            while (it.hasNext()) {
                Map.Entry<ServerPlayer, ObjectArrayList<VxBody>> entry = it.next();
                ServerPlayer player = entry.getKey();
                ObjectArrayList<VxBody> allBodies = entry.getValue();

                if (allBodies.isEmpty()) {
                    it.remove();
                    continue;
                }

                ObjectArrayList<VxSpawnData> toSend = new ObjectArrayList<>();
                ObjectArrayList<VxBody> toKeep = new ObjectArrayList<>();

                for (VxBody body : allBodies) {
                    // Check if the body is still valid/active
                    if (body.getDataStoreIndex() == -1) continue;

                    ChunkPos chunkPos = manager.getBodyChunkPos(body.getDataStoreIndex());

                    // Check if the player actually has the chunk loaded and SENT.
                    // chunkMap.getPlayers(pos, false) returns the list of players who have the chunk
                    // AND for whom the chunk is NOT pending send (not in the bandwidth throttling queue).
                    if (chunkMap.getPlayers(chunkPos, false).contains(player)) {
                        // Chunk is ready, create spawn data with current timestamp and position
                        toSend.add(new VxSpawnData(body, System.nanoTime()));
                    } else {
                        // Chunk is not ready on client yet, defer this spawn to the next tick
                        toKeep.add(body);
                    }
                }

                // Send the batch of bodies for chunks that are ready
                if (!toSend.isEmpty()) {
                    sendSpawnBatch(player, toSend);
                }

                // Update the map: remove entry if empty, otherwise keep deferred spawns
                if (toKeep.isEmpty()) {
                    it.remove();
                } else {
                    entry.setValue(toKeep);
                }
            }
        }
    }

    /**
     * Helper method to batch and send spawn packets.
     *
     * @param player        The player to send the packets to.
     * @param spawnDataList The list of spawn data objects to send.
     */
    private void sendSpawnBatch(ServerPlayer player, ObjectArrayList<VxSpawnData> spawnDataList) {
        ObjectArrayList<VxSpawnData> batch = new ObjectArrayList<>();
        int currentBatchSizeBytes = 0;
        for (VxSpawnData data : spawnDataList) {
            int dataSize = data.estimateSize();
            // Respect the maximum packet payload size to avoid network disconnection
            if (!batch.isEmpty() && currentBatchSizeBytes + dataSize > MAX_PACKET_PAYLOAD_SIZE) {
                VxPacketHandler.sendToPlayer(new S2CSpawnBodyBatchPacket(batch), player);
                batch.clear();
                currentBatchSizeBytes = 0;
            }
            batch.add(data);
            currentBatchSizeBytes += dataSize;
        }
        if (!batch.isEmpty()) {
            VxPacketHandler.sendToPlayer(new S2CSpawnBodyBatchPacket(batch), player);
        }
    }
}