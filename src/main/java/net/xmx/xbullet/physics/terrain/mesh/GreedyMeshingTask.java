package net.xmx.xbullet.physics.terrain.mesh;

import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.MutableCompoundShapeSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.function.Supplier;

public class GreedyMeshingTask implements Supplier<MutableCompoundShapeSettings> {

    private final VoxelShape[] voxelShapes;
    private final int resolution;

    public GreedyMeshingTask(VoxelShape[] voxelShapes, int resolution) {
        if (voxelShapes.length != 4096) {
            throw new IllegalArgumentException("VoxelShape array must have a size of 4096 (16x16x16)");
        }
        this.voxelShapes = voxelShapes;
        this.resolution = resolution;
    }

    @Override
    public MutableCompoundShapeSettings get() {
        boolean[][][] mask = VoxelRasterizer.rasterize(this.voxelShapes, this.resolution);
        final int sectionVoxelSize = 16 * this.resolution;
        final float voxelWorldSize = 1.0f / this.resolution;

        MutableCompoundShapeSettings compoundSettings = new MutableCompoundShapeSettings();

        for (int y = 0; y < sectionVoxelSize; ++y) {
            for (int x = 0; x < sectionVoxelSize; ++x) {
                for (int z = 0; z < sectionVoxelSize; ) {
                    if (mask[x][y][z]) {
                        int w = 1;
                        while (z + w < sectionVoxelSize && mask[x][y][z + w]) w++;

                        int h = 1;
                        boolean done = false;
                        while (x + h < sectionVoxelSize) {
                            for (int k = 0; k < w; ++k) if (!mask[x + h][y][z + k]) { done = true; break; }
                            if (done) break;
                            h++;
                        }

                        int d = 1;
                        done = false;
                        while (y + d < sectionVoxelSize) {
                            for (int i = 0; i < h; ++i) for (int j = 0; j < w; ++j) if (!mask[x + i][y + d][z + j]) { done = true; break; }
                            if (done) break;
                            d++;
                        }

                        float boxWidth = h * voxelWorldSize * 0.5f;
                        float boxHeight = d * voxelWorldSize * 0.5f;
                        float boxDepth = w * voxelWorldSize * 0.5f;

                        try (BoxShape boxShape = new BoxShape(boxWidth, boxHeight, boxDepth)) {

                            float posX = (x + h * 0.5f) * voxelWorldSize - 8.0f;
                            float posY = (y + d * 0.5f) * voxelWorldSize - 8.0f;
                            float posZ = (z + w * 0.5f) * voxelWorldSize - 8.0f;

                            Vec3 position = new Vec3(posX, posY, posZ);
                            compoundSettings.addShape(position, Quat.sIdentity(), boxShape);
                        }

                        for (int l = 0; l < d; ++l) {
                            for (int i = 0; i < h; ++i) {
                                for (int j = 0; j < w; ++j) {
                                    mask[x + i][y + l][z + j] = false;
                                }
                            }
                        }
                        z += w;
                    } else {
                        z++;
                    }
                }
            }
        }
        return compoundSettings;
    }
}