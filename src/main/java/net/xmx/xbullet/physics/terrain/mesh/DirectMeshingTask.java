package net.xmx.xbullet.physics.terrain.mesh;

import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.MutableCompoundShapeSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

public class DirectMeshingTask implements Supplier<MutableCompoundShapeSettings> {

    private final VoxelShape[] voxelShapes;

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
                    VoxelShape blockShape = this.voxelShapes[(y * 16 + z) * 16 + x];
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

                        float boxWidth = (float) (maxX - minX) * 0.5f;
                        float boxHeight = (float) (maxY - minY) * 0.5f;
                        float boxDepth = (float) (maxZ - minZ) * 0.5f;

                        try (BoxShape boxShape = new BoxShape(boxWidth, boxHeight, boxDepth)) {
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