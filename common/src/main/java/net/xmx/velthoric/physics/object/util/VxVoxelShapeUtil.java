package net.xmx.velthoric.physics.object.util;

import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.MutableCompoundShapeSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.velthoric.init.VxMainClass;

import java.util.List;

public final class VxVoxelShapeUtil {

    private VxVoxelShapeUtil() {}

    public static MutableCompoundShapeSettings toMutableCompoundShape(VoxelShape voxelShape) {
        if (voxelShape == null || voxelShape.isEmpty()) {
            return null;
        }

        List<AABB> aabbs = voxelShape.toAabbs();
        if (aabbs.isEmpty()) {
            return null;
        }

        MutableCompoundShapeSettings compoundShape = new MutableCompoundShapeSettings();
        Quat identityRotation = Quat.sIdentity();

        try {
            for (AABB aabb : aabbs) {
                float hx = (float) (aabb.maxX - aabb.minX) / 2.0f;
                float hy = (float) (aabb.maxY - aabb.minY) / 2.0f;
                float hz = (float) (aabb.maxZ - aabb.minZ) / 2.0f;

                float cx = (float) (aabb.minX + hx - 0.5f);
                float cy = (float) (aabb.minY + hy - 0.5f);
                float cz = (float) (aabb.minZ + hz - 0.5f);

                Vec3 position = new Vec3(cx, cy, cz);
                Vec3 halfExtents = new Vec3(hx, hy, hz);

                try (BoxShapeSettings boxSettings = new BoxShapeSettings(halfExtents, 0.0f)) {
                    compoundShape.addShape(position, identityRotation, boxSettings);
                }
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Error converting VoxelShape to Jolt compound shape", e);
            compoundShape.close();
            return null;
        }

        return compoundShape;
    }
}