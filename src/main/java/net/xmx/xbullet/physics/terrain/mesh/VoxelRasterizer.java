package net.xmx.xbullet.physics.terrain.mesh;

import net.minecraft.world.phys.shapes.VoxelShape;

public class VoxelRasterizer {

    /**
     * Konvertiert ein Array von VoxelShapes in eine 3D-Boolean-Maske.
     * @param blockShapes Das 16x16x16 Array der VoxelShapes der Section.
     * @param resolution Die Anzahl der Voxel pro Block-Achse (z.B. 2 für eine 32x32x32 Maske).
     * @return Eine boolean[][][] Maske der Gesamtgröße (16*resolution)^3.
     */
    public static boolean[][][] rasterize(VoxelShape[] blockShapes, int resolution) {
        final int sectionVoxelSize = 16 * resolution;
        boolean[][][] mask = new boolean[sectionVoxelSize][sectionVoxelSize][sectionVoxelSize];
        final double voxelSize = 1.0 / resolution;

        for (int by = 0; by < 16; ++by) {
            for (int bz = 0; bz < 16; ++bz) {
                for (int bx = 0; bx < 16; ++bx) {
                    VoxelShape blockShape = blockShapes[(by * 16 + bz) * 16 + bx];
                    if (blockShape.isEmpty()) {
                        continue;
                    }

                    for (int vy = 0; vy < resolution; ++vy) {
                        for (int vz = 0; vz < resolution; ++vz) {
                            for (int vx = 0; vx < resolution; ++vx) {
                                double voxelCenterX = (vx + 0.5) * voxelSize;
                                double voxelCenterY = (vy + 0.5) * voxelSize;
                                double voxelCenterZ = (vz + 0.5) * voxelSize;

                                if (blockShape.bounds().contains(voxelCenterX, voxelCenterY, voxelCenterZ)) {
                                    int maskX = bx * resolution + vx;
                                    int maskY = by * resolution + vy;
                                    int maskZ = bz * resolution + vz;
                                    mask[maskX][maskY][maskZ] = true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return mask;
    }
}