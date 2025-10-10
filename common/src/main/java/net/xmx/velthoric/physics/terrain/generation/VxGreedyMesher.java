/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.generation;

import com.github.stephengold.joltjni.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.terrain.cache.VxTerrainShapeCache;
import net.xmx.velthoric.physics.terrain.chunk.VxChunkSnapshot;
import net.xmx.velthoric.physics.terrain.data.VxSectionPos;

import java.util.Arrays;
import java.util.Objects;

/**
 * Generates terrain shapes by applying a highly optimized greedy meshing algorithm to voxel data.
 * This class merges adjacent solid voxels from all block shapes within a chunk section
 * into larger boxes to create an efficient static compound shape for physics simulation.
 * The algorithm is designed to be fast and memory-efficient.
 *
 * @author xI-Mx-Ix
 */
public final class VxGreedyMesher {

    private final VxTerrainShapeCache shapeCache;

    // Constants for the meshing grid.
    private static final int SUBDIVISIONS = 16;
    private static final int CHUNK_DIM = 16;
    private static final int GRID_SIZE = CHUNK_DIM * SUBDIVISIONS;
    private static final float SCALE = 1.0f / SUBDIVISIONS;

    /**
     * A thread-local cache for the sub-voxel grid.
     * This is a critical performance optimization. By providing each worker thread with its own
     * reusable grid instance, it avoids the massive memory allocation and subsequent garbage collection
     * overhead that would occur from creating a large 3D array for every meshing task.
     */
    private final ThreadLocal<boolean[][][]> subVoxelGrid = ThreadLocal.withInitial(() -> new boolean[GRID_SIZE][GRID_SIZE][GRID_SIZE]);

    /**
     * Constructs a new greedy mesher.
     *
     * @param shapeCache The cache for storing and retrieving generated shapes.
     */
    public VxGreedyMesher(VxTerrainShapeCache shapeCache) {
        this.shapeCache = shapeCache;
    }

    /**
     * Generates or retrieves a physics shape for the given chunk snapshot.
     *
     * @param snapshot The chunk data to process.
     * @return A reference to the generated or cached {@link ShapeRefC}, or null if the chunk is empty.
     */
    public ShapeRefC generateShape(VxChunkSnapshot snapshot) {
        if (snapshot.shapes().isEmpty()) {
            return null;
        }

        int contentHash = computeContentHash(snapshot);
        ShapeRefC cached = shapeCache.get(contentHash);
        if (cached != null) {
            return cached;
        }

        ShapeRefC generatedShape = generateOptimizedShape(snapshot);
        if (generatedShape != null) {
            // Store a new reference in the cache. The one we return will be owned by the caller.
            shapeCache.put(contentHash, generatedShape.getPtr().toRefC());
        }

        return generatedShape;
    }

    /**
     * Performs a highly optimized greedy meshing algorithm on the voxel data.
     * This version uses a thread-local, reusable grid to eliminate memory allocation overhead.
     *
     * @param snapshot The chunk snapshot containing block shapes.
     * @return A {@link ShapeRefC} representing the optimized compound shape.
     */
    private ShapeRefC generateOptimizedShape(VxChunkSnapshot snapshot) {
        // Retrieve the reusable grid from the thread-local storage.
        boolean[][][] subVoxels = subVoxelGrid.get();

        // Since the grid is reused, it must be cleared before each operation.
        for (boolean[][] plane : subVoxels) {
            for (boolean[] row : plane) {
                Arrays.fill(row, false);
            }
        }

        // Step 1: Voxelize all block shapes onto the high-resolution grid
        for (VxChunkSnapshot.ShapeInfo info : snapshot.shapes()) {
            BlockPos localBlockPos = info.localPos();
            for (AABB aabb : info.shape().toAabbs()) {
                int minX = (int) Math.floor((localBlockPos.getX() + aabb.minX) * SUBDIVISIONS);
                int minY = (int) Math.floor((localBlockPos.getY() + aabb.minY) * SUBDIVISIONS);
                int minZ = (int) Math.floor((localBlockPos.getZ() + aabb.minZ) * SUBDIVISIONS);
                int maxX = (int) Math.ceil((localBlockPos.getX() + aabb.maxX) * SUBDIVISIONS);
                int maxY = (int) Math.ceil((localBlockPos.getY() + aabb.maxY) * SUBDIVISIONS);
                int maxZ = (int) Math.ceil((localBlockPos.getZ() + aabb.maxZ) * SUBDIVISIONS);

                for (int y = minY; y < maxY; y++) {
                    for (int z = minZ; z < maxZ; z++) {
                        for (int x = minX; x < maxX; x++) {
                            if (x >= 0 && x < GRID_SIZE && y >= 0 && y < GRID_SIZE && z >= 0 && z < GRID_SIZE) {
                                subVoxels[x][y][z] = true;
                            }
                        }
                    }
                }
            }
        }

        StaticCompoundShapeSettings settings = new StaticCompoundShapeSettings();
        int boxCount = 0;

        // Step 2: Run the greedy meshing algorithm on the high-resolution grid
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int z = 0; z < GRID_SIZE; z++) {
                for (int x = 0; x < GRID_SIZE; ) {
                    if (!subVoxels[x][y][z]) {
                        x++;
                        continue;
                    }

                    int w = 1;
                    while (x + w < GRID_SIZE && subVoxels[x + w][y][z]) {
                        w++;
                    }

                    int h = 1;
                    boolean canExtend = true;
                    while (z + h < GRID_SIZE && canExtend) {
                        for (int k = 0; k < w; k++) {
                            if (!subVoxels[x + k][y][z + h]) {
                                canExtend = false;
                                break;
                            }
                        }
                        if (canExtend) {
                            h++;
                        }
                    }

                    int d = 1;
                    canExtend = true;
                    while (y + d < GRID_SIZE && canExtend) {
                        for (int kx = 0; kx < w; kx++) {
                            for (int kz = 0; kz < h; kz++) {
                                if (!subVoxels[x + kx][y + d][z + kz]) {
                                    canExtend = false;
                                    break;
                                }
                            }
                            if (!canExtend) break;
                        }
                        if (canExtend) {
                            d++;
                        }
                    }

                    for (int dy = 0; dy < d; dy++) {
                        for (int dz = 0; dz < h; dz++) {
                            for (int dx = 0; dx < w; dx++) {
                                subVoxels[x + dx][y + dy][z + dz] = false;
                            }
                        }
                    }

                    float boxHalfWidth = w * SCALE / 2.0f;
                    float boxHalfHeight = d * SCALE / 2.0f;
                    float boxHalfDepth = h * SCALE / 2.0f;

                    Vec3 halfExtent = new Vec3(boxHalfWidth, boxHalfHeight, boxHalfDepth);
                    Vec3 position = new Vec3(x * SCALE + boxHalfWidth, y * SCALE + boxHalfHeight, z * SCALE + boxHalfDepth);

                    try (BoxShapeSettings boxSettings = new BoxShapeSettings(halfExtent)) {
                        boxSettings.setConvexRadius(0.0f);
                        settings.addShape(position, Quat.sIdentity(), boxSettings);
                        boxCount++;
                    }

                    x += w;
                }
            }
        }

        if (boxCount == 0) {
            settings.close();
            return null;
        }

        try (ShapeResult result = settings.create()) {
            if (result.isValid()) {
                return result.get();
            } else {
                VxMainClass.LOGGER.error("Failed to create terrain compound shape for {}: {}", VxSectionPos.unpackToSectionPos(snapshot.packedPos()), result.getError());
                return null;
            }
        } finally {
            settings.close();
        }
    }

    /**
     * Computes a hash based on the precise geometry content of the chunk snapshot.
     * This version is optimized to reduce memory allocations by using a primitive array.
     *
     * @param snapshot The chunk snapshot.
     * @return An integer hash code.
     */
    private int computeContentHash(VxChunkSnapshot snapshot) {
        var shapes = snapshot.shapes();
        int[] hashes = new int[shapes.size()];
        for (int i = 0; i < shapes.size(); i++) {
            var info = shapes.get(i);
            // VoxelShape.toAabbs() provides a stable, order-independent representation of the geometry.
            hashes[i] = Objects.hash(info.state().hashCode(), info.localPos().hashCode(), info.shape().toAabbs());
        }
        // Sorting ensures that the order of blocks in the snapshot does not affect the final hash.
        Arrays.sort(hashes);
        return Arrays.hashCode(hashes);
    }
}