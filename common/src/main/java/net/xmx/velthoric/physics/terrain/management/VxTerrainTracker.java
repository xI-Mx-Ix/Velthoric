/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.management;

import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.physics.object.manager.VxObjectDataStore;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.terrain.VxSectionPos;
import net.xmx.velthoric.physics.terrain.job.VxTaskPriority;
import net.xmx.velthoric.physics.terrain.storage.VxChunkDataStore;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tracks physics objects in the world and determines which terrain chunks are required
 * for their simulation. It manages preloading, activation, and deactivation of chunks
 * based on object positions and velocities, reading state directly from the data store
 * for maximum performance. This implementation is optimized to handle thousands of bodies
 * by time-slicing updates and prioritizing active objects.
 *
 * @author xI-Mx-Ix
 */
public final class VxTerrainTracker {

    private final VxPhysicsWorld physicsWorld;
    private final VxTerrainManager terrainManager;
    private final VxChunkDataStore chunkDataStore;
    private final ServerLevel level;
    private final VxObjectDataStore objectDataStore;

    private final Map<UUID, Set<VxSectionPos>> objectTrackedChunks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> objectUpdateCooldowns = new ConcurrentHashMap<>();

    // --- Configuration Constants ---
    private static final int UPDATE_INTERVAL_TICKS = 10; // Base cooldown ticks between updates for an object.
    private static final float MAX_SPEED_FOR_COOLDOWN_SQR = 100f * 100f; // Objects faster than this are always updated.
    private static final int PRELOAD_RADIUS_CHUNKS = 3; // Radius around an object to request/preload chunks.
    private static final int ACTIVATION_RADIUS_CHUNKS = 1; // Radius around an object to make chunks physically active.
    private static final float PREDICTION_SECONDS = 0.5f; // How far into the future to predict object movement for preloading.
    private static final int OBJECTS_PER_TICK = 100; // How many objects to process for preloading each tick.

    private int objectUpdateIndex = 0;

    public VxTerrainTracker(VxPhysicsWorld physicsWorld, VxTerrainManager terrainManager, VxChunkDataStore chunkDataStore, ServerLevel level) {
        this.physicsWorld = physicsWorld;
        this.terrainManager = terrainManager;
        this.chunkDataStore = chunkDataStore;
        this.level = level;
        this.objectDataStore = physicsWorld.getObjectManager().getDataStore();
    }

    /**
     * Performs a single update tick of the tracker.
     * This method efficiently handles thousands of objects by:
     * 1. Time-slicing the preloading logic, processing only a small batch of objects per tick.
     * 2. Calculating the required active chunks for physically active bodies AND for bodies awaiting initial activation.
     */
    public void update() {
        // Get a snapshot of all currently managed objects.
        List<VxBody> currentObjects = new ArrayList<>(physicsWorld.getObjectManager().getAllObjects());
        if (currentObjects.isEmpty()) {
            deactivateAllChunks();
            return;
        }

        // Clean up tracking data for objects that have been removed from the world.
        cleanupRemovedObjects(currentObjects);

        // Process a small batch of objects for preloading to distribute the load over time.
        updateObjectPreloading(currentObjects);

        // Determine which chunks need to be physically active.
        updateChunkActivation(currentObjects);
    }

    /**
     * Processes a subset of objects each tick to manage which chunks they should be preloading.
     * This is the core of the time-slicing optimization.
     */
    private void updateObjectPreloading(List<VxBody> allObjects) {
        int objectCount = allObjects.size();
        if (objectCount == 0) return;

        int objectsToProcess = Math.min(objectCount, OBJECTS_PER_TICK);

        for (int i = 0; i < objectsToProcess; ++i) {
            objectUpdateIndex = (objectUpdateIndex + 1) % objectCount;
            VxBody obj = allObjects.get(objectUpdateIndex);
            int dataIndex = obj.getDataStoreIndex();

            if (dataIndex == -1 || obj.getBodyId() == 0) {
                removeObjectTracking(obj.getPhysicsId());
                continue;
            }

            // Check and update the cooldown for this object.
            int cooldown = objectUpdateCooldowns.getOrDefault(obj.getPhysicsId(), 0);
            float velSq = getVelocitySq(dataIndex);

            if (cooldown > 0 && velSq < MAX_SPEED_FOR_COOLDOWN_SQR) {
                objectUpdateCooldowns.put(obj.getPhysicsId(), cooldown - 1);
            } else {
                // Time to update this object's tracking.
                objectUpdateCooldowns.put(obj.getPhysicsId(), UPDATE_INTERVAL_TICKS);
                processPreloadForObject(obj.getPhysicsId(), dataIndex);
            }
        }
    }

    /**
     * Recalculates the full set of physically active chunks required by bodies that are either
     * already moving or are waiting to be activated for the first time.
     */
    private void updateChunkActivation(List<VxBody> allObjects) {
        Set<VxSectionPos> requiredActiveSet = new HashSet<>();

        // *** THE FIX IS HERE ***
        // Consider bodies for activation if they are either already active (moving)
        // OR if they are waiting for their initial activation. This breaks the deadlock.
        for (VxBody obj : allObjects) {
            int dataIndex = obj.getDataStoreIndex();
            if (dataIndex != -1 && (objectDataStore.isActive[dataIndex] || objectDataStore.isAwaitingActivation[dataIndex])) {
                calculateRequiredChunks(dataIndex, ACTIVATION_RADIUS_CHUNKS, requiredActiveSet);
            }
        }

        // Prioritize the generation of these critical chunks.
        requiredActiveSet.forEach(pos -> terrainManager.prioritizeChunk(pos, VxTaskPriority.CRITICAL));

        // Get the set of chunks that are currently active in the world.
        Set<VxSectionPos> currentlyActive = chunkDataStore.getActiveIndices().stream()
                .filter(index -> chunkDataStore.states[index] == 4 /* STATE_READY_ACTIVE */)
                .map(chunkDataStore::getPosForIndex)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Deactivate chunks that are no longer needed.
        for (VxSectionPos pos : currentlyActive) {
            if (!requiredActiveSet.contains(pos)) {
                terrainManager.deactivateChunk(pos);
            }
        }

        // Activate chunks that are newly required.
        for (VxSectionPos pos : requiredActiveSet) {
            if (!currentlyActive.contains(pos)) {
                terrainManager.activateChunk(pos);
            }
        }
    }

    /**
     * Calculates and applies the required preloaded chunks for a single object.
     * It compares the new set of required chunks with the previously tracked set and
     * issues requests/releases to the TerrainManager accordingly.
     */
    private void processPreloadForObject(UUID id, int dataIndex) {
        Set<VxSectionPos> requiredPreloadSet = new HashSet<>();
        calculateRequiredChunks(dataIndex, PRELOAD_RADIUS_CHUNKS, requiredPreloadSet);

        Set<VxSectionPos> previouslyTracked = objectTrackedChunks.computeIfAbsent(id, k -> new HashSet<>());

        // Release chunks that are no longer needed by this object.
        previouslyTracked.removeIf(pos -> {
            if (!requiredPreloadSet.contains(pos)) {
                terrainManager.releaseChunk(pos);
                return true;
            }
            return false;
        });

        // Request chunks that are newly required.
        for (VxSectionPos pos : requiredPreloadSet) {
            if (previouslyTracked.add(pos)) {
                terrainManager.requestChunk(pos);
            }
        }
    }

    /**
     * Calculates the set of chunk sections required for a body based on its AABB, velocity prediction, and a given radius.
     *
     * @param dataIndex The index of the body in the data store.
     * @param radius The radius in chunks to include around the body.
     * @param outChunks The set to which the required chunk positions will be added.
     */
    private void calculateRequiredChunks(int dataIndex, int radius, Set<VxSectionPos> outChunks) {
        // Read current AABB from data store
        float minX = objectDataStore.aabbMinX[dataIndex];
        float minY = objectDataStore.aabbMinY[dataIndex];
        float minZ = objectDataStore.aabbMinZ[dataIndex];
        float maxX = objectDataStore.aabbMaxX[dataIndex];
        float maxY = objectDataStore.aabbMaxY[dataIndex];
        float maxZ = objectDataStore.aabbMaxZ[dataIndex];

        // Add chunks for the current position
        addChunksForBounds(minX, minY, minZ, maxX, maxY, maxZ, radius, outChunks);

        // Predict future position and add chunks for it as well, but only if the object is moving.
        float velX = objectDataStore.velX[dataIndex];
        float velY = objectDataStore.velY[dataIndex];
        float velZ = objectDataStore.velZ[dataIndex];

        if (Math.abs(velX) > 0.01f || Math.abs(velY) > 0.01f || Math.abs(velZ) > 0.01f) {
            float predMinX = minX + velX * PREDICTION_SECONDS;
            float predMinY = minY + velY * PREDICTION_SECONDS;
            float predMinZ = minZ + velZ * PREDICTION_SECONDS;
            float predMaxX = maxX + velX * PREDICTION_SECONDS;
            float predMaxY = maxY + velY * PREDICTION_SECONDS;
            float predMaxZ = maxZ + velZ * PREDICTION_SECONDS;
            addChunksForBounds(predMinX, predMinY, predMinZ, predMaxX, predMaxY, predMaxZ, radius, outChunks);
        }
    }

    /**
     * A helper method to populate a set with all chunk sections that overlap a given AABB plus a radius.
     */
    private void addChunksForBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int radiusInChunks, Set<VxSectionPos> outChunks) {
        int minSectionX = ((int) Math.floor(minX) >> 4) - radiusInChunks;
        int minSectionY = ((int) Math.floor(minY) >> 4) - radiusInChunks;
        int minSectionZ = ((int) Math.floor(minZ) >> 4) - radiusInChunks;
        int maxSectionX = ((int) Math.floor(maxX) >> 4) + radiusInChunks;
        int maxSectionY = ((int) Math.floor(maxY) >> 4) + radiusInChunks;
        int maxSectionZ = ((int) Math.floor(maxZ) >> 4) + radiusInChunks;

        final int worldMinY = level.getMinBuildHeight() >> 4;
        final int worldMaxY = level.getMaxBuildHeight() >> 4;

        for (int y = minSectionY; y <= maxSectionY; ++y) {
            if (y < worldMinY || y >= worldMaxY) continue;
            for (int z = minSectionZ; z <= maxSectionZ; ++z) {
                for (int x = minSectionX; x <= maxSectionX; ++x) {
                    outChunks.add(new VxSectionPos(x, y, z));
                }
            }
        }
    }

    /**
     * Removes all tracking information for a specific object and releases its held chunks.
     * @param id The UUID of the object to stop tracking.
     */
    private void removeObjectTracking(UUID id) {
        Set<VxSectionPos> chunksToRelease = objectTrackedChunks.remove(id);
        if (chunksToRelease != null) {
            chunksToRelease.forEach(terrainManager::releaseChunk);
        }
        objectUpdateCooldowns.remove(id);
    }

    /**
     * Iterates through the list of tracked objects and removes any that are no longer present in the world.
     */
    private void cleanupRemovedObjects(List<VxBody> currentObjects) {
        Set<UUID> currentObjectIds = currentObjects.stream().map(VxBody::getPhysicsId).collect(Collectors.toSet());
        objectTrackedChunks.keySet().removeIf(id -> {
            if (!currentObjectIds.contains(id)) {
                removeObjectTracking(id);
                return true;
            }
            return false;
        });
    }

    /** Deactivates all currently managed terrain chunks. Used when no objects are in the world. */
    private void deactivateAllChunks() {
        chunkDataStore.getActiveIndices().stream()
                .map(chunkDataStore::getPosForIndex)
                .filter(Objects::nonNull)
                .forEach(terrainManager::deactivateChunk);
    }

    private float getVelocitySq(int dataIndex) {
        float vx = objectDataStore.velX[dataIndex];
        float vy = objectDataStore.velY[dataIndex];
        float vz = objectDataStore.velZ[dataIndex];
        return vx * vx + vy * vy + vz * vz;
    }

    /** Clears all tracking data. Used during shutdown. */
    public void clear() {
        objectTrackedChunks.clear();
        objectUpdateCooldowns.clear();
        objectUpdateIndex = 0;
    }
}