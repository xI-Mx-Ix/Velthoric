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
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.terrain.VxChunkDataStore;
import net.xmx.velthoric.physics.terrain.chunk.VxSectionPos;
import net.xmx.velthoric.physics.terrain.job.VxTerrainJobSystem;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Tracks physics objects in the world to determine which terrain chunks should be
 * loaded, preloaded, and activated.
 * <p>
 * This system calculates the required chunk set based on the current and predicted
 * positions of dynamic bodies, and then interfaces with the {@link VxChunkManager}
 * to request, release, and prioritize those chunks.
 *
 * @author xI-Mx-Ix
 */
public class VxObjectTracker {

    private final VxPhysicsWorld physicsWorld;
    private final ServerLevel level;
    private final VxChunkManager chunkManager;
    private final VxChunkDataStore chunkDataStore;
    private final VxTerrainJobSystem jobSystem;

    private final Map<UUID, Set<VxSectionPos>> objectTrackedChunks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> objectUpdateCooldowns = new ConcurrentHashMap<>();

    private int objectUpdateIndex = 0;
    private static final int OBJECT_PRELOAD_UPDATE_STRIDE = 250;
    private static final int OBJECT_ACTIVATION_BATCH_SIZE = 100;
    private static final int UPDATE_INTERVAL_TICKS = 10;
    private static final int PRELOAD_RADIUS_CHUNKS = 3;
    private static final int ACTIVATION_RADIUS_CHUNKS = 1;
    private static final float PREDICTION_SECONDS = 0.5f;

    public VxObjectTracker(VxPhysicsWorld physicsWorld, ServerLevel level, VxChunkManager chunkManager, VxChunkDataStore chunkDataStore, VxTerrainJobSystem jobSystem) {
        this.physicsWorld = physicsWorld;
        this.level = level;
        this.chunkManager = chunkManager;
        this.chunkDataStore = chunkDataStore;
        this.jobSystem = jobSystem;
    }

    /**
     * Main update method, called periodically by the terrain system's worker thread.
     * It updates chunk requirements based on object positions.
     */
    public void update() {
        if (jobSystem.isShutdown()) {
            return;
        }

        List<VxBody> currentObjects = new ArrayList<>(physicsWorld.getObjectManager().getAllObjects());
        cleanupStaleTrackers(currentObjects);

        if (currentObjects.isEmpty()) {
            deactivateAllChunks();
            return;
        }

        updatePreloadedChunks(currentObjects);
        updateActiveChunks(currentObjects);
    }

    /**
     * Removes trackers for objects that no longer exist in the physics world.
     */
    private void cleanupStaleTrackers(List<VxBody> currentObjects) {
        Set<UUID> currentObjectIds = currentObjects.stream().map(VxBody::getPhysicsId).collect(Collectors.toSet());
        objectTrackedChunks.keySet().removeIf(id -> {
            if (!currentObjectIds.contains(id)) {
                removeObjectTracking(id);
                return true;
            }
            return false;
        });
    }

    /**
     * Deactivates all currently active terrain chunks.
     */
    private void deactivateAllChunks() {
        for (int index : chunkDataStore.getActiveIndices()) {
            if (chunkDataStore.states[index] == VxChunkManager.STATE_READY_ACTIVE) {
                chunkManager.deactivateChunk(index);
            }
        }
    }

    /**
     * Updates the set of preloaded chunks for a subset of objects.
     */
    private void updatePreloadedChunks(List<VxBody> currentObjects) {
        int objectsToUpdate = Math.min(currentObjects.size(), OBJECT_PRELOAD_UPDATE_STRIDE);
        Map<Integer, VxBody> objectsToPreload = new HashMap<>();

        for (int i = 0; i < objectsToUpdate; ++i) {
            if (objectUpdateIndex >= currentObjects.size()) {
                objectUpdateIndex = 0;
            }
            VxBody obj = currentObjects.get(objectUpdateIndex++);
            if (obj.getDataStoreIndex() == -1 || obj.getBodyId() == 0) continue;

            int cooldown = objectUpdateCooldowns.getOrDefault(obj.getPhysicsId(), 0);
            if (cooldown > 0) {
                objectUpdateCooldowns.put(obj.getPhysicsId(), cooldown - 1);
            } else {
                objectsToPreload.put(obj.getBodyId(), obj);
            }
        }

        if (!objectsToPreload.isEmpty()) {
            int[] preloadBodyIds = objectsToPreload.keySet().stream().mapToInt(Integer::intValue).toArray();
            try (BodyLockMultiRead lock = new BodyLockMultiRead(physicsWorld.getPhysicsSystem().getBodyLockInterfaceNoLock(), preloadBodyIds)) {
                for (int i = 0; i < preloadBodyIds.length; i++) {
                    VxBody obj = objectsToPreload.get(preloadBodyIds[i]);
                    objectUpdateCooldowns.put(obj.getPhysicsId(), UPDATE_INTERVAL_TICKS);
                    processPreloadForLockedBody(obj, lock.getBody(i));
                }
            }
        }
    }

    /**
     * Updates the set of active chunks based on the positions of all objects.
     */
    private void updateActiveChunks(List<VxBody> currentObjects) {
        List<List<VxBody>> batches = partitionList(currentObjects, OBJECT_ACTIVATION_BATCH_SIZE);
        List<CompletableFuture<Set<VxSectionPos>>> futures = new ArrayList<>();

        for (List<VxBody> batch : batches) {
            futures.add(CompletableFuture.supplyAsync(() -> calculateActivationSetForBatch(batch), jobSystem.getExecutor()));
        }

        Set<VxSectionPos> activeSet = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        // Activate required chunks and deactivate unneeded ones
        activeSet.forEach(pos -> {
            Integer index = chunkDataStore.getIndexForPos(pos);
            if (index != null) {
                chunkManager.activateChunk(pos, index);
            }
        });

        chunkDataStore.getActiveIndices().forEach(index -> {
            VxSectionPos pos = chunkDataStore.getPosForIndex(index);
            if (pos != null && chunkDataStore.states[index] == VxChunkManager.STATE_READY_ACTIVE && !activeSet.contains(pos)) {
                chunkManager.deactivateChunk(index);
            }
        });
    }

    private Set<VxSectionPos> calculateActivationSetForBatch(List<VxBody> batch) {
        Set<VxSectionPos> required = new HashSet<>();
        if (batch.isEmpty()) return required;
        int[] batchBodyIds = batch.stream().mapToInt(VxBody::getBodyId).filter(id -> id != 0).toArray();
        if (batchBodyIds.length == 0) return required;

        try (BodyLockMultiRead batchLock = new BodyLockMultiRead(physicsWorld.getPhysicsSystem().getBodyLockInterfaceNoLock(), batchBodyIds)) {
            for (int i = 0; i < batchBodyIds.length; i++) {
                ConstBody body = batchLock.getBody(i);
                if (body != null) {
                    ConstAaBox bounds = body.getWorldSpaceBounds();
                    calculateRequiredChunks(bounds.getMin(), bounds.getMax(), body.getLinearVelocity(), ACTIVATION_RADIUS_CHUNKS, required);
                }
            }
        }
        return required;
    }

    private void processPreloadForLockedBody(VxBody obj, ConstBody body) {
        UUID id = obj.getPhysicsId();
        if (body == null) {
            removeObjectTracking(id);
            return;
        }

        Set<VxSectionPos> required = new HashSet<>();
        ConstAaBox bounds = body.getWorldSpaceBounds();
        calculateRequiredChunks(bounds.getMin(), bounds.getMax(), body.getLinearVelocity(), PRELOAD_RADIUS_CHUNKS, required);

        Set<VxSectionPos> previouslyTracked = objectTrackedChunks.computeIfAbsent(id, k -> new HashSet<>());
        previouslyTracked.removeIf(pos -> {
            if (!required.contains(pos)) {
                chunkManager.releaseChunk(pos);
                return true;
            }
            return false;
        });
        required.forEach(pos -> {
            if (previouslyTracked.add(pos)) {
                chunkManager.requestChunk(pos);
            }
        });
    }

    private void removeObjectTracking(UUID id) {
        Set<VxSectionPos> chunksToRelease = objectTrackedChunks.remove(id);
        if (chunksToRelease != null) {
            chunksToRelease.forEach(chunkManager::releaseChunk);
        }
        objectUpdateCooldowns.remove(id);
    }

    private void calculateRequiredChunks(Vec3 min, Vec3 max, Vec3 velocity, int radius, Set<VxSectionPos> outChunks) {
        addChunksForBounds(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ(), radius, outChunks);

        double predMinX = min.getX() + velocity.getX() * PREDICTION_SECONDS;
        double predMinY = min.getY() + velocity.getY() * PREDICTION_SECONDS;
        double predMinZ = min.getZ() + velocity.getZ() * PREDICTION_SECONDS;
        double predMaxX = max.getX() + velocity.getX() * PREDICTION_SECONDS;
        double predMaxY = max.getY() + velocity.getY() * PREDICTION_SECONDS;
        double predMaxZ = max.getZ() + velocity.getZ() * PREDICTION_SECONDS;

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
        if (list.isEmpty()) return Collections.emptyList();
        int numBatches = (list.size() + batchSize - 1) / batchSize;
        return IntStream.range(0, numBatches)
                .mapToObj(i -> list.subList(i * batchSize, Math.min((i + 1) * batchSize, list.size())))
                .collect(Collectors.toList());
    }
}