/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.generation;

import com.github.stephengold.joltjni.*;
import net.minecraft.core.BlockPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.terrain.cache.VxTerrainShapeCache;
import net.xmx.velthoric.physics.terrain.chunk.VxChunkSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Generates terrain shapes by applying a greedy meshing algorithm to voxel data.
 * This class merges adjacent solid blocks within a chunk section into larger boxes
 * to create an efficient static compound shape for physics simulation.
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
     * Performs the greedy meshing algorithm on the voxel data from a chunk snapshot.
     *
     * @param snapshot The chunk snapshot containing voxel data.
     * @return A {@link ShapeRefC} representing the optimized compound shape.
     */
    private ShapeRefC generateShapeFromVoxels(VxChunkSnapshot snapshot) {
        boolean[][][] voxels = new boolean[16][16][16];
        for (VxChunkSnapshot.ShapeInfo info : snapshot.shapes()) {
            BlockPos local = info.localPos();
            voxels[local.getX()][local.getY()][local.getZ()] = true;
        }

        boolean[][][] visited = new boolean[16][16][16];
        StaticCompoundShapeSettings settings = new StaticCompoundShapeSettings();
        int boxCount = 0;

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (!voxels[x][y][z] || visited[x][y][z]) {
                        continue;
                    }

                    // Expand along X axis (width)
                    int width = 1;
                    while (x + width < 16 && !visited[x + width][y][z] && voxels[x + width][y][z]) {
                        width++;
                    }

                    // Expand along Z axis (depth)
                    int depth = 1;
                    boolean canExpandZ = true;
                    while (z + depth < 16 && canExpandZ) {
                        for (int k = 0; k < width; k++) {
                            if (visited[x + k][y][z + depth] || !voxels[x + k][y][z + depth]) {
                                canExpandZ = false;
                                break;
                            }
                        }
                        if (canExpandZ) {
                            depth++;
                        }
                    }

                    // Expand along Y axis (height)
                    int height = 1;
                    boolean canExpandY = true;
                    while (y + height < 16 && canExpandY) {
                        for (int kx = 0; kx < width; kx++) {
                            for (int kz = 0; kz < depth; kz++) {
                                if (visited[x + kx][y + height][z + kz] || !voxels[x + kx][y + height][z + kz]) {
                                    canExpandY = false;
                                    break;
                                }
                            }
                            if (!canExpandY) break;
                        }
                        if (canExpandY) {
                            height++;
                        }
                    }

                    // Mark all voxels in the merged box as visited
                    for (int dy = 0; dy < height; dy++) {
                        for (int dz = 0; dz < depth; dz++) {
                            for (int dx = 0; dx < width; dx++) {
                                visited[x + dx][y + dy][z + dz] = true;
                            }
                        }
                    }

                    // Add the merged box to the compound shape
                    float boxHalfWidth = width / 2.0f;
                    float boxHalfHeight = height / 2.0f;
                    float boxHalfDepth = depth / 2.0f;

                    Vec3 halfExtent = new Vec3(boxHalfWidth, boxHalfHeight, boxHalfDepth);
                    Vec3 position = new Vec3(x + boxHalfWidth, y + boxHalfHeight, z + boxHalfDepth);
                    BoxShapeSettings boxSettings = new BoxShapeSettings(halfExtent);
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
     * Computes a hash based on the content of the chunk snapshot.
     *
     * @param snapshot The chunk snapshot.
     * @return An integer hash code.
     */
    private int computeContentHash(VxChunkSnapshot snapshot) {
        List<Integer> hashes = new ArrayList<>(snapshot.shapes().size());
        for (VxChunkSnapshot.ShapeInfo info : snapshot.shapes()) {
            hashes.add(Objects.hash(info.state().hashCode(), info.localPos().hashCode()));
        }
        Collections.sort(hashes);
        return hashes.hashCode();
    }
}