/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.generation;

import com.github.stephengold.joltjni.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.velthoric.init.VxMainClass;

/**
 * Generates physics shapes for terrain chunks using a StaticCompoundShape.
 * This generator iterates through all blocks in a chunk snapshot and adds their
 * collision bounding boxes as individual box shapes to a single compound shape.
 * This approach is significantly faster than triangle-based mesh generation.
 *
 * @author xI-Mx-Ix
 */
public final class VxTerrainGenerator {

    /**
     * Constructs a new terrain generator.
     */
    public VxTerrainGenerator() {
        // Constructor is now empty as cache and storage dependencies are removed.
    }

    /**
     * Generates a {@link ShapeRefC} for the given chunk snapshot.
     * <p>
     * The method creates a {@link StaticCompoundShape} composed of multiple {@link BoxShape}s,
     * where each box represents a collision AABB of a block in the chunk.
     *
     * @param level The server level, used to get context-aware collision shapes.
     * @param snapshot An immutable snapshot of the chunk section's block data.
     * @return A reference to the generated compound shape (ShapeRefC), or {@code null} if the chunk is empty or generation fails.
     *         The caller is responsible for closing the returned shape reference.
     */
    public ShapeRefC generateShape(ServerLevel level, VxChunkSnapshot snapshot) {
        if (snapshot.shapes().isEmpty()) {
            return null; // No collidable blocks, so no shape is needed.
        }

        try (StaticCompoundShapeSettings compoundSettings = new StaticCompoundShapeSettings()) {
            boolean hasShapes = false;
            for (VxChunkSnapshot.ShapeInfo info : snapshot.shapes()) {
                BlockPos worldPos = snapshot.pos().getOrigin().offset(info.localPos());
                VoxelShape voxelShape = info.state().getCollisionShape(level, worldPos);

                for (AABB aabb : voxelShape.toAabbs()) {
                    // Calculate half-extents for the BoxShape.
                    float hx = (float) (aabb.getXsize() / 2.0);
                    float hy = (float) (aabb.getYsize() / 2.0);
                    float hz = (float) (aabb.getZsize() / 2.0);

                    // Ignore degenerate boxes.
                    if (hx <= 0 || hy <= 0 || hz <= 0) {
                        continue;
                    }

                    // Calculate the center position of the box relative to the chunk section's origin.
                    float cx = (float) (info.localPos().getX() + aabb.minX + hx);
                    float cy = (float) (info.localPos().getY() + aabb.minY + hy);
                    float cz = (float) (info.localPos().getZ() + aabb.minZ + hz);

                    try (BoxShapeSettings boxSettings = new BoxShapeSettings(hx, hy, hz)) {
                        compoundSettings.addShape(cx, cy, cz, boxSettings);
                        hasShapes = true;
                    }
                }
            }

            if (!hasShapes) {
                return null;
            }

            // Create the final compound shape from the settings.
            try (ShapeResult result = compoundSettings.create()) {
                if (result.isValid()) {
                    return result.get();
                } else {
                    VxMainClass.LOGGER.error("Failed to create StaticCompoundShape for {}: {}", snapshot.pos(), result.getError());
                    return null;
                }
            }
        }
    }
}