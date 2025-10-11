/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.manager;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.network.VxPacketHandler;
import net.xmx.velthoric.physics.object.packet.VxSpawnData;
import net.xmx.velthoric.physics.object.packet.batch.*;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.vehicle.sync.VxWheelNetworkDispatcher;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages network synchronization of physics objects.
 * This class handles which objects are visible to each player and sends spawn,
 * removal, and state update packets. It uses a dedicated thread for state
 * synchronization to offload work from the main server thread.
 *
 * @author xI-Mx-Ix
 */
public class VxObjectNetworkDispatcher {

    private final ServerLevel level;
    private final VxObjectManager manager;
    private final VxObjectDataStore dataStore;
    private final VxWheelNetworkDispatcher wheelDispatcher;

    // --- Constants for network tuning ---
    private static final int NETWORK_THREAD_TICK_RATE_MS = 10;
    private static final int MAX_STATES_PER_PACKET = 50;
    private static final int MAX_VERTICES_PER_PACKET = 50;
    private static final int MAX_PACKET_PAYLOAD_SIZE = 128 * 1024;
    private static final int MAX_REMOVALS_PER_PACKET = 512;

    // --- Player tracking data structures ---
    private final Map<UUID, Set<UUID>> playerTrackedObjects = new ConcurrentHashMap<>();
    private final Map<UUID, Set<ServerPlayer>> objectTrackers = new ConcurrentHashMap<>();
    private final Map<ServerPlayer, ObjectArrayList<VxSpawnData>> pendingSpawns = new HashMap<>();
    private final Map<ServerPlayer, ObjectArrayList<UUID>> pendingRemovals = new HashMap<>();
    private ExecutorService networkSyncExecutor;

    public VxObjectNetworkDispatcher(ServerLevel level, VxObjectManager manager) {
        this.level = level;
        this.manager = manager;
        this.dataStore = manager.getDataStore();
        this.wheelDispatcher = new VxWheelNetworkDispatcher();
    }

    /**
     * Retrieves the object manager associated with this dispatcher.
     * @return The {@link VxObjectManager} instance.
     */
    public VxObjectManager getManager() {
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
                sendSynchronizedDataUpdates();
                this.wheelDispatcher.dispatchUpdates(this.level, this.manager, this.objectTrackers);
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
     * Collects all dirty object states and sends them to the appropriate players.
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
     * Groups a list of dirty object indices by the players who are tracking them.
     *
     * @param dirtyIndices    A list of indices from the data store that are marked as dirty.
     * @param updatesByPlayer A map to populate, where each player is mapped to a list of dirty indices they should be updated about.
     */
    private void groupUpdatesByPlayer(IntArrayList dirtyIndices, Map<ServerPlayer, IntArrayList> updatesByPlayer) {
        for (int dirtyIndex : dirtyIndices) {
            UUID objectId = dataStore.getIdForIndex(dirtyIndex);
            if (objectId == null) continue;

            Set<ServerPlayer> trackers = objectTrackers.get(objectId);
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
     *
     * @param player  The player to send the update packets to.
     * @param indices The list of data store indices for the objects to be updated.
     */
    private void sendBodyStatePackets(ServerPlayer player, IntArrayList indices) {
        if (indices.isEmpty()) {
            return;
        }

        UUID[] ids = new UUID[MAX_STATES_PER_PACKET];
        long[] timestamps = new long[MAX_STATES_PER_PACKET];
        double[] posX = new double[MAX_STATES_PER_PACKET], posY = new double[MAX_STATES_PER_PACKET], posZ = new double[MAX_STATES_PER_PACKET];
        float[] rotX = new float[MAX_STATES_PER_PACKET], rotY = new float[MAX_STATES_PER_PACKET], rotZ = new float[MAX_STATES_PER_PACKET], rotW = new float[MAX_STATES_PER_PACKET];
        float[] velX = new float[MAX_STATES_PER_PACKET], velY = new float[MAX_STATES_PER_PACKET], velZ = new float[MAX_STATES_PER_PACKET];
        boolean[] isActive = new boolean[MAX_STATES_PER_PACKET];
        int currentBatchCount = 0;

        for (int index : indices) {
            UUID uuid = dataStore.getIdForIndex(index);
            if (uuid == null) continue;

            ids[currentBatchCount] = uuid;
            timestamps[currentBatchCount] = dataStore.lastUpdateTimestamp[index];
            posX[currentBatchCount] = dataStore.posX[index];
            posY[currentBatchCount] = dataStore.posY[index];
            posZ[currentBatchCount] = dataStore.posZ[index];
            rotX[currentBatchCount] = dataStore.rotX[index];
            rotY[currentBatchCount] = dataStore.rotY[index];
            rotZ[currentBatchCount] = dataStore.rotZ[index];
            rotW[currentBatchCount] = dataStore.rotW[index];
            isActive[currentBatchCount] = dataStore.isActive[index];
            velX[currentBatchCount] = dataStore.velX[index];
            velY[currentBatchCount] = dataStore.velY[index];
            velZ[currentBatchCount] = dataStore.velZ[index];
            currentBatchCount++;

            if (currentBatchCount == MAX_STATES_PER_PACKET) {
                S2CUpdateBodyStateBatchPacket packet = new S2CUpdateBodyStateBatchPacket(currentBatchCount, ids, timestamps, posX, posY, posZ, rotX, rotY, rotZ, rotW, velX, velY, velZ, isActive);
                VxPacketHandler.sendToPlayer(packet, player);
                currentBatchCount = 0;
            }
        }

        if (currentBatchCount > 0) {
            S2CUpdateBodyStateBatchPacket packet = new S2CUpdateBodyStateBatchPacket(currentBatchCount, ids, timestamps, posX, posY, posZ, rotX, rotY, rotZ, rotW, velX, velY, velZ, isActive);
            VxPacketHandler.sendToPlayer(packet, player);
        }
    }

    /**
     * Sends batched vertex data updates to a specific player.
     * The data is split into multiple packets if the number of objects exceeds {@code MAX_VERTICES_PER_PACKET}.
     *
     * @param player  The player to send the vertex data packets to.
     * @param indices The list of data store indices for the objects whose vertex data has changed.
     */
    private void sendVertexDataPackets(ServerPlayer player, IntArrayList indices) {
        if (indices.isEmpty()) {
            return;
        }

        for (int i = 0; i < indices.size(); i += MAX_VERTICES_PER_PACKET) {
            int end = Math.min(i + MAX_VERTICES_PER_PACKET, indices.size());
            List<Integer> sublist = indices.subList(i, end);
            ObjectArrayList<UUID> idList = new ObjectArrayList<>();
            ObjectArrayList<float[]> vertexList = new ObjectArrayList<>();

            for (int index : sublist) {
                UUID uuid = dataStore.getIdForIndex(index);
                if (uuid != null) {
                    idList.add(uuid);
                    vertexList.add(dataStore.vertexData[index]);
                }
            }

            if (!idList.isEmpty()) {
                S2CUpdateVerticesBatchPacket packet = new S2CUpdateVerticesBatchPacket(idList.size(), idList.toArray(new UUID[0]), vertexList.toArray(new float[0][]));
                VxPacketHandler.sendToPlayer(packet, player);
            }
        }
    }

    /**
     * Scans for objects with dirty custom synchronized data, serializes it, and sends it to tracking players.
     * This allows {@link VxBody} instances to send arbitrary data to clients when their state changes.
     */
    private void sendSynchronizedDataUpdates() {
        IntArrayList dirtyDataIndices = new IntArrayList();
        synchronized (dataStore) {
            for (int i = 0; i < dataStore.getCapacity(); i++) {
                if (dataStore.isCustomDataDirty[i]) {
                    dirtyDataIndices.add(i);
                    dataStore.isCustomDataDirty[i] = false;
                }
            }
        }

        if (dirtyDataIndices.isEmpty()) {
            return;
        }

        Map<ServerPlayer, Map<UUID, byte[]>> updatesByPlayer = new Object2ObjectOpenHashMap<>();
        for (int dirtyIndex : dirtyDataIndices) {
            UUID objectId = dataStore.getIdForIndex(dirtyIndex);
            if (objectId == null) continue;
            VxBody body = manager.getObject(objectId);
            if (body == null) continue;

            io.netty.buffer.ByteBuf buffer = Unpooled.buffer();
            if (body.writeDirtySyncData(new VxByteBuf(buffer))) {
                byte[] data = new byte[buffer.readableBytes()];
                buffer.readBytes(data);
                Set<ServerPlayer> trackers = objectTrackers.get(objectId);
                if (trackers != null) {
                    for (ServerPlayer player : trackers) {
                        updatesByPlayer.computeIfAbsent(player, k -> new Object2ObjectArrayMap<>()).put(objectId, data);
                    }
                }
            }
            buffer.release();
        }

        if (!updatesByPlayer.isEmpty()) {
            level.getServer().execute(() -> updatesByPlayer.forEach((player, dataMap) -> {
                if (!dataMap.isEmpty()) {
                    VxPacketHandler.sendToPlayer(new S2CSynchronizedDataBatchPacket(dataMap), player);
                }
            }));
        }
    }

    /**
     * Called by the ObjectManager when an object is added to the world.
     * It checks for any players who should be tracking this new object and sends spawn packets.
     * @param body The physics object that was added.
     */
    public void onObjectAdded(VxBody body) {
        int index = body.getBodyHandle().getDataStoreIndex();
        if (index == -1) return;
        ChunkPos bodyChunk = manager.getObjectChunkPos(index);
        for (ServerPlayer player : this.level.getChunkSource().chunkMap.getPlayers(bodyChunk, false)) {
            trackObjectForPlayer(player, body);
        }
    }

    /**
     * Called by the ObjectManager when an object is removed from the physics world.
     * It ensures that all players who were tracking the object receive a removal packet.
     * @param body The physics object that was removed.
     */
    public void onObjectRemoved(VxBody body) {
        Set<ServerPlayer> trackers = objectTrackers.get(body.getPhysicsId());
        if (trackers != null) {
            for (ServerPlayer player : new ArrayList<>(trackers)) {
                untrackObjectForPlayer(player, body.getPhysicsId());
            }
        }
    }

    /**
     * Called by the VxChunkMap when an object has moved across a chunk boundary.
     * This method determines which players need to start or stop tracking the object
     * based on the visibility of the 'from' and 'to' chunks.
     *
     * @param body The object that moved.
     * @param from The chunk the object moved from.
     * @param to The chunk the object moved to.
     */
    public void onObjectMoved(VxBody body, ChunkPos from, ChunkPos to) {
        if (from.equals(to)) {
            return;
        }

        Set<ServerPlayer> playersTrackingFrom = new HashSet<>(this.level.getChunkSource().chunkMap.getPlayers(from, false));
        Set<ServerPlayer> playersTrackingTo = new HashSet<>(this.level.getChunkSource().chunkMap.getPlayers(to, false));

        for (ServerPlayer player : playersTrackingTo) {
            if (!playersTrackingFrom.contains(player)) {
                trackObjectForPlayer(player, body);
            }
        }

        for (ServerPlayer player : playersTrackingFrom) {
            if (!playersTrackingTo.contains(player)) {
                untrackObjectForPlayer(player, body.getPhysicsId());
            }
        }
    }

    /**
     * Called by a mixin when a player starts tracking a new chunk.
     * Queues spawn packets for all physics objects within that chunk for the player.
     * @param player The player who started tracking the chunk.
     * @param chunkPos The position of the newly tracked chunk.
     */
    public void trackObjectsInChunkForPlayer(ServerPlayer player, ChunkPos chunkPos) {
        List<VxBody> bodiesInChunk = manager.getObjectsInChunk(chunkPos);
        if (bodiesInChunk.isEmpty()) return;

        for (VxBody body : bodiesInChunk) {
            trackObjectForPlayer(player, body);
        }
    }

    /**
     * Called by a mixin when a player stops tracking a chunk.
     * Queues removal packets for all physics objects within that chunk for the player.
     * @param player The player who stopped tracking the chunk.
     * @param chunkPos The position of the untracked chunk.
     */
    public void untrackObjectsInChunkForPlayer(ServerPlayer player, ChunkPos chunkPos) {
        List<VxBody> bodiesInChunk = manager.getObjectsInChunk(chunkPos);
        if (bodiesInChunk.isEmpty()) return;

        for (VxBody body : bodiesInChunk) {
            untrackObjectForPlayer(player, body.getPhysicsId());
        }
    }

    /**
     * Instructs the dispatcher to track a physics object for a player.
     * This method updates tracking data structures and queues a spawn packet. It atomically
     * handles cases where a 'remove' request for the same object is already pending,
     * cancelling them out to prevent redundant network traffic.
     *
     * @param player The player who will start tracking the object.
     * @param body   The object to be tracked.
     */
    public void trackObjectForPlayer(ServerPlayer player, VxBody body) {
        Set<UUID> trackedByPlayer = playerTrackedObjects.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet());
        if (trackedByPlayer.add(body.getPhysicsId())) {
            objectTrackers.computeIfAbsent(body.getPhysicsId(), k -> ConcurrentHashMap.newKeySet()).add(player);
            synchronized (pendingSpawns) {
                ObjectArrayList<UUID> removals = pendingRemovals.get(player);
                if (removals != null && removals.remove(body.getPhysicsId())) {
                    return;
                }
                pendingSpawns.computeIfAbsent(player, k -> new ObjectArrayList<>())
                        .add(new VxSpawnData(body, System.nanoTime()));
            }
        }
    }

    /**
     * Instructs the dispatcher to stop tracking a physics object for a player.
     * This method updates tracking data structures and queues a removal packet. It atomically
     * handles cases where a 'spawn' request is already pending, cancelling them out
     * to prevent an object from being spawned and immediately removed on the client.
     *
     * @param player The player who will stop tracking the object.
     * @param bodyId The UUID of the object to stop tracking.
     */
    public void untrackObjectForPlayer(ServerPlayer player, UUID bodyId) {
        Set<UUID> trackedByPlayer = playerTrackedObjects.get(player.getUUID());
        if (trackedByPlayer != null && trackedByPlayer.remove(bodyId)) {
            Set<ServerPlayer> trackers = objectTrackers.get(bodyId);
            if (trackers != null) {
                trackers.remove(player);
                if (trackers.isEmpty()) {
                    objectTrackers.remove(bodyId);
                }
            }
            synchronized (pendingSpawns) {
                ObjectArrayList<VxSpawnData> spawns = pendingSpawns.get(player);
                if (spawns != null && spawns.removeIf(spawnData -> spawnData.id.equals(bodyId))) {
                    return;
                }
                pendingRemovals.computeIfAbsent(player, k -> new ObjectArrayList<>()).add(bodyId);
            }
        }
    }

    /**
     * Handles a player disconnecting. Cleans up all associated tracking data.
     * This method is called by a mixin when a player leaves the server.
     * @param player The player who disconnected.
     */
    public void onPlayerDisconnect(ServerPlayer player) {
        UUID playerId = player.getUUID();
        Set<UUID> trackedObjects = playerTrackedObjects.remove(playerId);
        if (trackedObjects != null) {
            for (UUID objectId : trackedObjects) {
                Set<ServerPlayer> trackers = objectTrackers.get(objectId);
                if (trackers != null) {
                    trackers.remove(player);
                    if (trackers.isEmpty()) {
                        objectTrackers.remove(objectId);
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
     * Processes the queue of pending object removals for all players.
     * This method is called on the main server thread. It sends batched removal packets to clients.
     */
    private void processPendingRemovals() {
        if (pendingRemovals.isEmpty()) return;
        synchronized (pendingRemovals) {
            pendingRemovals.forEach((player, removalList) -> {
                if (!removalList.isEmpty()) {
                    for (int i = 0; i < removalList.size(); i += MAX_REMOVALS_PER_PACKET) {
                        int end = Math.min(i + MAX_REMOVALS_PER_PACKET, removalList.size());
                        VxPacketHandler.sendToPlayer(new S2CRemoveBodyBatchPacket(removalList.subList(i, end)), player);
                    }
                }
            });
            pendingRemovals.clear();
        }
    }

    /**
     * Processes the queue of pending object spawns for all players.
     * This method is called on the main server thread. It creates and sends batched spawn packets,
     * respecting the maximum packet payload size to avoid network issues.
     */
    private void processPendingSpawns() {
        if (pendingSpawns.isEmpty()) return;
        synchronized (pendingSpawns) {
            pendingSpawns.forEach((player, spawnDataList) -> {
                if (!spawnDataList.isEmpty()) {
                    ObjectArrayList<VxSpawnData> batch = new ObjectArrayList<>();
                    int currentBatchSizeBytes = 0;
                    for (VxSpawnData data : spawnDataList) {
                        int dataSize = data.estimateSize();
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
            });
            pendingSpawns.clear();
        }
    }
}