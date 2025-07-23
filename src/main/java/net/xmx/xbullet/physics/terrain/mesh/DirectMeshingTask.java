package net.xmx.xbullet.physics.terrain.mesh;

import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.MutableCompoundShapeSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

public class DirectMeshingTask implements Supplier<MutableCompoundShapeSettings> {

    private final VoxelShape[] voxelShapes;

    private static final float MIN_HALF_EXTENT = Jolt.cDefaultConvexRadius;

    public DirectMeshingTask(VoxelShape[] voxelShapes) {
        if (voxelShapes.length != 4096) {
            throw new IllegalArgumentException("VoxelShape array must have a size of 4096 (16x16x16)");
        }
        this.voxelShapes = voxelShapes;
    }

    @Override
    public MutableCompoundShapeSettings get() {
        MutableCompoundShapeSettings compoundSettings = new MutableCompoundShapeSettings();

        final float sectionOffset = -8.0f;

        for (int y = 0; y < 16; ++y) {
            for (int z = 0; z < 16; ++z) {
                for (int x = 0; x < 16; ++x) {

                    int index = (y * 16 + z) * 16 + x;
                    VoxelShape blockShape = this.voxelShapes[index];

                    if (blockShape.isEmpty()) {
                        continue;
                    }

                    final float blockOffsetX = (float) x;
                    final float blockOffsetY = (float) y;
                    final float blockOffsetZ = (float) z;

                    blockShape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {

                        if (maxX <= minX || maxY <= minY || maxZ <= minZ) {
                            return;
                        }

                        float boxHalfWidth = (float) (maxX - minX) * 0.5f;
                        float boxHalfHeight = (float) (maxY - minY) * 0.5f;
                        float boxHalfDepth = (float) (maxZ - minZ) * 0.5f;

                        float validHalfWidth = Math.max(boxHalfWidth, MIN_HALF_EXTENT);
                        float validHalfHeight = Math.max(boxHalfHeight, MIN_HALF_EXTENT);
                        float validHalfDepth = Math.max(boxHalfDepth, MIN_HALF_EXTENT);

                        try (BoxShape boxShape = new BoxShape(new Vec3(validHalfWidth, validHalfHeight, validHalfDepth), 0.02f)) {

                            float posX = blockOffsetX + (float) (minX + maxX) * 0.5f + sectionOffset;
                            float posY = blockOffsetY + (float) (minY + maxY) * 0.5f + sectionOffset;
                            float posZ = blockOffsetZ + (float) (minZ + maxZ) * 0.5f + sectionOffset;

                            compoundSettings.addShape(new Vec3(posX, posY, posZ), Quat.sIdentity(), boxShape);
                        }
                    });
                }
            }
        }
        return compoundSettings;
    }
}