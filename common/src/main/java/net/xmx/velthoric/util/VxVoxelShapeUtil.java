/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.util;

import com.github.stephengold.joltjni.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.velthoric.core.body.shape.VxBoxShape;
import net.xmx.velthoric.core.body.shape.VxMutableCompoundShape;

import java.util.List;

/**
 * A utility class for converting Minecraft's {@link VoxelShape} into a Jolt physics shape.
 *
 * @author xI-Mx-Ix
 */
public final class VxVoxelShapeUtil {

    // Private constructor to prevent instantiation.
    private VxVoxelShapeUtil() {
    }

    /**
     * Converts a {@link VoxelShape} into a {@link VxMutableCompoundShape} wrapper.
     * The VoxelShape is decomposed into its constituent AABBs, and each AABB is converted
     * into a {@link VxBoxShape}, which are then combined into a compound shape.
     *
     * @param voxelShape The Minecraft VoxelShape to convert.
     * @return A compound shape wrapper, or null if the input is empty.
     */
    public static VxMutableCompoundShape toVxCompoundShape(VoxelShape voxelShape) {
        if (voxelShape == null || voxelShape.isEmpty()) {
            return null;
        }

        List<AABB> aabbs = voxelShape.toAabbs();
        if (aabbs.isEmpty()) {
            return null;
        }

        VxMutableCompoundShape compound = new VxMutableCompoundShape();

        for (AABB aabb : aabbs) {
            // Calculate half-extents (hx, hy, hz) of the box.
            float hx = (float) (aabb.maxX - aabb.minX) / 2.0f;
            float hy = (float) (aabb.maxY - aabb.minY) / 2.0f;
            float hz = (float) (aabb.maxZ - aabb.minZ) / 2.0f;

            // Calculate the center position (cx, cy, cz) of the box.
            // An offset of -0.5 is applied to align Minecraft's block-corner-based coordinates
            // with Jolt's center-of-mass-based coordinates.
            float cx = (float) (aabb.minX + hx - 0.5f);
            float cy = (float) (aabb.minY + hy - 0.5f);
            float cz = (float) (aabb.minZ + hz - 0.5f);

            Vec3 position = new Vec3(cx, cy, cz);
            VxBoxShape box = new VxBoxShape(new Vec3(hx, hy, hz), 0.0f);
            compound.addShape(box, position);
        }

        return compound;
    }
}