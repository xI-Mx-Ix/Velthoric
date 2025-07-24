package net.xmx.vortex.physics.terrain.loader;

import com.github.stephengold.joltjni.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.xmx.vortex.physics.terrain.cache.TerrainShapeCache;
import net.xmx.vortex.physics.terrain.greedy.GreedyShapeManager;
import java.util.Arrays;
import java.util.Objects;

public final class TerrainGenerator {

    private final TerrainShapeCache shapeCache;
    private static final float MIN_BOX_HALF_EXTENT = 0.01f;

    public TerrainGenerator(TerrainShapeCache shapeCache) {
        this.shapeCache = shapeCache;
    }

    public ShapeRefC generatePlaceholderShape(ChunkSnapshot snapshot) {
        if (snapshot.shapes().isEmpty()) {
            return null;
        }

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        for (ChunkSnapshot.ShapeInfo info : snapshot.shapes()) {

            AABB blockAabb = info.state().getCollisionShape(null, null).bounds();

            float blockMinX = (float) (info.localPos().getX() + blockAabb.minX);
            float blockMinY = (float) (info.localPos().getY() + blockAabb.minY);
            float blockMinZ = (float) (info.localPos().getZ() + blockAabb.minZ);
            float blockMaxX = (float) (info.localPos().getX() + blockAabb.maxX);
            float blockMaxY = (float) (info.localPos().getY() + blockAabb.maxY);
            float blockMaxZ = (float) (info.localPos().getZ() + blockAabb.maxZ);

            minX = Math.min(minX, blockMinX);
            minY = Math.min(minY, blockMinY);
            minZ = Math.min(minZ, blockMinZ);
            maxX = Math.max(maxX, blockMaxX);
            maxY = Math.max(maxY, blockMaxY);
            maxZ = Math.max(maxZ, blockMaxZ);
        }

        float halfX = (maxX - minX) / 2.0f;
        float halfY = (maxY - minY) / 2.0f;
        float halfZ = (maxZ - minZ) / 2.0f;

        float centerX = minX + halfX;
        float centerY = minY + halfY;
        float centerZ = minZ + halfZ;

        halfX = Math.max(halfX, MIN_BOX_HALF_EXTENT);
        halfY = Math.max(halfY, MIN_BOX_HALF_EXTENT);
        halfZ = Math.max(halfZ, MIN_BOX_HALF_EXTENT);

        try (
                BoxShapeSettings boxSettings = new BoxShapeSettings(halfX, halfY, halfZ);
                StaticCompoundShapeSettings compoundSettings = new StaticCompoundShapeSettings()
        ) {
            compoundSettings.addShape(centerX, centerY, centerZ, boxSettings);
            try (ShapeResult result = compoundSettings.create()) {
                if (result.isValid()) {
                    return result.get();
                }
            }
        }

        return null;
    }

    public ShapeRefC generateShape(ServerLevel level, ChunkSnapshot snapshot) {
        if (snapshot.shapes().isEmpty()) {
            return null;
        }

        int contentHash = computeContentHash(snapshot);
        ShapeRefC cachedShape = shapeCache.get(contentHash);
        if (cachedShape != null) {
            return cachedShape;
        }

        GreedyShapeManager shapeManager = new GreedyShapeManager();
        snapshot.shapes().forEach(info ->
                shapeManager.addVoxelShape(
                        info.state().getCollisionShape(level, snapshot.pos().getOrigin().offset(info.localPos())),
                        info.localPos()
                )
        );

        try (ShapeSettings settings = shapeManager.buildShape()) {
            if (settings == null) {
                return null;
            }
            try (ShapeResult result = settings.create()) {
                if (result.isValid()) {
                    ShapeRefC newShape = result.get();
                    shapeCache.put(contentHash, newShape.getPtr().toRefC());
                    return newShape;
                }
            }
        }
        return null;
    }

    private int computeContentHash(ChunkSnapshot snapshot) {
        int[] hashes = new int[snapshot.shapes().size()];
        for (int i = 0; i < snapshot.shapes().size(); i++) {
            ChunkSnapshot.ShapeInfo info = snapshot.shapes().get(i);
            hashes[i] = Objects.hash(info.state().hashCode(), info.localPos().hashCode());
        }
        return Arrays.hashCode(hashes);
    }
}