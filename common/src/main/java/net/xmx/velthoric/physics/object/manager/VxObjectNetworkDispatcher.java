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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages the network synchronization of physics objects between the server and clients.
 * This class is responsible for tracking which objects are visible to each player and
 * efficiently sending spawn, removal, and state update packets. It uses a dedicated
 * thread for state synchronization to offload work from the main server thread.
 *
 * @author xI-Mx-Ix
 */
public class VxObjectNetworkDispatcher {

    private final ServerLevel level;
    private final VxObjectManager manager;
    private final VxObjectDataStore dataStore;

    // --- Constants for network tuning ---
    // The target tick rate for the network synchronization thread in milliseconds.
    private static final int NETWORK_THREAD_TICK_RATE_MS = 10;
    // The maximum number of object state updates to include in a single state synchronization packet.
    private static final int MAX_STATES_PER_PACKET = 50;
    // The maximum number of vertex updates to include in a single vertex synchronization packet.
    private static final int MAX_VERTICES_PER_PACKET = 50;
    // The maximum size of a packet payload in bytes to avoid exceeding network limits.
    private static final int MAX_PACKET_PAYLOAD_SIZE = 128 * 1024;
    // The maximum number of removals to include in a single batch packet.
    private static final int MAX_REMOVALS_PER_PACKET = 512;

    // --- Player tracking data structures ---
    // Maps each player's UUID to the set of physics object UUIDs they are currently tracking.
    private final Map<UUID, Set<UUID>> playerTrackedObjects = new ConcurrentHashMap<>();
    // Caches the last known chunk position for each player to determine visibility.
    private final Map<UUID, ChunkPos> playerChunkPositions = new ConcurrentHashMap<>();
    // Caches the view distance for each player.
    private final Map<UUID, Integer> playerViewDistances = new ConcurrentHashMap<>();

    // --- Pending packet batches (accessed from main thread) ---
    // A map to queue physics objects that need to be spawned on a player's client.
    private final Map<ServerPlayer, ObjectArrayList<SpawnData>> pendingSpawns = new HashMap<>();
    // A map to queue physics object UUIDs that need to be removed from a player's client.
    private final Map<ServerPlayer, ObjectArrayList<UUID>> pendingRemovals = new HashMap<>();

    // The dedicated executor service for handling network synchronization tasks.
    private ExecutorService networkSyncExecutor;

    public VxObjectNetworkDispatcher(ServerLevel level, VxObjectManager manager) {
        this.level = level;
        this.manager = manager;
        this.dataStore = manager.getDataStore();
    }

    // Starts the dedicated network synchronization thread.
    public void start() {
        this.networkSyncExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Velthoric-Network-Sync-Thread"));
        this.networkSyncExecutor.submit(this::runSyncLoop);
    }

    // Stops the network synchronization thread gracefully.
    public void stop() {
        if (this.networkSyncExecutor != null) {
            this.networkSyncExecutor.shutdownNow();
        }
    }

    // Called every game tick from the main server thread to process pending spawns, removals, and custom data updates.
    public void onGameTick() {
        processPendingRemovals();
        processPendingSpawns();
    }

    /**
     * The main loop for the network synchronization thread.
     * Periodically sends state updates for dirty objects to relevant clients.
     */
    private void runSyncLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                long cycleStartTime = System.nanoTime();

                // Build and schedule packets for sending.
                sendStateUpdates();

                long cycleEndTime = System.nanoTime();

                // Calculate sleep time to maintain the target tick rate.
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
        // Step 1: Collect all dirty indices from the data store into separate lists.
        IntArrayList dirtyTransformIndices = new IntArrayList();
        IntArrayList dirtyVertexIndices = new IntArrayList();
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

        if (dirtyTransformIndices.isEmpty() && dirtyVertexIndices.isEmpty()) {
            return;
        }

        // Step 2: Group updates by player.
        Map<ServerPlayer, IntArrayList> transformUpdatesByPlayer = new Object2ObjectOpenHashMap<>();
        Map<ServerPlayer, IntArrayList> vertexUpdatesByPlayer = new Object2ObjectOpenHashMap<>();

        // Group transform updates
        groupUpdatesByPlayer(dirtyTransformIndices, transformUpdatesByPlayer);
        // Group vertex updates
        groupUpdatesByPlayer(dirtyVertexIndices, vertexUpdatesByPlayer);


        // Step 3: Schedule packet sending on the main server thread to ensure thread safety with Netty.
        level.getServer().execute(() -> {
            transformUpdatesByPlayer.forEach(this::sendBodyStatePackets);
            vertexUpdatesByPlayer.forEach(this::sendVertexDataPackets);
        });
    }

    /**
     * Helper method to group a list of dirty indices by which player is tracking them.
     */
    private void groupUpdatesByPlayer(IntArrayList dirtyIndices, Map<ServerPlayer, IntArrayList> updatesByPlayer) {
        for (int dirtyIndex : dirtyIndices) {
            UUID objectId = dataStore.getIdForIndex(dirtyIndex);
            if (objectId == null) continue;

            // Find all players tracking this object.
            for (ServerPlayer player : level.players()) {
                Set<UUID> trackedByPlayer = playerTrackedObjects.get(player.getUUID());
                if (trackedByPlayer != null && trackedByPlayer.contains(objectId)) {
                    updatesByPlayer.computeIfAbsent(player, k -> new IntArrayList()).add(dirtyIndex);
                }
            }
        }
    }

    /**
     * Creates and sends S2CUpdateBodyStateBatchPacket for a player.
     * This must be called on the main server thread.
     */
    private void sendBodyStatePackets(ServerPlayer player, IntArrayList indices) {
        if (player.connection == null || indices.isEmpty()) {
            return;
        }

        for (int i = 0; i < indices.size(); i += MAX_STATES_PER_PACKET) {
            int end = Math.min(i + MAX_STATES_PER_PACKET, indices.size());
            List<Integer> sublist = indices.subList(i, end);
            int count = sublist.size();

            // Prepare SoA arrays
            UUID[] ids = new UUID[count];
            long[] timestamps = new long[count];
            double[] posX = new double[count], posY = new double[count], posZ = new double[count];
            float[] rotX = new float[count], rotY = new float[count], rotZ = new float[count], rotW = new float[count];
            float[] velX = new float[count], velY = new float[count], velZ = new float[count];
            boolean[] isActive = new boolean[count];

            for (int j = 0; j < count; j++) {
                int index = sublist.get(j);
                ids[j] = dataStore.getIdForIndex(index);
                timestamps[j] = dataStore.lastUpdateTimestamp[index];
                posX[j] = dataStore.posX[index];
                posY[j] = dataStore.posY[index];
                posZ[j] = dataStore.posZ[index];
                rotX[j] = dataStore.rotX[index];
                rotY[j] = dataStore.rotY[index];
                rotZ[j] = dataStore.rotZ[index];
                rotW[j] = dataStore.rotW[index];
                isActive[j] = dataStore.isActive[index];
                velX[j] = dataStore.velX[index];
                velY[j] = dataStore.velY[index];
                velZ[j] = dataStore.velZ[index];
            }

            S2CUpdateBodyStateBatchPacket packet = new S2CUpdateBodyStateBatchPacket(count, ids, timestamps, posX, posY, posZ, rotX, rotY, rotZ, rotW, velX, velY, velZ, isActive);
            NetworkHandler.sendToPlayer(packet, player);
        }
    }

    /**
     * Creates and sends S2CUpdateVerticesBatchPacket for a player.
     * This must be called on the main server thread.
     */
    private void sendVertexDataPackets(ServerPlayer player, IntArrayList indices) {
        if (player.connection == null || indices.isEmpty()) {
            return;
        }

        for (int i = 0; i < indices.size(); i += MAX_VERTICES_PER_PACKET) {
            int end = Math.min(i + MAX_VERTICES_PER_PACKET, indices.size());
            List<Integer> sublist = indices.subList(i, end);
            int count = sublist.size();

            UUID[] ids = new UUID[count];
            float[][] vertexData = new float[count][];

            for (int j = 0; j < count; j++) {
                int index = sublist.get(j);
                ids[j] = dataStore.getIdForIndex(index);
                vertexData[j] = dataStore.vertexData[index];
            }

            S2CUpdateVerticesBatchPacket packet = new S2CUpdateVerticesBatchPacket(count, ids, vertexData);
            NetworkHandler.sendToPlayer(packet, player);
        }
    }

    /**
     * Handles a new physics object being added to the world.
     * It checks which players should see the object and starts tracking it for them.
     *
     * @param body The physics object that was added.
     */
    public void onObjectAdded(VxBody body) {
        ChunkPos bodyChunk = manager.getObjectChunkPos(body.getDataStoreIndex());
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
        for (ServerPlayer player : level.players()) {
            stopTracking(player, body.getPhysicsId());
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
        // Update cached player data.
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
                VxBody body = manager.getObject(trackedId);
                if(body != null) stopTracking(player, body.getPhysicsId());
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
     * Starts tracking a physics object for a player and queues a spawn packet.
     *
     * @param player The player who will start tracking the object.
     * @param body   The object to be tracked.
     */
    private void startTracking(ServerPlayer player, VxBody body) {
        Set<UUID> tracked = playerTrackedObjects.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet());
        if (tracked.add(body.getPhysicsId())) {
            synchronized (pendingSpawns) {
                pendingSpawns.computeIfAbsent(player, k -> new ObjectArrayList<>())
                        .add(new SpawnData(body, System.nanoTime()));
            }
        }
    }

    /**
     * Stops tracking a physics object for a player and queues a removal packet.
     *
     * @param player The player who will stop tracking the object.
     * @param bodyId The UUID of the object to stop tracking.
     */
    private void stopTracking(ServerPlayer player, UUID bodyId) {
        Set<UUID> tracked = playerTrackedObjects.get(player.getUUID());
        if (tracked != null && tracked.remove(bodyId)) {
            synchronized (pendingRemovals) {
                pendingRemovals.computeIfAbsent(player, k -> new ObjectArrayList<>())
                        .add(bodyId);
            }
        }
    }

    /**
     * Checks if a given chunk is within a player's current view distance.
     */
    private boolean isChunkVisible(ServerPlayer player, ChunkPos chunkPos) {
        Integer viewDistance = playerViewDistances.get(player.getUUID());
        ChunkPos playerChunkPos = playerChunkPositions.get(player.getUUID());
        if (viewDistance == null || playerChunkPos == null) {
            return false;
        }
        return Math.abs(chunkPos.x - playerChunkPos.x) <= viewDistance &&
                Math.abs(chunkPos.z - playerChunkPos.z) <= viewDistance;
    }

    /**
     * Handles a player joining the game. Initializes their tracking data.
     */
    public void onPlayerJoin(ServerPlayer player) {
        updatePlayerTracking(player);
    }

    /**
     * Handles a player disconnecting from the game. Cleans up their tracking data.
     */
    public void onPlayerDisconnect(ServerPlayer player) {
        this.playerTrackedObjects.remove(player.getUUID());
        this.playerChunkPositions.remove(player.getUUID());
        this.playerViewDistances.remove(player.getUUID());
        synchronized (pendingSpawns) {
            this.pendingSpawns.remove(player);
        }
        synchronized (pendingRemovals) {
            this.pendingRemovals.remove(player);
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