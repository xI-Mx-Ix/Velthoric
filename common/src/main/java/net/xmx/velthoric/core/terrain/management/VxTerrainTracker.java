/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.terrain.management;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.config.VxModConfig;
import net.xmx.velthoric.core.body.server.VxServerBodyDataStore;
import net.xmx.velthoric.core.body.type.VxBody;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.core.terrain.storage.VxChunkDataStore;
import net.xmx.velthoric.init.VxMainClass;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * Stores the set of required chunks from the previous tick to calculate differences.
     * Uses primitive long keys (bit-packed coordinates) to avoid object allocation.
     */
    private LongSet previouslyRequiredChunks = new LongOpenHashSet();

    // --- Configuration Constants ---

    /**
     * Defines the size of the coarse grid cells used for clustering, in chunks.
     */
    private final int GRID_CELL_SIZE_IN_CHUNKS;

    /**
     * The radius, in chunks, around a moving body's bounding box to keep active.
     */
    private final int ACTIVATION_RADIUS_CHUNKS;

    /**
     * The radius, in chunks, to preload around a cluster's bounding box.
     */
    private final int PRELOAD_RADIUS_CHUNKS;

    /**
     * The time, in seconds, to predict a body's future position for preloading terrain.
     */
    private final float PREDICTION_SECONDS;

    /**
     * The maximum distance, in blocks, that the system will look ahead based on velocity.
     * This prevents requesting thousands of chunks if a body glitches and gains excessive speed.
     */
    private final float MAX_PREDICTION_DISTANCE;

    /**
     * The maximum number of chunks a single cluster can request in one update tick.
     * This acts as a safety brake against physics glitches or infinite bounding boxes.
     */
    private final int MAX_CHUNKS_PER_CLUSTER_ITERATION;

    /**
     * The maximum Y-level at which terrain generation is tracked.
     * Bodies above this height will not trigger terrain loading.
     */
    private final int MAX_GENERATION_HEIGHT;

    /**
     * The minimum Y-level at which terrain generation is tracked.
     * Bodies below this height will not trigger terrain loading.
     */
    private final int MIN_GENERATION_HEIGHT;

    /**
     * Reusable map that groups bodies into grid-based clusters to avoid allocations.
     * Using fastutil Long2ObjectMap to avoid boxing cell keys.
     */
    private final Long2ObjectMap<ObjectArrayList<VxBody>> cachedBodyClusters = new Long2ObjectOpenHashMap<>();

    /**
     * Reusable cache for chunk sections required during the current update tick.
     * Uses bit-packed long coordinates to ensure zero allocation during scan loops.
     */
    private final LongSet requiredChunksCache = new LongOpenHashSet();

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

        this.GRID_CELL_SIZE_IN_CHUNKS = VxModConfig.TERRAIN.gridCellSize.get();
        this.ACTIVATION_RADIUS_CHUNKS = VxModConfig.TERRAIN.activationRadius.get();
        this.PRELOAD_RADIUS_CHUNKS = VxModConfig.TERRAIN.preloadRadius.get();
        this.PREDICTION_SECONDS = VxModConfig.TERRAIN.predictionSeconds.get().floatValue();
        this.MAX_PREDICTION_DISTANCE = VxModConfig.TERRAIN.maxPredictionDistance.get().floatValue();
        this.MAX_CHUNKS_PER_CLUSTER_ITERATION = VxModConfig.TERRAIN.maxChunksPerCluster.get();
        this.MAX_GENERATION_HEIGHT = VxModConfig.TERRAIN.maxGenHeight.get();
        this.MIN_GENERATION_HEIGHT = VxModConfig.TERRAIN.minGenHeight.get();
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
        LongSet currentlyRequiredChunks = calculateRequiredPreloadSet(currentBodies);

        // 2. Request new chunks using packed long coordinates.
        for (long packedPos : currentlyRequiredChunks) {
            if (!previouslyRequiredChunks.contains(packedPos)) {
                terrainManager.requestChunk(packedPos);
            }
        }

        // 3. Release old chunks using packed long coordinates.
        for (long packedPos : previouslyRequiredChunks) {
            if (!currentlyRequiredChunks.contains(packedPos)) {
                terrainManager.releaseChunk(packedPos);
            }
        }

        // 4. Update the state for the next tick using a primitive-based copy.
        this.previouslyRequiredChunks = new LongOpenHashSet(currentlyRequiredChunks);

        // 5. Handle fine-grained activation for bodies that are actually moving.
        updateChunkActivation(currentBodies);
    }

    /**
     * Groups all physics bodies into grid-based clusters and calculates the union of
     * chunks required by each cluster.
     * <p>
     * This implementation reuses the {@code cachedBodyClusters} map and its internal lists to
     * ensure zero allocation during steady-state execution. It clears the collections
     * instead of re-instantiating them.
     *
     * @param allBodies A list of all physics bodies currently in the world.
     * @return A set of bit-packed long coordinates representing all chunks that should be loaded.
     */
    private LongSet calculateRequiredPreloadSet(List<VxBody> allBodies) {
        // Clear previous data without discarding the internal arrays/buckets to preserve memory capacity
        for (ObjectArrayList<VxBody> list : cachedBodyClusters.values()) {
            list.clear();
        }
        cachedBodyClusters.clear();
        requiredChunksCache.clear();

        // Iterate via index to avoid Iterator allocation
        for (int b = 0, size = allBodies.size(); b < size; b++) {
            VxBody body = allBodies.get(b);
            int i = body.getDataStoreIndex();
            if (i == -1) continue;

            // Access raw primitive arrays from the DataStore
            float px = (float) bodyDataStore.posX[i];
            float py = (float) bodyDataStore.posY[i];
            float pz = (float) bodyDataStore.posZ[i];

            // Safety check for NaN values which could crash the spatial hashing
            if (!Float.isFinite(px) || !Float.isFinite(py) || !Float.isFinite(pz)) {
                continue;
            }

            // Height filtering to avoid processing bodies far outside valid terrain range
            if (py > MAX_GENERATION_HEIGHT || py < MIN_GENERATION_HEIGHT) {
                continue;
            }

            // Inline coordinate calculation to avoid creating SectionPos objects
            int cellX = SectionPos.blockToSectionCoord(px) / GRID_CELL_SIZE_IN_CHUNKS;
            int cellY = SectionPos.blockToSectionCoord(py) / GRID_CELL_SIZE_IN_CHUNKS;
            int cellZ = SectionPos.blockToSectionCoord(pz) / GRID_CELL_SIZE_IN_CHUNKS;

            long cellKey = SectionPos.asLong(cellX, cellY, cellZ);

            // Populate the reusable structure
            cachedBodyClusters.computeIfAbsent(cellKey, k -> new ObjectArrayList<>()).add(body);
        }

        // Process the formed clusters
        for (ObjectArrayList<VxBody> cluster : cachedBodyClusters.values()) {
            if (cluster.isEmpty()) continue;

            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            boolean clusterHasValidBodies = false;

            for (int k = 0; k < cluster.size(); k++) {
                VxBody body = cluster.get(k);
                int i = body.getDataStoreIndex();
                if (i == -1) continue;

                float bMinX = bodyDataStore.aabbMinX[i];
                float bMinY = bodyDataStore.aabbMinY[i];
                float bMinZ = bodyDataStore.aabbMinZ[i];
                float bMaxX = bodyDataStore.aabbMaxX[i];
                float bMaxY = bodyDataStore.aabbMaxY[i];
                float bMaxZ = bodyDataStore.aabbMaxZ[i];

                if (!Float.isFinite(bMinX) || !Float.isFinite(bMaxX)) continue;

                clusterHasValidBodies = true;

                // Expand cluster AABB by body bounds
                minX = Math.min(minX, bMinX);
                minY = Math.min(minY, bMinY);
                minZ = Math.min(minZ, bMinZ);
                maxX = Math.max(maxX, bMaxX);
                maxY = Math.max(maxY, bMaxY);
                maxZ = Math.max(maxZ, bMaxZ);

                // Velocity Prediction: Expand AABB based on where the body is projected to be.
                // This allows terrain to load ahead of moving objects.
                float velX = bodyDataStore.velX[i];
                float velY = bodyDataStore.velY[i];
                float velZ = bodyDataStore.velZ[i];

                if (Math.abs(velX) > 0.01f || Math.abs(velY) > 0.01f || Math.abs(velZ) > 0.01f) {
                    float predictedOffsetX = velX * PREDICTION_SECONDS;
                    float predictedOffsetY = velY * PREDICTION_SECONDS;
                    float predictedOffsetZ = velZ * PREDICTION_SECONDS;

                    // Clamp the prediction vector to the configured maximum distance.
                    // This prevents extreme velocities (e.g., from physics overlaps) from requesting
                    // massive amounts of terrain, which would trigger the safety brake.
                    predictedOffsetX = Math.max(-MAX_PREDICTION_DISTANCE, Math.min(MAX_PREDICTION_DISTANCE, predictedOffsetX));
                    predictedOffsetY = Math.max(-MAX_PREDICTION_DISTANCE, Math.min(MAX_PREDICTION_DISTANCE, predictedOffsetY));
                    predictedOffsetZ = Math.max(-MAX_PREDICTION_DISTANCE, Math.min(MAX_PREDICTION_DISTANCE, predictedOffsetZ));

                    minX = Math.min(minX, bMinX + predictedOffsetX);
                    minY = Math.min(minY, bMinY + predictedOffsetY);
                    minZ = Math.min(minZ, bMinZ + predictedOffsetZ);
                    maxX = Math.max(maxX, bMaxX + predictedOffsetX);
                    maxY = Math.max(maxY, bMaxY + predictedOffsetY);
                    maxZ = Math.max(maxZ, bMaxZ + predictedOffsetZ);
                }
            }

            if (clusterHasValidBodies) {
                forEachSectionInBox(minX, minY, minZ, maxX, maxY, maxZ, PRELOAD_RADIUS_CHUNKS, requiredChunksCache);
            }
        }

        // Return the primitive set; the caller compares it with previous state.
        return requiredChunksCache;
    }

    /**
     * Updates the set of active terrain chunks for the current physics tick.
     * This logic activates chunks only around bodies that are currently in motion.
     *
     * @param allBodies The list of all physics bodies in the world.
     */
    private void updateChunkActivation(List<VxBody> allBodies) {
        LongSet requiredActiveSet = new LongOpenHashSet();

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

        // Convert packed coordinates back to prioritized building tasks
        for (long packed : requiredActiveSet) {
            terrainManager.prioritizeChunk(packed);
        }

        LongSet currentlyActive = new LongOpenHashSet();
        for (int index : chunkDataStore.getActiveIndices()) {
            if (chunkDataStore.getState(index) == VxTerrainManager.STATE_READY_ACTIVE) {
                currentlyActive.add(chunkDataStore.getPackedPosForIndex(index));
            }
        }

        for (long packed : currentlyActive) {
            if (!requiredActiveSet.contains(packed)) {
                terrainManager.deactivateChunk(packed);
            }
        }

        for (long packed : requiredActiveSet) {
            if (!currentlyActive.contains(packed)) {
                terrainManager.activateChunk(packed);
            }
        }
    }

    /**
     * Helper method to iterate over all chunk sections that overlap a given AABB, expanded by a radius.
     * Includes a safety brake to prevent processing excessively large areas due to physics glitches.
     *
     * @param minX           The minimum X coordinate of the bounding box.
     * @param minY           The minimum Y coordinate of the bounding box.
     * @param minZ           The minimum Z coordinate of the bounding box.
     * @param maxX           The maximum X coordinate of the bounding box.
     * @param maxY           The maximum Y coordinate of the bounding box.
     * @param maxZ           The maximum Z coordinate of the bounding box.
     * @param radiusInChunks The radius in chunks to expand the box by.
     * @param outChunks      The set to which the overlapping bit-packed chunk positions will be added.
     */
    private void forEachSectionInBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int radiusInChunks, LongSet outChunks) {
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
                    // Pack x, y, z into a single long coordinate to eliminate Garbage Collection pressure.
                    outChunks.add(SectionPos.asLong(x, y, z));
                }
            }
        }
    }

    /**
     * Releases all chunks currently held by the tracker.
     */
    private void releaseAllChunks() {
        if (previouslyRequiredChunks.isEmpty()) return;
        for (long packed : previouslyRequiredChunks) {
            terrainManager.releaseChunk(packed);
        }
        previouslyRequiredChunks.clear();
    }

    /**
     * Deactivates all currently managed terrain chunks.
     */
    private void deactivateAllChunks() {
        for (int index : chunkDataStore.getActiveIndices()) {
            terrainManager.deactivateChunk(chunkDataStore.getPackedPosForIndex(index));
        }
    }

    /**
     * Clears all tracking data and releases all held chunks.
     */
    public void clear() {
        releaseAllChunks();
    }
}