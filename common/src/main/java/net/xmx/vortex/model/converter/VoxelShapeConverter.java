package net.xmx.vortex.model.converter;

import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.MutableCompoundShapeSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class VoxelShapeConverter {

    private static final float MIN_VOXEL_EXTENT = 1e-4f;

    private VoxelShapeConverter() {}

    @Nullable
    public static ShapeSettings convert(VoxelShape voxelShape) {
        if (voxelShape == null || voxelShape.isEmpty()) {
            return null;
        }

        if (voxelShape == Shapes.block()) {
            return new BoxShapeSettings(0.5f, 0.5f, 0.5f);
        }

        List<AABB> aabbs = voxelShape.toAabbs();
        if (aabbs.isEmpty()) {
            return null;
        }

        if (aabbs.size() == 1) {
            AABB box = aabbs.get(0);
            if (box.minX == 0 && box.minY == 0 && box.minZ == 0 && box.maxX == 1 && box.maxY == 1 && box.maxZ == 1) {
                return new BoxShapeSettings(0.5f, 0.5f, 0.5f);
            }
        }

        MutableCompoundShapeSettings compoundShapeSettings = new MutableCompoundShapeSettings();
        boolean hasChildren = false;

        for (AABB box : aabbs) {
            float halfX = (float) (box.getXsize() / 2.0);
            float halfY = (float) (box.getYsize() / 2.0);
            float halfZ = (float) (box.getZsize() / 2.0);

            if (halfX < MIN_VOXEL_EXTENT || halfY < MIN_VOXEL_EXTENT || halfZ < MIN_VOXEL_EXTENT) {
                continue;
            }

            try (BoxShapeSettings boxShapeSettings = new BoxShapeSettings(halfX, halfY, halfZ)) {
                float boxCenterX = (float) (box.minX + halfX);
                float boxCenterY = (float) (box.minY + halfY);
                float boxCenterZ = (float) (box.minZ + halfZ);

                Vec3 childPosition = new Vec3(boxCenterX - 0.5f, boxCenterY - 0.5f, boxCenterZ - 0.5f);

                compoundShapeSettings.addShape(childPosition, Quat.sIdentity(), boxShapeSettings);
                hasChildren = true;
            }
        }

        if (!hasChildren) {

            compoundShapeSettings.close();
            return null;
        }

        return compoundShapeSettings;
    }
}