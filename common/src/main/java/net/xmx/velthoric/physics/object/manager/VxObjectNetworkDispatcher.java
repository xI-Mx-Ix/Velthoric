/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.manager;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.NetworkHandler;
import net.xmx.velthoric.physics.object.packet.SpawnData;
import net.xmx.velthoric.physics.object.packet.batch.S2CRemoveBodyBatchPacket;
import net.xmx.velthoric.physics.object.packet.batch.S2CSpawnBodyBatchPacket;
import net.xmx.velthoric.physics.object.packet.batch.S2CUpdateBodyStateBatchPacket;
import net.xmx.velthoric.physics.object.packet.batch.S2CUpdateVerticesBatchPacket;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.vehicle.VxVehicle;
import net.xmx.velthoric.physics.vehicle.packet.S2CUpdateWheelsPacket;
import net.xmx.velthoric.physics.vehicle.wheel.VxWheel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages the network synchronization of physics objects between the server and clients.
 * This class is responsible for tracking which objects are visible to each player and
 * efficiently sending spawn, removal, and state update packets. It uses a dedicated
 * thread for state synchronization to offload work from the main server thread.
 * The implementation is optimized for high-scale environments with many players and objects,
 * minimizing GC overhead and CPU usage.
 *
 * @author xI-Mx-Ix
 */
public class VxObjectNetworkDispatcher {

    private final ServerLevel level;
    private final VxObjectManager manager;
    private final VxObjectDataStore dataStore;

    // --- Constants for network tuning ---
    private static final int NETWORK_THREAD_TICK_RATE_MS = 10;
    private static final int MAX_STATES_PER_PACKET = 50;
    private static final int MAX_VERTICES_PER_PACKET = 50;
    private static final int MAX_PACKET_PAYLOAD_SIZE = 128 * 1024;
    private static final int MAX_REMOVALS_PER_PACKET = 512;

    // --- Player tracking data structures ---
    // Maps a player's UUID to the set of physics object UUIDs they are currently tracking
    private final Map<UUID, Set<UUID>> playerTrackedObjects = new ConcurrentHashMap<>();
    // Caches the last known chunk position for each player to determine visibility
    private final Map<UUID, ChunkPos> playerChunkPositions = new ConcurrentHashMap<>();
    // Caches the view distance for each player
    private final Map<UUID, Integer> playerViewDistances = new ConcurrentHashMap<>();

    // High-performance reverse lookup map from an object's UUID to the set of players tracking it
    // This is the key to efficiently grouping updates by player without iterating all online players
    private final Map<UUID, Set<ServerPlayer>> objectTrackers = new ConcurrentHashMap<>();

    // --- Pending packet batches (accessed from main thread) ---
    // A map to queue physics objects that need to be spawned on a player's client
    private final Map<ServerPlayer, ObjectArrayList<SpawnData>> pendingSpawns = new HashMap<>();
    // A map to queue physics object UUIDs that need to be removed from a player's client
    private final Map<ServerPlayer, ObjectArrayList<UUID>> pendingRemovals = new HashMap<>();

    // The dedicated executor service for handling network synchronization tasks
    private ExecutorService networkSyncExecutor;

    public VxObjectNetworkDispatcher(ServerLevel level, VxObjectManager manager) {
        this.level = level;
        this.manager = manager;
        this.dataStore = manager.getDataStore();
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
     * Periodically collects dirty states and sends them to clients.
     */
    private void runSyncLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                long cycleStartTime = System.nanoTime();
                sendStateUpdates();
                sendWheelUpdates();
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
     * This method runs on the dedicated network sync thread.
     */
    private void sendStateUpdates() {
        // Step 1: Collect all dirty indices from the data store.
        IntArrayList dirtyTransformIndices = new IntArrayList();
        IntArrayList dirtyVertexIndices = new IntArrayList();
        // This is synchronized to prevent concurrent modification issues if the data store were to resize.
        synchronized (dataStore) {
            for (int i = 0; i < dataStore.getCapacity(); i++) {
                if (dataStore.isTransformDirty[i]) {
                    dirtyTransformIndices.add(i);
                    dataStore.isTransformDirty[i] = false; // Reset flag immediately.
                }
                if (dataStore.isVertexDataDirty[i]) {
                    dirtyVertexIndices.add(i);
                    dataStore.isVertexDataDirty[i] = false; // Reset flag immediately.
                }
            }
        }

        if (dirtyTransformIndices.isEmpty() && dirtyVertexIndices.isEmpty()) {
            return;
        }

        // Step 2: Group updates by player using the optimized reverse lookup map.
        Map<ServerPlayer, IntArrayList> transformUpdatesByPlayer = new Object2ObjectOpenHashMap<>();
        Map<ServerPlayer, IntArrayList> vertexUpdatesByPlayer = new Object2ObjectOpenHashMap<>();

        groupUpdatesByPlayer(dirtyTransformIndices, transformUpdatesByPlayer);
        groupUpdatesByPlayer(dirtyVertexIndices, vertexUpdatesByPlayer);

        // Step 3: Schedule packet sending on the main server thread for thread safety with Netty.
        if (!transformUpdatesByPlayer.isEmpty() || !vertexUpdatesByPlayer.isEmpty()) {
            level.getServer().execute(() -> {
                transformUpdatesByPlayer.forEach(this::sendBodyStatePackets);
                vertexUpdatesByPlayer.forEach(this::sendVertexDataPackets);
            });
        }
    }

    /**
     * Collects wheel state updates for all dirty vehicles and sends them to tracking players.
     * This method runs on the dedicated network sync thread but schedules the actual
     * packet sending on the main server thread for thread safety with Netty.
     */
    private void sendWheelUpdates() {
        // Step 1: Collect all vehicles that have been marked with dirty wheel states.
        // This avoids iterating over non-vehicle objects.
        List<VxVehicle> dirtyVehicles = new ArrayList<>();
        for (VxBody body : manager.getAllObjects()) {
            if (body instanceof VxVehicle vehicle && vehicle.areWheelsDirty()) {
                dirtyVehicles.add(vehicle);
                // Reset the dirty flag immediately to prevent sending the same data multiple times.
                vehicle.clearWheelsDirty();
            }
        }

        // If no vehicles need updates, exit early.
        if (dirtyVehicles.isEmpty()) {
            return;
        }

        // Step 2: Schedule the packet creation and sending on the main server thread.
        // This is crucial because network operations (sending packets) must be done
        // from the main thread in Minecraft.
        level.getServer().execute(() -> {
            for (VxVehicle vehicle : dirtyVehicles) {
                // Find all players who are currently tracking this vehicle.
                Set<ServerPlayer> trackers = objectTrackers.get(vehicle.getPhysicsId());
                if (trackers == null || trackers.isEmpty()) {
                    continue; // Skip if no one is watching this vehicle.
                }

                List<VxWheel> wheels = vehicle.getWheels();
                int wheelCount = wheels.size();
                if (wheelCount == 0) continue;

                // Step 3: Gather the wheel data into arrays for the packet constructor.
                float[] rotations = new float[wheelCount];
                float[] steers = new float[wheelCount];
                float[] suspensions = new float[wheelCount];

                for (int i = 0; i < wheelCount; i++) {
                    VxWheel wheel = wheels.get(i);
                    rotations[i] = wheel.getRotationAngle();
                    steers[i] = wheel.getSteerAngle();
                    suspensions[i] = wheel.getSuspensionLength();
                }

                // Step 4: Create a single packet with all wheel data for this vehicle.
                S2CUpdateWheelsPacket packet = new S2CUpdateWheelsPacket(vehicle.getPhysicsId(), wheelCount, rotations, steers, suspensions);

                // Step 5: Send the packet to every player tracking this vehicle.
                for (ServerPlayer player : trackers) {
                    NetworkHandler.sendToPlayer(packet, player);
                }
            }
        });
    }

    /**
     * Groups a list of dirty indices by player. This is highly optimized using the
     * {@code objectTrackers} reverse map to avoid iterating all online players.
     *
     * @param dirtyIndices    The list of dirty object indices to group.
     * @param updatesByPlayer The map to populate with player-specific update lists.
     */
    private void groupUpdatesByPlayer(IntArrayList dirtyIndices, Map<ServerPlayer, IntArrayList> updatesByPlayer) {
        for (int dirtyIndex : dirtyIndices) {
            UUID objectId = dataStore.getIdForIndex(dirtyIndex);
            if (objectId == null) continue; // Object was removed, skip.

            Set<ServerPlayer> trackers = objectTrackers.get(objectId);
            if (trackers != null) {
                for (ServerPlayer player : trackers) {
                    updatesByPlayer.computeIfAbsent(player, k -> new IntArrayList()).add(dirtyIndex);
                }
            }
        }
    }

    /**
     * Creates and sends {@link S2CUpdateBodyStateBatchPacket} for a player.
     * This method is highly optimized to avoid allocations (GC-friendly) by building packet
     * data directly into arrays of a fixed maximum size. It also safely handles race conditions
     * where an object might be removed after being marked dirty.
     * This must be called on the main server thread.
     *
     * @param player  The player to send the packet to.
     * @param indices The list of data store indices for objects to include in the packet.
     */
    private void sendBodyStatePackets(ServerPlayer player, IntArrayList indices) {
        if (player.connection == null || indices.isEmpty()) {
            return;
        }

        // --- GC-FRIENDLY & HIGH-PERFORMANCE PACKET BUILDING ---
        // Pre-allocate arrays to the maximum possible size for a batch.
        UUID[] ids = new UUID[MAX_STATES_PER_PACKET];
        long[] timestamps = new long[MAX_STATES_PER_PACKET];
        double[] posX = new double[MAX_STATES_PER_PACKET], posY = new double[MAX_STATES_PER_PACKET], posZ = new double[MAX_STATES_PER_PACKET];
        float[] rotX = new float[MAX_STATES_PER_PACKET], rotY = new float[MAX_STATES_PER_PACKET], rotZ = new float[MAX_STATES_PER_PACKET], rotW = new float[MAX_STATES_PER_PACKET];
        float[] velX = new float[MAX_STATES_PER_PACKET], velY = new float[MAX_STATES_PER_PACKET], velZ = new float[MAX_STATES_PER_PACKET];
        boolean[] isActive = new boolean[MAX_STATES_PER_PACKET];

        int currentBatchCount = 0;
        for (int index : indices) {
            // --- RACE CONDITION SAFETY CHECK ---
            // Fetch the UUID. If it's null, the object was removed between the dirty check
            // and now. We must skip it to prevent a NullPointerException.
            UUID uuid = dataStore.getIdForIndex(index);
            if (uuid == null) {
                continue;
            }

            // Add valid object data to the current batch at the next available slot.
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

            // When the batch is full, send it and reset the counter.
            if (currentBatchCount == MAX_STATES_PER_PACKET) {
                S2CUpdateBodyStateBatchPacket packet = new S2CUpdateBodyStateBatchPacket(currentBatchCount, ids, timestamps, posX, posY, posZ, rotX, rotY, rotZ, rotW, velX, velY, velZ, isActive);
                NetworkHandler.sendToPlayer(packet, player);
                currentBatchCount = 0; // Reset for the next batch
            }
        }

        // Send any remaining items in the last, partially-filled batch.
        if (currentBatchCount > 0) {
            S2CUpdateBodyStateBatchPacket packet = new S2CUpdateBodyStateBatchPacket(currentBatchCount, ids, timestamps, posX, posY, posZ, rotX, rotY, rotZ, rotW, velX, velY, velZ, isActive);
            NetworkHandler.sendToPlayer(packet, player);
        }
    }

    /**
     * Creates and sends {@link S2CUpdateVerticesBatchPacket} for a player.
     * This must be called on the main server thread.
     *
     * @param player  The player to send the packet to.
     * @param indices The list of data store indices for objects to include in the packet.
     */
    private void sendVertexDataPackets(ServerPlayer player, IntArrayList indices) {
        if (player.connection == null || indices.isEmpty()) {
            return;
        }

        // This method can also be optimized similarly if it becomes a bottleneck,
        // but vertex data updates are typically less frequent than transform updates.
        // For now, we keep the simpler implementation.
        for (int i = 0; i < indices.size(); i += MAX_VERTICES_PER_PACKET) {
            int end = Math.min(i + MAX_VERTICES_PER_PACKET, indices.size());
            List<Integer> sublist = indices.subList(i, end);

            // Using temporary lists here is acceptable as vertex updates are less frequent.
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
                NetworkHandler.sendToPlayer(packet, player);
            }
        }
    }

    /**
     * Handles a new physics object being added to the world.
     * Checks which players should see the object and starts tracking it for them.
     *
     * @param body The physics object that was added.
     */
    public void onObjectAdded(VxBody body) {
        int index = body.getDataStoreIndex();
        if (index == -1) return;
        ChunkPos bodyChunk = manager.getObjectChunkPos(index);
        for (ServerPlayer player : level.players()) {
            if (isChunkVisible(player, bodyChunk)) {
                startTracking(player, body);
            }
        }
    }

    /**
     * Handles a physics object being removed from the world.
     * It stops tracking the object for all players, triggering a removal packet.
     *
     * @param body The physics object that was removed.
     */
    public void onObjectRemoved(VxBody body) {
        // A copy is made to avoid ConcurrentModificationException if a player disconnects during iteration.
        Set<ServerPlayer> trackers = objectTrackers.get(body.getPhysicsId());
        if (trackers != null) {
            for (ServerPlayer player : new ArrayList<>(trackers)) {
                stopTracking(player, body.getPhysicsId());
            }
        }
    }

    /**
     * Handles a physics object moving between chunks.
     * Updates tracking for players based on whether the object moved into or out of their view.
     *
     * @param body The object that moved.
     * @param from The chunk the object moved from.
     * @param to   The chunk the object moved to.
     */
    public void onObjectMoved(VxBody body, ChunkPos from, ChunkPos to) {
        for (ServerPlayer player : level.players()) {
            boolean wasVisible = isChunkVisible(player, from);
            boolean isVisible = isChunkVisible(player, to);

            if (wasVisible && !isVisible) {
                stopTracking(player, body.getPhysicsId());
            } else if (!wasVisible && isVisible) {
                startTracking(player, body);
            }
        }
    }

    /**
     * Updates the set of tracked physics objects for a specific player.
     * This is typically called when a player moves or their view distance changes.
     *
     * @param player The player whose tracking information should be updated.
     */
    public void updatePlayerTracking(ServerPlayer player) {
        playerChunkPositions.put(player.getUUID(), player.chunkPosition());
        playerViewDistances.put(player.getUUID(), player.server.getPlayerList().getViewDistance());

        Set<UUID> previouslyTracked = playerTrackedObjects.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet());
        Set<UUID> newlyVisible = new HashSet<>();

        int viewDistance = playerViewDistances.getOrDefault(player.getUUID(), 0);
        ChunkPos playerChunkPos = playerChunkPositions.getOrDefault(player.getUUID(), new ChunkPos(0, 0));

        // Find all objects within the player's view distance.
        for (int cz = playerChunkPos.z - viewDistance; cz <= playerChunkPos.z + viewDistance; ++cz) {
            for (int cx = playerChunkPos.x - viewDistance; cx <= playerChunkPos.x + viewDistance; ++cx) {
                for (VxBody body : manager.getObjectsInChunk(new ChunkPos(cx, cz))) {
                    newlyVisible.add(body.getPhysicsId());
                }
            }
        }

        // Stop tracking objects that are no longer visible.
        for (UUID trackedId : new ArrayList<>(previouslyTracked)) {
            if (!newlyVisible.contains(trackedId)) {
                stopTracking(player, trackedId);
            }
        }

        // Start tracking newly visible objects.
        for (UUID visibleId : newlyVisible) {
            if (!previouslyTracked.contains(visibleId)) {
                VxBody body = manager.getObject(visibleId);
                if (body != null) {
                    startTracking(player, body);
                }
            }
        }
    }

    /**
     * Starts tracking a physics object for a player, updating all relevant maps
     * and queuing a spawn packet.
     *
     * @param player The player who will start tracking the object.
     * @param body   The object to be tracked.
     */
    private void startTracking(ServerPlayer player, VxBody body) {
        Set<UUID> trackedByPlayer = playerTrackedObjects.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet());
        if (trackedByPlayer.add(body.getPhysicsId())) {
            // Add to the reverse lookup map for efficient updates.
            objectTrackers.computeIfAbsent(body.getPhysicsId(), k -> ConcurrentHashMap.newKeySet()).add(player);

            synchronized (pendingSpawns) {
                pendingSpawns.computeIfAbsent(player, k -> new ObjectArrayList<>())
                        .add(new SpawnData(body, System.nanoTime()));
            }
        }
    }

    /**
     * Stops tracking a physics object for a player, updating all relevant maps
     * and queuing a removal packet.
     *
     * @param player The player who will stop tracking the object.
     * @param bodyId The UUID of the object to stop tracking.
     */
    private void stopTracking(ServerPlayer player, UUID bodyId) {
        Set<UUID> trackedByPlayer = playerTrackedObjects.get(player.getUUID());
        if (trackedByPlayer != null && trackedByPlayer.remove(bodyId)) {
            // Remove from the reverse lookup map.
            Set<ServerPlayer> trackers = objectTrackers.get(bodyId);
            if (trackers != null) {
                trackers.remove(player);
                if (trackers.isEmpty()) {
                    objectTrackers.remove(bodyId); // Clean up to prevent memory leaks.
                }
            }

            synchronized (pendingRemovals) {
                pendingRemovals.computeIfAbsent(player, k -> new ObjectArrayList<>())
                        .add(bodyId);
            }
        }
    }

    /**
     * Checks if a given chunk is within a player's current view distance using cached data.
     * @param player The player.
     * @param chunkPos The chunk position to check.
     * @return True if the chunk is visible to the player, false otherwise.
     */
    private boolean isChunkVisible(ServerPlayer player, ChunkPos chunkPos) {
        Integer viewDistance = playerViewDistances.get(player.getUUID());
        ChunkPos playerChunkPos = playerChunkPositions.get(player.getUUID());
        if (viewDistance == null || playerChunkPos == null) {
            return false; // Player data not yet cached
        }
        return Math.abs(chunkPos.x - playerChunkPos.x) <= viewDistance &&
                Math.abs(chunkPos.z - playerChunkPos.z) <= viewDistance;
    }

    /**
     * Handles a player joining the game. Initializes their tracking data.
     * @param player The player who joined.
     */
    public void onPlayerJoin(ServerPlayer player) {
        updatePlayerTracking(player);
    }

    /**
     * Handles a player disconnecting from the game. Cleans up all tracking data associated with them.
     * @param player The player who disconnected.
     */
    public void onPlayerDisconnect(ServerPlayer player) {
        UUID playerId = player.getUUID();
        Set<UUID> trackedObjects = playerTrackedObjects.remove(playerId);

        // Clean up the reverse tracker map.
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

        // Clean up caches and pending packets.
        playerChunkPositions.remove(playerId);
        playerViewDistances.remove(playerId);
        synchronized (pendingSpawns) {
            pendingSpawns.remove(player);
        }
        synchronized (pendingRemovals) {
            pendingRemovals.remove(player);
        }
    }

    /**
     * Processes and sends batched removal packets for all players.
     * This is called from the main game tick.
     */
    private void processPendingRemovals() {
        if (pendingRemovals.isEmpty()) return;

        synchronized (pendingRemovals) {
            pendingRemovals.forEach((player, removalList) -> {
                if (!removalList.isEmpty()) {
                    for (int i = 0; i < removalList.size(); i += MAX_REMOVALS_PER_PACKET) {
                        int end = Math.min(i + MAX_REMOVALS_PER_PACKET, removalList.size());
                        NetworkHandler.sendToPlayer(new S2CRemoveBodyBatchPacket(removalList.subList(i, end)), player);
                    }
                }
            });
            pendingRemovals.clear();
        }
    }

    /**
     * Processes and sends batched spawn packets for all players.
     * This is called from the main game tick.
     */
    private void processPendingSpawns() {
        if (pendingSpawns.isEmpty()) return;

        synchronized (pendingSpawns) {
            pendingSpawns.forEach((player, spawnDataList) -> {
                if (!spawnDataList.isEmpty()) {
                    ObjectArrayList<SpawnData> batch = new ObjectArrayList<>();
                    int currentBatchSizeBytes = 0;
                    for (SpawnData data : spawnDataList) {
                        int dataSize = data.estimateSize();
                        // Send the current batch if adding the next item would exceed the payload limit.
                        if (!batch.isEmpty() && currentBatchSizeBytes + dataSize > MAX_PACKET_PAYLOAD_SIZE) {
                            NetworkHandler.sendToPlayer(new S2CSpawnBodyBatchPacket(batch), player);
                            batch.clear();
                            currentBatchSizeBytes = 0;
                        }
                        batch.add(data);
                        currentBatchSizeBytes += dataSize;
                    }
                    // Send the final batch.
                    if (!batch.isEmpty()) {
                        NetworkHandler.sendToPlayer(new S2CSpawnBodyBatchPacket(batch), player);
                    }
                }
            });
            pendingSpawns.clear();
        }
    }
}