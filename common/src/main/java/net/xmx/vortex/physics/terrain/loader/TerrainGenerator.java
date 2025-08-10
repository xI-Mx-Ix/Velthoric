package net.xmx.vortex.physics.terrain.loader;

import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.StaticCompoundShapeSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.xmx.vortex.physics.terrain.cache.TerrainShapeCache;

import java.util.Arrays;
import java.util.Objects;

public final class TerrainGenerator {

    private final TerrainShapeCache shapeCache;
    private static final float MIN_BOX_HALF_EXTENT = 0.01f;

    public TerrainGenerator(TerrainShapeCache shapeCache) {
        this.shapeCache = shapeCache;
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

        try (StaticCompoundShapeSettings compoundSettings = new StaticCompoundShapeSettings()) {

            for (ChunkSnapshot.ShapeInfo info : snapshot.shapes()) {

                AABB blockAabb = info.state()
                        .getCollisionShape(level, snapshot.pos().getOrigin().offset(info.localPos()))
                        .bounds();

                float halfX = (float) (blockAabb.getXsize() / 2.0);
                float halfY = (float) (blockAabb.getYsize() / 2.0);
                float halfZ = (float) (blockAabb.getZsize() / 2.0);

                halfX = Math.max(halfX, MIN_BOX_HALF_EXTENT);
                halfY = Math.max(halfY, MIN_BOX_HALF_EXTENT);
                halfZ = Math.max(halfZ, MIN_BOX_HALF_EXTENT);

                float centerX = (float) (info.localPos().getX() + blockAabb.minX + halfX);
                float centerY = (float) (info.localPos().getY() + blockAabb.minY + halfY);
                float centerZ = (float) (info.localPos().getZ() + blockAabb.minZ + halfZ);

                try (BoxShapeSettings boxSettings = new BoxShapeSettings(halfX, halfY, halfZ)) {

                    compoundSettings.addShape(centerX, centerY, centerZ, boxSettings);
                }
            }

            try (ShapeResult result = compoundSettings.create()) {
                if (result.isValid()) {
                    ShapeRefC newShape = result.get();

                    shapeCache.put(contentHash, newShape.getPtr().toRefC());
                    return newShape;
                } else {

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