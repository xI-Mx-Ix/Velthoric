/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.chunk;

import com.github.stephengold.joltjni.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.terrain.cache.VxTerrainShapeCache;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;

/**
 * Responsible for generating optimized Jolt physics shapes from chunk data.
 * <p>
 * This class uses a greedy meshing algorithm for full-cube blocks and generates
 * detailed shapes for complex blocks. It relies on an in-memory cache to avoid
 * re-computing shapes for identical chunk sections. It does not persist shapes
 * to disk, as recalculation is extremely fast and more robust.
 *
 * @author xI-Mx-Ix
 */
public final class VxTerrainGenerator {

    private final VxTerrainShapeCache shapeCache;
    private static final int CHUNK_DIM = 16;

    public VxTerrainGenerator(VxTerrainShapeCache shapeCache) {
        this.shapeCache = shapeCache;
    }

    @Nullable
    public ShapeRefC generateShape(ServerLevel level, VxChunkSnapshot snapshot) {
        if (snapshot.isEmpty()) {
            return null;
        }

        int contentHash = computeContentHash(snapshot);

        // 1. Check the fast in-memory cache.
        ShapeRefC cached = shapeCache.get(contentHash);
        if (cached != null) {
            return cached;
        }

        // 2. If not in cache, generate it now.
        ShapeRefC generatedShape = generateAccurateGreedyMeshShape(snapshot);

        // 3. Put the newly generated shape into the cache for future requests.
        if (generatedShape != null) {
            shapeCache.put(contentHash, generatedShape.getPtr().toRefC());
        }

        return generatedShape;
    }

    /**
     * Generates a physics shape by applying greedy meshing to full-cube blocks and
     * generating exact shapes for complex blocks.
     *
     * @param snapshot The chunk data snapshot.
     * @return The generated compound shape, or null if the chunk was effectively empty.
     */
    @Nullable
    private ShapeRefC generateAccurateGreedyMeshShape(VxChunkSnapshot snapshot) {
        try (StaticCompoundShapeSettings compoundSettings = new StaticCompoundShapeSettings()) {
            boolean hasAnyShape = false;
            boolean[] visited = new boolean[CHUNK_DIM * CHUNK_DIM * CHUNK_DIM];
            int[] blockData = snapshot.blockData();
            Map<Integer, BlockState> palette = snapshot.palette();

            for (int y = 0; y < CHUNK_DIM; y++) {
                for (int z = 0; z < CHUNK_DIM; z++) {
                    for (int x = 0; x < CHUNK_DIM; x++) {
                        int index = getIndex(x, y, z);
                        if (visited[index] || blockData[index] == 0) continue;

                        int blockId = blockData[index];
                        hasAnyShape = true;

                        if (snapshot.fullCubeIds().contains(blockId)) {
                            // --- GREEDY MESHING FOR FULL CUBES ---
                            int width = 1; // x-axis
                            while (x + width < CHUNK_DIM && !visited[getIndex(x + width, y, z)] && blockData[getIndex(x + width, y, z)] == blockId) width++;

                            int height = 1; // y-axis
                            height_loop:
                            while (y + height < CHUNK_DIM) {
                                for (int i = 0; i < width; i++) {
                                    if (visited[getIndex(x + i, y + height, z)] || blockData[getIndex(x + i, y + height, z)] != blockId) break height_loop;
                                }
                                height++;
                            }

                            int depth = 1; // z-axis
                            depth_loop:
                            while (z + depth < CHUNK_DIM) {
                                for (int j = 0; j < height; j++) {
                                    for (int i = 0; i < width; i++) {
                                        if (visited[getIndex(x + i, y + j, z + depth)] || blockData[getIndex(x + i, y + j, z + depth)] != blockId) break depth_loop;
                                    }
                                }
                                depth++;
                            }

                            for (int dz = 0; dz < depth; dz++) for (int dy = 0; dy < height; dy++) for (int dx = 0; dx < width; dx++) {
                                visited[getIndex(x + dx, y + dy, z + dz)] = true;
                            }
                            addBox(compoundSettings, x, y, z, width, height, depth);

                        } else {
                            // --- DETAILED SHAPE FOR COMPLEX BLOCKS ---
                            BlockState state = palette.get(blockId);
                            for (AABB aabb : state.getShape(null, null).toAabbs()) {
                                addBoxFromAABB(compoundSettings, aabb, x, y, z);
                            }
                            visited[index] = true;
                        }
                    }
                }
            }

            if (!hasAnyShape) return null;

            try (ShapeResult result = compoundSettings.create()) {
                if (result.isValid()) {
                    return result.get();
                } else {
                    VxMainClass.LOGGER.error("Failed to create terrain compound shape for {}: {}", snapshot.pos(), result.getError());
                }
            }
        }
        return null;
    }

    private void addBoxFromAABB(StaticCompoundShapeSettings settings, AABB aabb, int offsetX, int offsetY, int offsetZ) {
        float hx = (float) (aabb.maxX - aabb.minX) / 2.0f;
        float hy = (float) (aabb.maxY - aabb.minY) / 2.0f;
        float hz = (float) (aabb.maxZ - aabb.minZ) / 2.0f;
        float px = (float) (offsetX + aabb.minX + hx);
        float py = (float) (offsetY + aabb.minY + hy);
        float pz = (float) (offsetZ + aabb.minZ + hz);

        try (BoxShapeSettings boxSettings = new BoxShapeSettings(new Vec3(hx, hy, hz))) {
            try(ShapeResult boxResult = boxSettings.create()){
                if (boxResult.isValid()) {
                    try (ShapeRefC boxShape = boxResult.get()) {
                        settings.addShape(new Vec3(px, py, pz), Quat.sIdentity(), boxShape);
                    }
                }
            }
        }
    }

    private void addBox(StaticCompoundShapeSettings settings, int x, int y, int z, int width, int height, int depth) {
        float hx = width / 2.0f;
        float hy = height / 2.0f;
        float hz = depth / 2.0f;
        float px = x + hx;
        float py = y + hy;
        float pz = z + hz;

        try (BoxShapeSettings boxSettings = new BoxShapeSettings(new Vec3(hx, hy, hz))) {
            try (ShapeResult boxResult = boxSettings.create()) {
                if (boxResult.isValid()) {
                    try (ShapeRefC boxShape = boxResult.get()) {
                        settings.addShape(new Vec3(px, py, pz), Quat.sIdentity(), boxShape);
                    }
                }
            }
        }
    }

    private int getIndex(int x, int y, int z) {
        return y * CHUNK_DIM * CHUNK_DIM + z * CHUNK_DIM + x;
    }

    private int computeContentHash(VxChunkSnapshot snapshot) {
        return Arrays.hashCode(snapshot.blockData());
    }
}