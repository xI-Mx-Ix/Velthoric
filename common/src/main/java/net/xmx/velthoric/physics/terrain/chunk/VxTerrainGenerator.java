/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.chunk;

import com.github.stephengold.joltjni.*;
import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.terrain.cache.VxTerrainShapeCache;
import net.xmx.velthoric.physics.terrain.persistence.VxTerrainStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Responsible for generating optimized Jolt physics shapes from chunk data.
 * <p>
 * This class implements a greedy meshing algorithm to combine adjacent, identical
 * blocks into larger box shapes. It interacts with a cache and persistent storage
 * to avoid redundant computations by storing and reconstructing shapes from pure
 * geometric data rather than native binary blobs.
 *
 * @author xI-Mx-Ix
 */
public final class VxTerrainGenerator {

    private final VxTerrainShapeCache shapeCache;
    private final VxTerrainStorage terrainStorage;
    private static final int CHUNK_DIM = 16;

    /**
     * A simple container for the results of the greedy meshing process.
     */
    private record GreedyMeshResult(ShapeRefC shape, List<VxTerrainStorage.BoxData> data) {}

    public VxTerrainGenerator(VxTerrainShapeCache shapeCache, VxTerrainStorage terrainStorage) {
        this.shapeCache = shapeCache;
        this.terrainStorage = terrainStorage;
    }

    @Nullable
    public ShapeRefC generateShape(ServerLevel level, VxChunkSnapshot snapshot) {
        if (snapshot.isEmpty()) {
            terrainStorage.removeShape(snapshot.pos());
            return null;
        }

        int contentHash = computeContentHash(snapshot);

        ShapeRefC cached = shapeCache.get(contentHash);
        if (cached != null) {
            return cached;
        }

        List<VxTerrainStorage.BoxData> storedData = terrainStorage.getShapeData(snapshot.pos(), contentHash);
        if (storedData != null && !storedData.isEmpty()) {
            ShapeRefC reconstructedShape = reconstructShapeFromData(storedData);
            if (reconstructedShape != null) {
                shapeCache.put(contentHash, reconstructedShape.getPtr().toRefC());
            }
            return reconstructedShape;
        }

        GreedyMeshResult result = generateGreedyMeshShape(snapshot);
        if (result != null && result.shape != null) {
            terrainStorage.storeShapeData(snapshot.pos(), contentHash, result.data());
            shapeCache.put(contentHash, result.shape.getPtr().toRefC());
            return result.shape;
        } else {
            terrainStorage.removeShape(snapshot.pos());
        }

        return null;
    }

    /**
     * Reconstructs a Jolt physics shape from a list of box geometry data.
     *
     * @param data The list of boxes to include in the shape.
     * @return A new reference to the reconstructed compound shape, or null on failure.
     */
    @Nullable
    private ShapeRefC reconstructShapeFromData(List<VxTerrainStorage.BoxData> data) {
        try (StaticCompoundShapeSettings compoundSettings = new StaticCompoundShapeSettings()) {
            for (VxTerrainStorage.BoxData box : data) {
                Vec3 halfExtents = new Vec3(box.hx(), box.hy(), box.hz());
                Vec3 position = new Vec3(box.px(), box.py(), box.pz());
                BoxShapeSettings boxSettings = new BoxShapeSettings(halfExtents);
                ShapeResult boxResult = boxSettings.create();

                if (boxResult.isValid()) {
                    try (ShapeRefC boxShape = boxResult.get()) {
                        compoundSettings.addShape(position, Quat.sIdentity(), boxShape);
                    }
                }
            }
            try (ShapeResult result = compoundSettings.create()) {
                if (result.isValid()) {
                    return result.get();
                } else {
                    VxMainClass.LOGGER.error("Failed to reconstruct terrain compound shape: {}", result.getError());
                }
            }
        }
        return null;
    }

    @Nullable
    private GreedyMeshResult generateGreedyMeshShape(VxChunkSnapshot snapshot) {
        try (StaticCompoundShapeSettings compoundSettings = new StaticCompoundShapeSettings()) {
            List<VxTerrainStorage.BoxData> boxDataList = new ArrayList<>();
            boolean[] visited = new boolean[CHUNK_DIM * CHUNK_DIM * CHUNK_DIM];
            int[] blockData = snapshot.blockData();

            for (int y = 0; y < CHUNK_DIM; y++) {
                for (int z = 0; z < CHUNK_DIM; z++) {
                    for (int x = 0; x < CHUNK_DIM; x++) {
                        int index = getIndex(x, y, z);
                        if (visited[index] || blockData[index] == 0) continue;

                        int blockId = blockData[index];
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

                        float hx = width / 2.0f, hy = height / 2.0f, hz = depth / 2.0f;
                        float px = x + hx, py = y + hy, pz = z + hz;

                        boxDataList.add(new VxTerrainStorage.BoxData(px, py, pz, hx, hy, hz));

                        Vec3 halfExtents = new Vec3(hx, hy, hz);
                        Vec3 position = new Vec3(px, py, pz);
                        BoxShapeSettings boxSettings = new BoxShapeSettings(halfExtents);
                        ShapeResult boxResult = boxSettings.create();
                        if (boxResult.isValid()) {
                            try (ShapeRefC boxShape = boxResult.get()) {
                                compoundSettings.addShape(position, Quat.sIdentity(), boxShape);
                            }
                        }
                    }
                }
            }

            if (boxDataList.isEmpty()) return null;

            try (ShapeResult result = compoundSettings.create()) {
                if (result.isValid()) {
                    return new GreedyMeshResult(result.get(), boxDataList);
                } else {
                    VxMainClass.LOGGER.error("Failed to create terrain compound shape for {}: {}", snapshot.pos(), result.getError());
                }
            }
        }
        return null;
    }

    private int getIndex(int x, int y, int z) {
        return y * CHUNK_DIM * CHUNK_DIM + z * CHUNK_DIM + x;
    }

    private int computeContentHash(VxChunkSnapshot snapshot) {
        return Arrays.hashCode(snapshot.blockData());
    }
}