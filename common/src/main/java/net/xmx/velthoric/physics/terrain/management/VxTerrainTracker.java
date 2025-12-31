/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.management;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.body.manager.VxServerBodyDataStore;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.terrain.VxSectionPos;
import net.xmx.velthoric.physics.terrain.storage.VxChunkDataStore;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks physics bodies using a high-performance, grid-based dynamic clustering approach.
 * <p>
 * This system groups nearby bodies into clusters to minimize the overhead of terrain
 * calculations. It calculates the union of chunks required for collision based on
 * body positions, bounding boxes, and predicted velocities.
 * <p>
 * It includes safety mechanisms to prevent excessive chunk loading caused by
 * extreme velocities or floating-point errors.
 *
 * @author xI-Mx-Ix
 */
public final class VxTerrainTracker {

    private final VxPhysicsWorld physicsWorld;
    private final VxTerrainManager terrainManager;
    private final VxChunkDataStore chunkDataStore;
    private final ServerLevel level;
    private final VxServerBodyDataStore bodyDataStore;

    private Set<VxSectionPos> previouslyRequiredChunks = new HashSet<>();

    // --- Configuration Constants ---

    /**
     * Defines the size of the coarse grid cells used for clustering, in chunks.
     */
    private static final int GRID_CELL_SIZE_IN_CHUNKS = 4;

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
     * The maximum number of chunks a single cluster can request in one update tick.
     * This acts as a safety brake against physics glitches or infinite bounding boxes.
     */
    private static final int MAX_CHUNKS_PER_CLUSTER_ITERATION = 4096;

    /**
     * The maximum Y-level at which terrain generation is tracked.
     * Bodies above this height will not trigger terrain loading.
     */
    private static final int MAX_GENERATION_HEIGHT = 500;

    /**
     * The minimum Y-level at which terrain generation is tracked.
     * Bodies below this height will not trigger terrain loading.
     */
    private static final int MIN_GENERATION_HEIGHT = -250;

    /**
     * Constructs a new VxTerrainTracker.
     *
     * @param physicsWorld   The physics world containing the bodies to track.
     * @param terrainManager The manager responsible for loading and unloading terrain.
     * @param chunkDataStore The data store for chunk state information.
     * @param level          The server level in which the tracking occurs.
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

        // 2. Request new chunks.
        for (VxSectionPos pos : currentlyRequiredChunks) {
            if (!previouslyRequiredChunks.contains(pos)) {
                terrainManager.requestChunk(pos);
            }
        }

        // 3. Release old chunks.
        for (VxSectionPos pos : previouslyRequiredChunks) {
            if (!currentlyRequiredChunks.contains(pos)) {
                terrainManager.releaseChunk(pos);
            }
        }

        // 4. Update the state for the next tick.
        this.previouslyRequiredChunks = currentlyRequiredChunks;

        // 5. Handle fine-grained activation for bodies that are actually moving.
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
        Map<Long, List<VxBody>> bodyClusters = new HashMap<>();

        for (VxBody body : allBodies) {
            int i = body.getDataStoreIndex();
            if (i == -1) continue;

            float px = (float) bodyDataStore.posX[i];
            float py = (float) bodyDataStore.posY[i];
            float pz = (float) bodyDataStore.posZ[i];

            // Basic sanity check for valid coordinates
            if (!Float.isFinite(px) || !Float.isFinite(py) || !Float.isFinite(pz)) {
                continue;
            }

            // Height check: Ignore bodies too high or too low
            if (py > MAX_GENERATION_HEIGHT || py < MIN_GENERATION_HEIGHT) {
                continue;
            }

            VxSectionPos bodySectionPos = VxSectionPos.fromWorldSpace(px, py, pz);

            // Calculate the coarse grid cell coordinates
            int cellX = bodySectionPos.x() / GRID_CELL_SIZE_IN_CHUNKS;
            int cellY = bodySectionPos.y() / GRID_CELL_SIZE_IN_CHUNKS;
            int cellZ = bodySectionPos.z() / GRID_CELL_SIZE_IN_CHUNKS;

            long cellKey = SectionPos.asLong(cellX, cellY, cellZ);
            bodyClusters.computeIfAbsent(cellKey, k -> new ArrayList<>()).add(body);
        }

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
                int i = body.getDataStoreIndex();
                if (i == -1) continue;

                float bMinX = bodyDataStore.aabbMinX[i];
                float bMinY = bodyDataStore.aabbMinY[i];
                float bMinZ = bodyDataStore.aabbMinZ[i];
                float bMaxX = bodyDataStore.aabbMaxX[i];
                float bMaxY = bodyDataStore.aabbMaxY[i];
                float bMaxZ = bodyDataStore.aabbMaxZ[i];

                if (!Float.isFinite(bMinX) || !Float.isFinite(bMaxX) ||
                        !Float.isFinite(bMinY) || !Float.isFinite(bMaxY) ||
                        !Float.isFinite(bMinZ) || !Float.isFinite(bMaxZ)) {
                    continue;
                }

                clusterHasValidBodies = true;

                minX = Math.min(minX, bMinX);
                minY = Math.min(minY, bMinY);
                minZ = Math.min(minZ, bMinZ);
                maxX = Math.max(maxX, bMaxX);
                maxY = Math.max(maxY, bMaxY);
                maxZ = Math.max(maxZ, bMaxZ);

                // Velocity Prediction
                float velX = bodyDataStore.velX[i];
                float velY = bodyDataStore.velY[i];
                float velZ = bodyDataStore.velZ[i];

                if (Float.isFinite(velX) && Float.isFinite(velY) && Float.isFinite(velZ)) {
                    // Prediction logic without clamping
                    if (Math.abs(velX) > 0.01f || Math.abs(velY) > 0.01f || Math.abs(velZ) > 0.01f) {
                        minX = Math.min(minX, bMinX + velX * PREDICTION_SECONDS);
                        minY = Math.min(minY, bMinY + velY * PREDICTION_SECONDS);
                        minZ = Math.min(minZ, bMinZ + velZ * PREDICTION_SECONDS);
                        maxX = Math.max(maxX, bMaxX + velX * PREDICTION_SECONDS);
                        maxY = Math.max(maxY, bMaxY + velY * PREDICTION_SECONDS);
                        maxZ = Math.max(maxZ, bMaxZ + velZ * PREDICTION_SECONDS);
                    }
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
     * This logic activates chunks only around bodies that are currently in motion.
     *
     * @param allBodies The list of all physics bodies in the world.
     */
    private void updateChunkActivation(List<VxBody> allBodies) {
        Set<VxSectionPos> requiredActiveSet = new HashSet<>();

        for (VxBody body : allBodies) {
            int i = body.getDataStoreIndex();

            // Check active status
            if (i != -1 && bodyDataStore.isActive[i]) {
                float py = (float) bodyDataStore.posY[i];

                // Check height limits
                if (py > MAX_GENERATION_HEIGHT || py < MIN_GENERATION_HEIGHT) {
                    continue;
                }

                float minX = bodyDataStore.aabbMinX[i];
                float minY = bodyDataStore.aabbMinY[i];
                float minZ = bodyDataStore.aabbMinZ[i];
                float maxX = bodyDataStore.aabbMaxX[i];
                float maxY = bodyDataStore.aabbMaxY[i];
                float maxZ = bodyDataStore.aabbMaxZ[i];

                // Ensure bounds are valid before iterating
                if (Float.isFinite(minX) && Float.isFinite(maxX) &&
                        Float.isFinite(minY) && Float.isFinite(maxY) &&
                        Float.isFinite(minZ) && Float.isFinite(maxZ)) {
                    forEachSectionInBox(minX, minY, minZ, maxX, maxY, maxZ, ACTIVATION_RADIUS_CHUNKS, requiredActiveSet);
                }
            }
        }

        requiredActiveSet.forEach(pos -> terrainManager.prioritizeChunk(pos));

        Set<VxSectionPos> currentlyActive = chunkDataStore.getActiveIndices().stream()
                .filter(index -> chunkDataStore.getState(index) == VxTerrainManager.STATE_READY_ACTIVE)
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
     * Includes a safety brake to prevent processing excessively large areas due to physics glitches.
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
        if (Double.isNaN(minX) || Double.isNaN(maxX) || Double.isNaN(minZ) || Double.isNaN(maxZ)) return;

        int minSectionX = SectionPos.blockToSectionCoord(minX) - radiusInChunks;
        int minSectionY = SectionPos.blockToSectionCoord(minY) - radiusInChunks;
        int minSectionZ = SectionPos.blockToSectionCoord(minZ) - radiusInChunks;
        int maxSectionX = SectionPos.blockToSectionCoord(maxX) + radiusInChunks;
        int maxSectionY = SectionPos.blockToSectionCoord(maxY) + radiusInChunks;
        int maxSectionZ = SectionPos.blockToSectionCoord(maxZ) + radiusInChunks;

        final int worldMinY = level.getMinBuildHeight() >> 4;
        final int worldMaxY = level.getMaxBuildHeight() >> 4;

        // Calculate the volume of the requested area
        long width = (long) maxSectionX - minSectionX + 1;
        long height = (long) maxSectionY - minSectionY + 1;
        long depth = (long) maxSectionZ - minSectionZ + 1;

        if (width <= 0 || height <= 0 || depth <= 0) return;

        long totalVolume = width * height * depth;

        // Safety brake: Abort if the requested volume is unreasonably large (likely a glitch)
        if (totalVolume > MAX_CHUNKS_PER_CLUSTER_ITERATION) {
            VxMainClass.LOGGER.warn("Terrain Tracker Safety Brake triggered! Ignored request for {} chunks in one cluster. (Bounds: {},{} to {},{})",
                    totalVolume, minSectionX, minSectionZ, maxSectionX, maxSectionZ);
            return;
        }

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
     * Deactivates all currently managed terrain chunks.
     */
    private void deactivateAllChunks() {
        chunkDataStore.getActiveIndices().stream()
                .map(chunkDataStore::getPosForIndex)
                .filter(Objects::nonNull)
                .forEach(terrainManager::deactivateChunk);
    }

    /**
     * Clears all tracking data and releases all held chunks.
     */
    public void clear() {
        releaseAllChunks();
    }
}