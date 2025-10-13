/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.management;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.physics.body.manager.VxBodyDataStore;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.terrain.VxSectionPos;
import net.xmx.velthoric.physics.terrain.job.VxTaskPriority;
import net.xmx.velthoric.physics.terrain.storage.VxChunkDataStore;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks physics bodies using a high-performance, grid-based dynamic clustering approach.
 * This system is optimized to handle thousands of bodies, whether they are clustered together
 * or widely distributed across the world. It avoids the high CPU overhead of per-body tracking
 * and the high memory overhead of a single global bounding box by grouping nearby bodies into
 * clusters and managing terrain for each cluster.
 *
 * @author xI-Mx-Ix
 */
public final class VxTerrainTracker {

    private final VxPhysicsWorld physicsWorld;
    private final VxTerrainManager terrainManager;
    private final VxChunkDataStore chunkDataStore;
    private final ServerLevel level;
    private final VxBodyDataStore bodyDataStore;

    private Set<VxSectionPos> previouslyRequiredChunks = new HashSet<>();

    // --- Configuration Constants ---

    /**
     * Defines the size of the coarse grid cells used for clustering, in chunks.
     * A larger value groups more distant bodies, behaving more like a "single box" approach.
     * A smaller value creates more, smaller clusters, behaving more like per-body tracking.
     * A value of 16 (a 256x256 block area) provides a good balance.
     */
    private static final int GRID_CELL_SIZE_IN_CHUNKS = 16;

    /**
     * The radius, in chunks, around a moving body's bounding box to keep active.
     */
    private static final int ACTIVATION_RADIUS_CHUNKS = 1;

    /**
     * The radius, in chunks, to preload around a cluster's bounding box.
     */
    private static final int PRELOAD_RADIUS_CHUNKS = 3;

    /**
     * The time, in seconds, to predict a body's future position for preloading terrain.
     */
    private static final float PREDICTION_SECONDS = 0.5f;


    /**
     * Constructs a new VxTerrainTracker.
     *
     * @param physicsWorld The physics world containing the bodies to track.
     * @param terrainManager The manager responsible for loading and unloading terrain.
     * @param chunkDataStore The data store for chunk state information.
     * @param level The server level in which the tracking occurs.
     */
    public VxTerrainTracker(VxPhysicsWorld physicsWorld, VxTerrainManager terrainManager, VxChunkDataStore chunkDataStore, ServerLevel level) {
        this.physicsWorld = physicsWorld;
        this.terrainManager = terrainManager;
        this.chunkDataStore = chunkDataStore;
        this.level = level;
        this.bodyDataStore = physicsWorld.getBodyManager().getDataStore();
    }

    /**
     * Performs a single update tick. It dynamically clusters all bodies, calculates the
     * required terrain for each cluster, and manages chunk loading and activation based on
     * the current and previous state.
     */
    public void update() {
        List<VxBody> currentBodies = new ArrayList<>(physicsWorld.getBodyManager().getAllBodies());

        if (currentBodies.isEmpty()) {
            releaseAllChunks();
            deactivateAllChunks();
            return;
        }

        // 1. Calculate the total set of required chunks based on dynamic clustering.
        Set<VxSectionPos> currentlyRequiredChunks = calculateRequiredPreloadSet(currentBodies);

        // 2. Request new chunks and release old ones by comparing the current and previous sets.
        for (VxSectionPos pos : currentlyRequiredChunks) {
            if (!previouslyRequiredChunks.contains(pos)) {
                terrainManager.requestChunk(pos);
            }
        }
        for (VxSectionPos pos : previouslyRequiredChunks) {
            if (!currentlyRequiredChunks.contains(pos)) {
                terrainManager.releaseChunk(pos);
            }
        }

        // 3. Update the state for the next tick.
        this.previouslyRequiredChunks = currentlyRequiredChunks;

        // 4. Handle fine-grained activation for bodies that are actually moving.
        updateChunkActivation(currentBodies);
    }

    /**
     * Groups all physics bodies into grid-based clusters and calculates the union of
     * chunks required by each cluster, including a preloading radius and motion prediction.
     *
     * @param allBodies A list of all physics bodies currently in the world.
     * @return A set of {@link VxSectionPos} representing all chunks that should be loaded.
     */
    private Set<VxSectionPos> calculateRequiredPreloadSet(List<VxBody> allBodies) {
        // Step 1: Group bodies into clusters using a fast spatial hash (grid).
        Map<Long, List<VxBody>> bodyClusters = new HashMap<>();
        for (VxBody body : allBodies) {
            int dataIndex = body.getDataStoreIndex();
            if (dataIndex == -1) continue;

            // Get the body's position in chunk coordinates.
            VxSectionPos bodySectionPos = VxSectionPos.fromWorldSpace(
                    bodyDataStore.posX[dataIndex],
                    bodyDataStore.posY[dataIndex],
                    bodyDataStore.posZ[dataIndex]
            );

            // Calculate the coarse grid cell coordinates.
            int cellX = bodySectionPos.x() / GRID_CELL_SIZE_IN_CHUNKS;
            int cellY = bodySectionPos.y() / GRID_CELL_SIZE_IN_CHUNKS;
            int cellZ = bodySectionPos.z() / GRID_CELL_SIZE_IN_CHUNKS;

            // Use SectionPos.asLong to create a unique, stable key for the cell.
            long cellKey = SectionPos.asLong(cellX, cellY, cellZ);
            bodyClusters.computeIfAbsent(cellKey, k -> new ArrayList<>()).add(body);
        }

        // Step 2: For each cluster, calculate its bounding box and the chunks it needs.
        Set<VxSectionPos> requiredChunks = new HashSet<>();
        for (List<VxBody> cluster : bodyClusters.values()) {
            if (cluster.isEmpty()) continue;

            // Initialize AABB with inverted bounds to correctly encompass all valid bodies in the cluster.
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            boolean clusterHasValidBodies = false;

            // Expand the AABB to include all bodies in the cluster and their predictions.
            for (VxBody body : cluster) {
                int dataIndex = body.getDataStoreIndex();
                // Safely skip any body that may have been removed concurrently.
                if (dataIndex == -1) continue;

                clusterHasValidBodies = true;

                // Current bounds
                minX = Math.min(minX, bodyDataStore.aabbMinX[dataIndex]);
                minY = Math.min(minY, bodyDataStore.aabbMinY[dataIndex]);
                minZ = Math.min(minZ, bodyDataStore.aabbMinZ[dataIndex]);
                maxX = Math.max(maxX, bodyDataStore.aabbMaxX[dataIndex]);
                maxY = Math.max(maxY, bodyDataStore.aabbMaxY[dataIndex]);
                maxZ = Math.max(maxZ, bodyDataStore.aabbMaxZ[dataIndex]);

                // Predicted bounds
                float velX = bodyDataStore.velX[dataIndex];
                float velY = bodyDataStore.velY[dataIndex];
                float velZ = bodyDataStore.velZ[dataIndex];
                if (Math.abs(velX) > 0.01f || Math.abs(velY) > 0.01f || Math.abs(velZ) > 0.01f) {
                    float predMinX = bodyDataStore.aabbMinX[dataIndex] + velX * PREDICTION_SECONDS;
                    float predMinY = bodyDataStore.aabbMinY[dataIndex] + velY * PREDICTION_SECONDS;
                    float predMinZ = bodyDataStore.aabbMinZ[dataIndex] + velZ * PREDICTION_SECONDS;
                    float predMaxX = bodyDataStore.aabbMaxX[dataIndex] + velX * PREDICTION_SECONDS;
                    float predMaxY = bodyDataStore.aabbMaxY[dataIndex] + velY * PREDICTION_SECONDS;
                    float predMaxZ = bodyDataStore.aabbMaxZ[dataIndex] + velZ * PREDICTION_SECONDS;

                    minX = Math.min(minX, predMinX);
                    minY = Math.min(minY, predMinY);
                    minZ = Math.min(minZ, predMinZ);
                    maxX = Math.max(maxX, predMaxX);
                    maxY = Math.max(maxY, predMaxY);
                    maxZ = Math.max(maxZ, predMaxZ);
                }
            }

            // Only add chunks if the cluster contained at least one valid body.
            if (clusterHasValidBodies) {
                forEachSectionInBox(minX, minY, minZ, maxX, maxY, maxZ, PRELOAD_RADIUS_CHUNKS, requiredChunks);
            }
        }

        return requiredChunks;
    }

    /**
     * Updates the set of active terrain chunks for the current physics tick.
     * This logic is kept separate for performance, activating chunks only around bodies
     * that are currently in motion to ensure immediate collision data is available.
     *
     * @param allBodies The list of all physics bodies in the world.
     */
    private void updateChunkActivation(List<VxBody> allBodies) {
        Set<VxSectionPos> requiredActiveSet = new HashSet<>();

        for (VxBody body : allBodies) {
            int dataIndex = body.getDataStoreIndex();
            if (dataIndex != -1 && bodyDataStore.isActive[dataIndex]) {
                float minX = bodyDataStore.aabbMinX[dataIndex];
                float minY = bodyDataStore.aabbMinY[dataIndex];
                float minZ = bodyDataStore.aabbMinZ[dataIndex];
                float maxX = bodyDataStore.aabbMaxX[dataIndex];
                float maxY = bodyDataStore.aabbMaxY[dataIndex];
                float maxZ = bodyDataStore.aabbMaxZ[dataIndex];
                forEachSectionInBox(minX, minY, minZ, maxX, maxY, maxZ, ACTIVATION_RADIUS_CHUNKS, requiredActiveSet);
            }
        }

        requiredActiveSet.forEach(pos -> terrainManager.prioritizeChunk(pos, VxTaskPriority.CRITICAL));

        Set<VxSectionPos> currentlyActive = chunkDataStore.getActiveIndices().stream()
                .filter(index -> chunkDataStore.states[index] == 4 /* STATE_READY_ACTIVE */)
                .map(chunkDataStore::getPosForIndex)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (VxSectionPos pos : currentlyActive) {
            if (!requiredActiveSet.contains(pos)) {
                terrainManager.deactivateChunk(pos);
            }
        }

        for (VxSectionPos pos : requiredActiveSet) {
            if (!currentlyActive.contains(pos)) {
                terrainManager.activateChunk(pos);
            }
        }
    }

    /**
     * Helper method to iterate over all chunk sections that overlap a given AABB, expanded by a radius.
     *
     * @param minX The minimum X coordinate of the bounding box.
     * @param minY The minimum Y coordinate of the bounding box.
     * @param minZ The minimum Z coordinate of the bounding box.
     * @param maxX The maximum X coordinate of the bounding box.
     * @param maxY The maximum Y coordinate of the bounding box.
     * @param maxZ The maximum Z coordinate of the bounding box.
     * @param radiusInChunks The radius in chunks to expand the box by.
     * @param outChunks The set to which the overlapping chunk positions will be added.
     */
    private void forEachSectionInBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int radiusInChunks, Set<VxSectionPos> outChunks) {
        int minSectionX = SectionPos.blockToSectionCoord(minX) - radiusInChunks;
        int minSectionY = SectionPos.blockToSectionCoord(minY) - radiusInChunks;
        int minSectionZ = SectionPos.blockToSectionCoord(minZ) - radiusInChunks;
        int maxSectionX = SectionPos.blockToSectionCoord(maxX) + radiusInChunks;
        int maxSectionY = SectionPos.blockToSectionCoord(maxY) + radiusInChunks;
        int maxSectionZ = SectionPos.blockToSectionCoord(maxZ) + radiusInChunks;

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
     * Releases all chunks currently held by the tracker.
     */
    private void releaseAllChunks() {
        if (previouslyRequiredChunks.isEmpty()) return;
        for (VxSectionPos pos : previouslyRequiredChunks) {
            terrainManager.releaseChunk(pos);
        }
        previouslyRequiredChunks.clear();
    }

    /**
     * Deactivates all currently managed terrain chunks. This is typically used when no physics
     * bodies are present in the world.
     */
    private void deactivateAllChunks() {
        chunkDataStore.getActiveIndices().stream()
                .map(chunkDataStore::getPosForIndex)
                .filter(Objects::nonNull)
                .forEach(terrainManager::deactivateChunk);
    }

    /**
     * Clears all tracking data and releases all held chunks. Used during world shutdown or reloads.
     */
    public void clear() {
        releaseAllChunks();
    }
}