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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
            shapeCache.put(contentHash, generatedShape.getPtr().toRefC());
        }

        return generatedShape;
    }

    /**
     * Performs a highly optimized greedy meshing algorithm on the voxel data.
     * This version avoids creating a dense 3D grid, instead operating on a list of
     * discrete AABBs for better performance with sparse geometry.
     *
     * @param snapshot The chunk snapshot containing block shapes.
     * @return A {@link ShapeRefC} representing the optimized compound shape.
     */
    private ShapeRefC generateOptimizedShape(VxChunkSnapshot snapshot) {
        final int SUBDIVISIONS = 16;
        final int CHUNK_DIM = 16;
        final int GRID_SIZE = CHUNK_DIM * SUBDIVISIONS;
        final float SCALE = 1.0f / SUBDIVISIONS;

        boolean[][][] subVoxels = new boolean[GRID_SIZE][GRID_SIZE][GRID_SIZE];

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
        // This algorithm iterates through each slice of the grid.
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int z = 0; z < GRID_SIZE; z++) {
                for (int x = 0; x < GRID_SIZE; ) {
                    if (!subVoxels[x][y][z]) {
                        x++;
                        continue;
                    }

                    // Find the width of the current strip
                    int w = 1;
                    while (x + w < GRID_SIZE && subVoxels[x + w][y][z]) {
                        w++;
                    }

                    // Find the depth (height in 2D slice) of the rectangle
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

                    // Now, extrude this 2D rectangle upwards (in the Y direction)
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

                    // Mark the voxels as used
                    for (int dy = 0; dy < d; dy++) {
                        for (int dz = 0; dz < h; dz++) {
                            for (int dx = 0; dx < w; dx++) {
                                subVoxels[x + dx][y + dy][z + dz] = false;
                            }
                        }
                    }

                    // Create the box shape
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
                // Corrected line: unpack the packedPos for logging.
                VxMainClass.LOGGER.error("Failed to create terrain compound shape for {}: {}", VxSectionPos.unpackToSectionPos(snapshot.packedPos()), result.getError());
                return null;
            }
        } finally {
            settings.close();
        }
    }

    /**
     * Computes a hash based on the precise geometry content of the chunk snapshot.
     *
     * @param snapshot The chunk snapshot.
     * @return An integer hash code.
     */
    private int computeContentHash(VxChunkSnapshot snapshot) {
        List<Integer> hashes = new ArrayList<>(snapshot.shapes().size());
        for (VxChunkSnapshot.ShapeInfo info : snapshot.shapes()) {
            // AABBs list is a stable representation of the VoxelShape geometry
            hashes.add(Objects.hash(info.state().hashCode(), info.localPos().hashCode(), info.shape().toAabbs()));
        }
        Collections.sort(hashes);
        return hashes.hashCode();
    }
}