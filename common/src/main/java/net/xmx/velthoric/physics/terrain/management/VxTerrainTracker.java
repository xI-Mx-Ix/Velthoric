/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.management;

import com.github.stephengold.joltjni.BodyLockMultiRead;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
import com.github.stephengold.joltjni.readonly.ConstBody;
import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.physics.object.manager.VxObjectDataStore;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.object.type.internal.VxBodyHandle;
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
 * based on object positions and velocities.
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
    private static final int OBJECT_ACTIVATION_BATCH_SIZE = 100;

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
     * Updates the preloaded chunks for a subset of tracked objects.
     */
    private void updatePreloading(List<VxBody> currentObjects) {
        int objectsToUpdate = Math.min(currentObjects.size(), OBJECT_PRELOAD_UPDATE_STRIDE);
        Map<Integer, VxBody> objectsToPreload = new HashMap<>();

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
                objectsToPreload.put(obj.getBodyHandle().getBodyId(), obj);
            }
        }

        if (!objectsToPreload.isEmpty()) {
            int[] preloadBodyIds = objectsToPreload.keySet().stream().mapToInt(Integer::intValue).toArray();
            BodyLockMultiRead lock = new BodyLockMultiRead(physicsWorld.getPhysicsSystem().getBodyLockInterfaceNoLock(), preloadBodyIds);
            try {
                for (int i = 0; i < preloadBodyIds.length; i++) {
                    int bodyId = preloadBodyIds[i];
                    ConstBody body = lock.getBody(i);
                    VxBody obj = objectsToPreload.get(bodyId);
                    objectUpdateCooldowns.put(obj.getPhysicsId(), UPDATE_INTERVAL_TICKS);
                    processPreloadForLockedBody(obj, body);
                }
            } finally {
                lock.releaseLocks();
            }
        }
    }

    /**
     * Updates the set of active chunks for all tracked objects.
     */
    private void updateActivation(List<VxBody> currentObjects) {
        List<List<VxBody>> batches = partitionList(currentObjects, OBJECT_ACTIVATION_BATCH_SIZE);
        Set<VxSectionPos> activeSet = new HashSet<>();

        for (List<VxBody> batch : batches) {
            if (batch.isEmpty()) continue;
            int[] batchBodyIds = batch.stream()
                .map(VxBody::getBodyHandle)
                .mapToInt(VxBodyHandle::getBodyId)
                .filter(id -> id != 0)
                .toArray();

            if (batchBodyIds.length == 0) continue;

            BodyLockMultiRead batchLock = new BodyLockMultiRead(physicsWorld.getPhysicsSystem().getBodyLockInterfaceNoLock(), batchBodyIds);
            try {
                for (int i = 0; i < batchBodyIds.length; i++) {
                    ConstBody body = batchLock.getBody(i);
                    if (body != null) {
                        ConstAaBox bounds = body.getWorldSpaceBounds();
                        calculateRequiredChunks(bounds.getMin(), bounds.getMax(), body.getLinearVelocity(), ACTIVATION_RADIUS_CHUNKS, activeSet);
                    }
                }
            } finally {
                batchLock.releaseLocks();
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
     */
    private void processPreloadForLockedBody(VxBody obj, ConstBody body) {
        UUID id = obj.getPhysicsId();
        if (body == null) {
            removeObjectTracking(id);
            return;
        }

        int dataIndex = obj.getBodyHandle().getDataStoreIndex();
        float velX = objectDataStore.velX[dataIndex];
        float velY = objectDataStore.velY[dataIndex];
        float velZ = objectDataStore.velZ[dataIndex];

        Set<VxSectionPos> required = new HashSet<>();
        ConstAaBox bounds = body.getWorldSpaceBounds();
        calculateRequiredChunks(bounds.getMin(), bounds.getMax(), velX, velY, velZ, PRELOAD_RADIUS_CHUNKS, required);

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

    private void calculateRequiredChunks(Vec3 min, Vec3 max, Vec3 velocity, int radius, Set<VxSectionPos> outChunks) {
        calculateRequiredChunks(min, max, velocity.getX(), velocity.getY(), velocity.getZ(), radius, outChunks);
    }

    private void calculateRequiredChunks(Vec3 min, Vec3 max, float velX, float velY, float velZ, int radius, Set<VxSectionPos> outChunks) {
        addChunksForBounds(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ(), radius, outChunks);

        double predMinX = min.getX() + velX * PREDICTION_SECONDS;
        double predMinY = min.getY() + velY * PREDICTION_SECONDS;
        double predMinZ = min.getZ() + velZ * PREDICTION_SECONDS;
        double predMaxX = max.getX() + velX * PREDICTION_SECONDS;
        double predMaxY = max.getY() + velY * PREDICTION_SECONDS;
        double predMaxZ = max.getZ() + velZ * PREDICTION_SECONDS;

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