/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.management;

import com.github.stephengold.joltjni.readonly.ConstAaBox;
import com.github.stephengold.joltjni.readonly.ConstBody;
import net.xmx.velthoric.physics.object.manager.VxObjectDataStore;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.terrain.VxUpdateContext;
import net.xmx.velthoric.physics.terrain.data.VxChunkDataStore;
import net.xmx.velthoric.physics.terrain.data.VxSectionPos;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tracks physics objects and manages which terrain chunks should be loaded or active.
 * This implementation is heavily optimized for performance and low garbage collection
 * overhead. It uses packed longs for positions and reuses collections via a
 * thread-local context to avoid allocations in its main update loop.
 *
 * @author xI-Mx-Ix
 */
public class VxTerrainTracker {

    private final VxPhysicsWorld physicsWorld;
    private final VxTerrainManager terrainManager;
    private final VxChunkDataStore chunkDataStore;
    private final VxObjectDataStore objectDataStore;

    private final Map<UUID, Set<Long>> objectTrackedChunks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> objectUpdateCooldowns = new ConcurrentHashMap<>();

    private static final int UPDATE_INTERVAL_TICKS = 10;
    private static final float MAX_SPEED_FOR_COOLDOWN_SQR = 100f * 100f;
    private static final int PRELOAD_RADIUS_CHUNKS = 3;
    private static final int ACTIVATION_RADIUS_CHUNKS = 1;
    private static final float PREDICTION_SECONDS = 0.5f;

    private int objectUpdateIndex = 0;
    private static final int OBJECT_PRELOAD_UPDATE_STRIDE = 250;

    private static final ThreadLocal<VxUpdateContext> updateContext = ThreadLocal.withInitial(VxUpdateContext::new);

    public VxTerrainTracker(VxPhysicsWorld physicsWorld, VxTerrainManager terrainManager, VxChunkDataStore chunkDataStore) {
        this.physicsWorld = physicsWorld;
        this.terrainManager = terrainManager;
        this.chunkDataStore = chunkDataStore;
        this.objectDataStore = physicsWorld.getObjectManager().getDataStore();
    }

    public void update() {
        List<VxBody> currentObjects = new ArrayList<>(physicsWorld.getObjectManager().getAllObjects());
        if (currentObjects.isEmpty()) {
            deactivateAllChunks();
            return;
        }

        Set<UUID> currentObjectIds = currentObjects.stream().map(VxBody::getPhysicsId).collect(Collectors.toSet());
        cleanupRemovedObjects(currentObjectIds);

        updatePreloadForObjects(currentObjects);

        VxUpdateContext ctx = updateContext.get();
        Set<Long> requiredActiveSet = ctx.requiredChunksSet;
        requiredActiveSet.clear();
        calculateRequiredActiveSet(currentObjects, requiredActiveSet);

        updateActiveChunks(requiredActiveSet);
    }

    private void deactivateAllChunks() {
        for (int index : chunkDataStore.getActiveIndices()) {
            if (chunkDataStore.states[index] == VxChunkDataStore.STATE_READY_ACTIVE) {
                terrainManager.deactivateChunk(index);
            }
        }
    }

    private void cleanupRemovedObjects(Set<UUID> currentObjectIds) {
        objectTrackedChunks.keySet().removeIf(id -> {
            if (!currentObjectIds.contains(id)) {
                removeObjectTracking(id);
                return true;
            }
            return false;
        });
    }

    private void updateActiveChunks(Set<Long> requiredActiveSet) {
        for (int index : chunkDataStore.getActiveIndices()) {
            long pos = chunkDataStore.getPosForIndex(index);
            if (pos != 0L && chunkDataStore.states[index] == VxChunkDataStore.STATE_READY_ACTIVE && !requiredActiveSet.contains(pos)) {
                terrainManager.deactivateChunk(index);
            }
        }

        for (long pos : requiredActiveSet) {
            Integer index = chunkDataStore.getIndexForPos(pos);
            if (index != null) {
                terrainManager.activateChunk(index);
            }
        }
    }

    /**
     * Calculates the set of chunks that need to be active for all objects.
     * This method avoids object allocations by using a pre-existing Set.
     *
     * @param allObjects A list of all current physics objects.
     * @param outRequiredActiveSet The set to populate with required chunk positions.
     */
    private void calculateRequiredActiveSet(List<VxBody> allObjects, Set<Long> outRequiredActiveSet) {
        for (VxBody obj : allObjects) {
            ConstBody body = obj.getConstBody();
            if (body != null) {
                ConstAaBox bounds = body.getWorldSpaceBounds();
                calculateRequiredChunks(bounds.getMin().getX(), bounds.getMin().getY(), bounds.getMin().getZ(),
                        bounds.getMax().getX(), bounds.getMax().getY(), bounds.getMax().getZ(),
                        body.getLinearVelocity().getX(), body.getLinearVelocity().getY(), body.getLinearVelocity().getZ(),
                        ACTIVATION_RADIUS_CHUNKS, outRequiredActiveSet);
            }
        }
    }

    private void updatePreloadForObjects(List<VxBody> currentObjects) {
        int objectsToUpdate = Math.min(currentObjects.size(), OBJECT_PRELOAD_UPDATE_STRIDE);
        for (int i = 0; i < objectsToUpdate; ++i) {
            if (objectUpdateIndex >= currentObjects.size()) {
                objectUpdateIndex = 0;
            }
            VxBody obj = currentObjects.get(objectUpdateIndex++);
            if (obj.getInternalBody().getBodyId() != 0) {
                updatePreloadForObject(obj);
            } else {
                removeObjectTracking(obj.getPhysicsId());
            }
        }
    }

    private void updatePreloadForObject(VxBody obj) {
        UUID id = obj.getPhysicsId();
        int dataIndex = obj.getInternalBody().getDataStoreIndex();
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

        ConstBody body = obj.getConstBody();
        if (body == null) {
            removeObjectTracking(id);
            return;
        }

        VxUpdateContext ctx = updateContext.get();
        Set<Long> required = ctx.requiredChunksSet;
        required.clear();

        ConstAaBox bounds = body.getWorldSpaceBounds();
        calculateRequiredChunks(bounds.getMin().getX(), bounds.getMin().getY(), bounds.getMin().getZ(),
                bounds.getMax().getX(), bounds.getMax().getY(), bounds.getMax().getZ(),
                velX, velY, velZ, PRELOAD_RADIUS_CHUNKS, required);

        Set<Long> previouslyTracked = objectTrackedChunks.computeIfAbsent(id, k -> new HashSet<>());

        Set<Long> toAdd = ctx.toAddSet;
        toAdd.clear();
        toAdd.addAll(required);
        toAdd.removeAll(previouslyTracked);

        Set<Long> toRemove = ctx.toRemoveSet;
        toRemove.clear();
        toRemove.addAll(previouslyTracked);
        toRemove.removeAll(required);

        toRemove.forEach(pos -> {
            terrainManager.releaseChunk(pos);
            previouslyTracked.remove(pos);
        });

        toAdd.forEach(pos -> {
            terrainManager.requestChunk(pos);
            previouslyTracked.add(pos);
        });
    }

    public void removeObjectTracking(UUID id) {
        Set<Long> chunksToRelease = objectTrackedChunks.remove(id);
        if (chunksToRelease != null) {
            chunksToRelease.forEach(terrainManager::releaseChunk);
        }
        objectUpdateCooldowns.remove(id);
    }

    private void calculateRequiredChunks(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                         float velX, float velY, float velZ, int radius, Set<Long> outChunks) {
        double predMinX = minX + velX * PREDICTION_SECONDS;
        double predMinY = minY + velY * PREDICTION_SECONDS;
        double predMinZ = minZ + velZ * PREDICTION_SECONDS;
        double predMaxX = maxX + velX * PREDICTION_SECONDS;
        double predMaxY = maxY + velY * PREDICTION_SECONDS;
        double predMaxZ = maxZ + velZ * PREDICTION_SECONDS;

        double combinedMinX = Math.min(minX, predMinX);
        double combinedMinY = Math.min(minY, predMinY);
        double combinedMinZ = Math.min(minZ, predMinZ);
        double combinedMaxX = Math.max(maxX, predMaxX);
        double combinedMaxY = Math.max(maxY, predMaxY);
        double combinedMaxZ = Math.max(maxZ, predMaxZ);

        addChunksForBounds(combinedMinX, combinedMinY, combinedMinZ, combinedMaxX, combinedMaxY, combinedMaxZ, radius, outChunks);
    }

    private void addChunksForBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int radiusInChunks, Set<Long> outChunks) {
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
                    outChunks.add(VxSectionPos.pack(x, y, z));
                }
            }
        }
    }

    public void clear() {
        objectTrackedChunks.clear();
        objectUpdateCooldowns.clear();
    }
}