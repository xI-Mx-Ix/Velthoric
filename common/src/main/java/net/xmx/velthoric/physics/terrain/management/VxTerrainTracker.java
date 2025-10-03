/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.management;

import com.github.stephengold.joltjni.readonly.ConstAaBox;
import com.github.stephengold.joltjni.readonly.ConstBody;
import net.xmx.velthoric.physics.object.manager.VxObjectDataStore;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.terrain.data.VxChunkDataStore;
import net.xmx.velthoric.physics.terrain.data.VxSectionPos;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tracks physics objects and manages which terrain chunks should be loaded or active
 * based on their proximity, position, and velocity.
 *
 * @author xI-Mx-Ix
 */
public class VxTerrainTracker {

    private final VxPhysicsWorld physicsWorld;
    private final VxTerrainManager terrainManager;
    private final VxChunkDataStore chunkDataStore;
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

    public VxTerrainTracker(VxPhysicsWorld physicsWorld, VxTerrainManager terrainManager, VxChunkDataStore chunkDataStore) {
        this.physicsWorld = physicsWorld;
        this.terrainManager = terrainManager;
        this.chunkDataStore = chunkDataStore;
        this.objectDataStore = physicsWorld.getObjectManager().getDataStore();
    }

    /**
     * Main update method for the tracker. Periodically updates object tracking and determines
     * which chunks need to be active.
     */
    public void update() {
        List<VxBody> currentObjects = new ArrayList<>(physicsWorld.getObjectManager().getAllObjects());
        Set<UUID> currentObjectIds = currentObjects.stream().map(VxBody::getPhysicsId).collect(Collectors.toSet());

        // Clean up trackers for objects that no longer exist
        objectTrackedChunks.keySet().removeIf(id -> {
            if (!currentObjectIds.contains(id)) {
                removeObjectTracking(id);
                return true;
            }
            return false;
        });

        if (currentObjects.isEmpty()) {
            // If no objects, deactivate all chunks
            for (int index : chunkDataStore.getActiveIndices()) {
                if (chunkDataStore.states[index] == VxChunkDataStore.STATE_READY_ACTIVE) {
                    terrainManager.deactivateChunk(index);
                }
            }
            return;
        }

        // Update preloading for a subset of objects each tick
        updatePreloadForObjects(currentObjects);

        // Determine the set of chunks that must be active for all objects
        Set<VxSectionPos> requiredActiveSet = calculateRequiredActiveSet(currentObjects);

        // Deactivate chunks that are no longer needed
        for (int index : chunkDataStore.getActiveIndices()) {
            VxSectionPos pos = chunkDataStore.getPosForIndex(index);
            if (pos != null && chunkDataStore.states[index] == VxChunkDataStore.STATE_READY_ACTIVE && !requiredActiveSet.contains(pos)) {
                terrainManager.deactivateChunk(index);
            }
        }

        // Activate chunks that are now required
        for (VxSectionPos pos : requiredActiveSet) {
            Integer index = chunkDataStore.getIndexForPos(pos);
            if (index != null) {
                terrainManager.activateChunk(index);
            }
        }
    }

    /**
     * Calculates the complete set of chunks that need to be active for all tracked objects.
     *
     * @param allObjects A list of all current physics objects.
     * @return A set of {@link VxSectionPos} that should be active.
     */
    private Set<VxSectionPos> calculateRequiredActiveSet(List<VxBody> allObjects) {
        return allObjects.parallelStream()
                .map(obj -> {
                    Set<VxSectionPos> requiredForObj = new HashSet<>();
                    ConstBody body = obj.getBody();
                    if (body != null) {
                        ConstAaBox bounds = body.getWorldSpaceBounds();
                        calculateRequiredChunks(bounds.getMin().getX(), bounds.getMin().getY(), bounds.getMin().getZ(),
                                bounds.getMax().getX(), bounds.getMax().getY(), bounds.getMax().getZ(),
                                body.getLinearVelocity().getX(), body.getLinearVelocity().getY(), body.getLinearVelocity().getZ(),
                                ACTIVATION_RADIUS_CHUNKS, requiredForObj);
                    }
                    return requiredForObj;
                })
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    /**
     * Updates the preloaded chunks for a subset of objects to distribute the workload over time.
     *
     * @param currentObjects The list of all current physics objects.
     */
    private void updatePreloadForObjects(List<VxBody> currentObjects) {
        int objectsToUpdate = Math.min(currentObjects.size(), OBJECT_PRELOAD_UPDATE_STRIDE);
        for (int i = 0; i < objectsToUpdate; ++i) {
            if (objectUpdateIndex >= currentObjects.size()) {
                objectUpdateIndex = 0;
            }
            VxBody obj = currentObjects.get(objectUpdateIndex++);
            if (obj.getBodyId() != 0) {
                updatePreloadForObject(obj);
            } else {
                removeObjectTracking(obj.getPhysicsId());
            }
        }
    }

    /**
     * Updates the set of preloaded (but not necessarily active) chunks for a single object.
     *
     * @param obj The physics object to update.
     */
    private void updatePreloadForObject(VxBody obj) {
        UUID id = obj.getPhysicsId();
        int dataIndex = obj.getDataStoreIndex();
        if (dataIndex == -1) {
            removeObjectTracking(id);
            return;
        }

        int cooldown = objectUpdateCooldowns.getOrDefault(id, 0);
        float velX = objectDataStore.velX[dataIndex];
        float velY = objectDataStore.velY[dataIndex];
        float velZ = objectDataStore.velZ[dataIndex];
        float velSq = velX * velX + velY * velY + velZ * velZ;

        if (cooldown > 0 && velSq < MAX_SPEED_FOR_COOLDOWN_SQR) {
            objectUpdateCooldowns.put(id, cooldown - 1);
            return;
        }
        objectUpdateCooldowns.put(id, UPDATE_INTERVAL_TICKS);

        Set<VxSectionPos> required = new HashSet<>();
        ConstBody body = obj.getBody();
        if (body != null) {
            ConstAaBox bounds = body.getWorldSpaceBounds();
            calculateRequiredChunks(bounds.getMin().getX(), bounds.getMin().getY(), bounds.getMin().getZ(),
                    bounds.getMax().getX(), bounds.getMax().getY(), bounds.getMax().getZ(),
                    velX, velY, velZ, PRELOAD_RADIUS_CHUNKS, required);
        } else {
            removeObjectTracking(id);
            return;
        }

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
     * Removes all tracking information for a given object ID and releases its chunks.
     *
     * @param id The UUID of the object.
     */
    public void removeObjectTracking(UUID id) {
        Set<VxSectionPos> chunksToRelease = objectTrackedChunks.remove(id);
        if (chunksToRelease != null) {
            chunksToRelease.forEach(terrainManager::releaseChunk);
        }
        objectUpdateCooldowns.remove(id);
    }

    /**
     * Calculates the required chunk sections for a given AABB, velocity, and radius.
     *
     * @param minX   Minimum X coordinate of the AABB.
     * @param minY   Minimum Y coordinate of the AABB.
     * @param minZ   Minimum Z coordinate of the AABB.
     * @param maxX   Maximum X coordinate of the AABB.
     * @param maxY   Maximum Y coordinate of the AABB.
     * @param maxZ   Maximum Z coordinate of the AABB.
     * @param velX   Velocity on the X axis.
     * @param velY   Velocity on the Y axis.
     * @param velZ   Velocity on the Z axis.
     * @param radius The radius in chunks to load around the AABB.
     * @param outChunks The set to which the required chunk positions will be added.
     */
    private void calculateRequiredChunks(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                         float velX, float velY, float velZ, int radius, Set<VxSectionPos> outChunks) {
        // Add chunks for current position
        addChunksForBounds(minX, minY, minZ, maxX, maxY, maxZ, radius, outChunks);

        // Add chunks for predicted future position
        double predMinX = minX + velX * PREDICTION_SECONDS;
        double predMinY = minY + velY * PREDICTION_SECONDS;
        double predMinZ = minZ + velZ * PREDICTION_SECONDS;
        double predMaxX = maxX + velX * PREDICTION_SECONDS;
        double predMaxY = maxY + velY * PREDICTION_SECONDS;
        double predMaxZ = maxZ + velZ * PREDICTION_SECONDS;
        addChunksForBounds(predMinX, predMinY, predMinZ, predMaxX, predMaxY, predMaxZ, radius, outChunks);
    }

    /**
     * Adds all chunk sections that overlap with a given bounding box and radius.
     *
     * @param minX           Minimum X coordinate.
     * @param minY           Minimum Y coordinate.
     * @param minZ           Minimum Z coordinate.
     * @param maxX           Maximum X coordinate.
     * @param maxY           Maximum Y coordinate.
     * @param maxZ           Maximum Z coordinate.
     * @param radiusInChunks The radius in chunks.
     * @param outChunks      The output set of chunk positions.
     */
    private void addChunksForBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int radiusInChunks, Set<VxSectionPos> outChunks) {
        int minSectionX = ((int) Math.floor(minX) >> 4) - radiusInChunks;
        int minSectionY = ((int) Math.floor(minY) >> 4) - radiusInChunks;
        int minSectionZ = ((int) Math.floor(minZ) >> 4) - radiusInChunks;
        int maxSectionX = ((int) Math.floor(maxX) >> 4) + radiusInChunks;
        int maxSectionY = ((int) Math.floor(maxY) >> 4) + radiusInChunks;
        int maxSectionZ = ((int) Math.floor(maxZ) >> 4) + radiusInChunks;

        final int worldMinY = physicsWorld.getLevel().getMinBuildHeight() >> 4;
        final int worldMaxY = physicsWorld.getLevel().getMaxBuildHeight() >> 4;

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
     * Clears all tracking data.
     */
    public void clear() {
        objectTrackedChunks.clear();
        objectUpdateCooldowns.clear();
    }
}