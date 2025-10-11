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
import java.util.stream.IntStream;

/**
 * Tracks physics objects in the world and determines which terrain chunks are required
 * for their simulation. It manages preloading, activation, and deactivation of chunks
 * based on object positions and velocities, reading state directly from the data store
 * for maximum performance.
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

    private static final int UPDATE_INTERVAL_TICKS = 10;
    private static final float MAX_SPEED_FOR_COOLDOWN_SQR = 100f * 100f;
    private static final int PRELOAD_RADIUS_CHUNKS = 3;
    private static final int ACTIVATION_RADIUS_CHUNKS = 1;
    private static final float PREDICTION_SECONDS = 0.5f;

    private int objectUpdateIndex = 0;

    private static final int OBJECT_PRELOAD_UPDATE_STRIDE = 250;

    public VxTerrainTracker(VxPhysicsWorld physicsWorld, VxTerrainManager terrainManager, VxChunkDataStore chunkDataStore, ServerLevel level) {
        this.physicsWorld = physicsWorld;
        this.terrainManager = terrainManager;
        this.chunkDataStore = chunkDataStore;
        this.level = level;
        this.objectDataStore = physicsWorld.getObjectManager().getDataStore();
    }

    /**
     * Performs a single update tick of the tracker. This should be called periodically.
     * It updates preloading for a subset of objects and recalculates the set of
     * active chunks required by all objects.
     */
    public void update() {
        List<VxBody> currentObjects = new ArrayList<>(physicsWorld.getObjectManager().getAllObjects());
        Set<UUID> currentObjectIds = currentObjects.stream().map(VxBody::getPhysicsId).collect(Collectors.toSet());

        objectTrackedChunks.keySet().removeIf(id -> {
            if (!currentObjectIds.contains(id)) {
                removeObjectTracking(id);
                return true;
            }
            return false;
        });

        if (currentObjects.isEmpty()) {
            chunkDataStore.getActiveIndices().stream()
                    .map(chunkDataStore::getPosForIndex)
                    .filter(Objects::nonNull)
                    .forEach(terrainManager::deactivateChunk);
            return;
        }

        updatePreloading(currentObjects);
        updateActivation(currentObjects);
    }

    /**
     * Updates the preloaded chunks for a subset of tracked objects by reading directly from the data store.
     */
    private void updatePreloading(List<VxBody> currentObjects) {
        int objectsToUpdate = Math.min(currentObjects.size(), OBJECT_PRELOAD_UPDATE_STRIDE);

        for (int i = 0; i < objectsToUpdate; ++i) {
            if (objectUpdateIndex >= currentObjects.size()) {
                objectUpdateIndex = 0;
            }
            VxBody obj = currentObjects.get(objectUpdateIndex++);

            int dataIndex = obj.getBodyHandle().getDataStoreIndex();
            if (dataIndex == -1 || obj.getBodyHandle().getBodyId() == 0) {
                removeObjectTracking(obj.getPhysicsId());
                continue;
            }

            int cooldown = objectUpdateCooldowns.getOrDefault(obj.getPhysicsId(), 0);
            float velSq = objectDataStore.velX[dataIndex] * objectDataStore.velX[dataIndex] +
                    objectDataStore.velY[dataIndex] * objectDataStore.velY[dataIndex] +
                    objectDataStore.velZ[dataIndex] * objectDataStore.velZ[dataIndex];

            if (cooldown > 0 && velSq < MAX_SPEED_FOR_COOLDOWN_SQR) {
                objectUpdateCooldowns.put(obj.getPhysicsId(), cooldown - 1);
            } else {
                objectUpdateCooldowns.put(obj.getPhysicsId(), UPDATE_INTERVAL_TICKS);
                processPreloadForObject(obj, dataIndex);
            }
        }
    }

    /**
     * Updates the set of active chunks for all tracked objects, reading directly from the data store.
     */
    private void updateActivation(List<VxBody> currentObjects) {
        Set<VxSectionPos> activeSet = new HashSet<>();

        for (VxBody obj : currentObjects) {
            int dataIndex = obj.getBodyHandle().getDataStoreIndex();
            if (dataIndex != -1) {
                calculateRequiredChunks(
                        dataIndex,
                        ACTIVATION_RADIUS_CHUNKS,
                        activeSet
                );
            }
        }

        activeSet.forEach(pos -> terrainManager.prioritizeChunk(pos, VxTaskPriority.CRITICAL));

        Set<VxSectionPos> currentlyActive = chunkDataStore.getActiveIndices().stream()
                .map(chunkDataStore::getPosForIndex)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (VxSectionPos pos : currentlyActive) {
            if (!activeSet.contains(pos)) {
                terrainManager.deactivateChunk(pos);
            }
        }

        for (VxSectionPos pos : activeSet) {
            terrainManager.activateChunk(pos);
        }
    }

    /**
     * Calculates the required chunks for a single preloaded object and updates its tracking info.
     * This method reads all necessary data directly from the VxObjectDataStore, avoiding Jolt locks.
     */
    private void processPreloadForObject(VxBody obj, int dataIndex) {
        UUID id = obj.getPhysicsId();
        Set<VxSectionPos> required = new HashSet<>();
        calculateRequiredChunks(dataIndex, PRELOAD_RADIUS_CHUNKS, required);

        Set<VxSectionPos> previouslyTracked = objectTrackedChunks.computeIfAbsent(id, k -> new HashSet<>());

        previouslyTracked.removeIf(pos -> {
            if (!required.contains(pos)) {
                terrainManager.releaseChunk(pos);
                return true;
            }
            return false;
        });

        for (VxSectionPos pos : required) {
            if (previouslyTracked.add(pos)) {
                terrainManager.requestChunk(pos);
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
     * Clears all tracking data. Used during shutdown.
     */
    public void clear() {
        objectTrackedChunks.clear();
        objectUpdateCooldowns.clear();
    }

    private void calculateRequiredChunks(int dataIndex, int radius, Set<VxSectionPos> outChunks) {
        // Read AABB and velocity from data store
        float minX = objectDataStore.aabbMinX[dataIndex];
        float minY = objectDataStore.aabbMinY[dataIndex];
        float minZ = objectDataStore.aabbMinZ[dataIndex];
        float maxX = objectDataStore.aabbMaxX[dataIndex];
        float maxY = objectDataStore.aabbMaxY[dataIndex];
        float maxZ = objectDataStore.aabbMaxZ[dataIndex];
        float velX = objectDataStore.velX[dataIndex];
        float velY = objectDataStore.velY[dataIndex];
        float velZ = objectDataStore.velZ[dataIndex];

        // Use current bounds
        addChunksForBounds(minX, minY, minZ, maxX, maxY, maxZ, radius, outChunks);

        // Predict future bounds and add chunks for them as well
        float predMinX = minX + velX * PREDICTION_SECONDS;
        float predMinY = minY + velY * PREDICTION_SECONDS;
        float predMinZ = minZ + velZ * PREDICTION_SECONDS;
        float predMaxX = maxX + velX * PREDICTION_SECONDS;
        float predMaxY = maxY + velY * PREDICTION_SECONDS;
        float predMaxZ = maxZ + velZ * PREDICTION_SECONDS;

        addChunksForBounds(predMinX, predMinY, predMinZ, predMaxX, predMaxY, predMaxZ, radius, outChunks);
    }

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

    private static <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        int numBatches = (int) Math.ceil((double) list.size() / (double) batchSize);
        return IntStream.range(0, numBatches)
                .mapToObj(i -> list.subList(i * batchSize, Math.min((i + 1) * batchSize, list.size())))
                .collect(Collectors.toList());
    }
}