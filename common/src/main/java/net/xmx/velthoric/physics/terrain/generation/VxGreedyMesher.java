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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Generates terrain shapes by applying a greedy meshing algorithm to voxel data.
 * This class merges adjacent solid voxels from all block shapes within a chunk section
 * into larger boxes to create an efficient static compound shape for physics simulation.
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

        ShapeRefC generatedShape = generateShapeFromVoxels(snapshot);
        if (generatedShape != null) {
            shapeCache.put(contentHash, generatedShape.getPtr().toRefC());
        }

        return generatedShape;
    }

    /**
     * Performs the greedy meshing algorithm on the detailed voxel data from a chunk snapshot.
     *
     * @param snapshot The chunk snapshot containing detailed voxel shapes.
     * @return A {@link ShapeRefC} representing the optimized compound shape.
     */
    private ShapeRefC generateShapeFromVoxels(VxChunkSnapshot snapshot) {
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

        boolean[][][] visited = new boolean[GRID_SIZE][GRID_SIZE][GRID_SIZE];
        StaticCompoundShapeSettings settings = new StaticCompoundShapeSettings();
        int boxCount = 0;

        // Step 2: Run the greedy meshing algorithm on the high-resolution grid
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int z = 0; z < GRID_SIZE; z++) {
                for (int x = 0; x < GRID_SIZE; x++) {
                    if (!subVoxels[x][y][z] || visited[x][y][z]) {
                        continue;
                    }

                    int width = 1;
                    while (x + width < GRID_SIZE && !visited[x + width][y][z] && subVoxels[x + width][y][z]) {
                        width++;
                    }

                    int depth = 1;
                    boolean canExpandZ = true;
                    while (z + depth < GRID_SIZE && canExpandZ) {
                        for (int k = 0; k < width; k++) {
                            if (visited[x + k][y][z + depth] || !subVoxels[x + k][y][z + depth]) {
                                canExpandZ = false;
                                break;
                            }
                        }
                        if (canExpandZ) depth++;
                    }

                    int height = 1;
                    boolean canExpandY = true;
                    while (y + height < GRID_SIZE && canExpandY) {
                        for (int kx = 0; kx < width; kx++) {
                            for (int kz = 0; kz < depth; kz++) {
                                if (visited[x + kx][y + height][z + kz] || !subVoxels[x + kx][y + height][z + kz]) {
                                    canExpandY = false;
                                    break;
                                }
                            }
                            if (!canExpandY) break;
                        }
                        if (canExpandY) height++;
                    }

                    for (int dy = 0; dy < height; dy++) {
                        for (int dz = 0; dz < depth; dz++) {
                            for (int dx = 0; dx < width; dx++) {
                                visited[x + dx][y + dy][z + dz] = true;
                            }
                        }
                    }

                    float boxHalfWidth = width * SCALE / 2.0f;
                    float boxHalfHeight = height * SCALE / 2.0f;
                    float boxHalfDepth = depth * SCALE / 2.0f;

                    Vec3 halfExtent = new Vec3(boxHalfWidth, boxHalfHeight, boxHalfDepth);
                    Vec3 position = new Vec3(x * SCALE + boxHalfWidth, y * SCALE + boxHalfHeight, z * SCALE + boxHalfDepth);

                    BoxShapeSettings boxSettings = new BoxShapeSettings(halfExtent);

                    boxSettings.setConvexRadius(0.0f);

                    settings.addShape(position, Quat.sIdentity(), boxSettings);
                    boxCount++;
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
                VxMainClass.LOGGER.error("Failed to create terrain compound shape for {}: {}", snapshot.pos(), result.getError());
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