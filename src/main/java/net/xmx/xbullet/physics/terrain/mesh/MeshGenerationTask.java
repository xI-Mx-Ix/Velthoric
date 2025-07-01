package net.xmx.xbullet.physics.terrain.mesh;

import com.github.stephengold.joltjni.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class MeshGenerationTask implements Supplier<ShapeSettings> {

    private static final int RESOLUTION = 16;
    private static final int SECTION_DIM = 16 * RESOLUTION;

    private final VoxelShape[] voxelShapes;
    private final boolean[][][] voxels;

    private final List<Float3> vertices = new ArrayList<>();
    private final IndexedTriangleList triangles = new IndexedTriangleList();
    private final Map<BlockPos, Integer> vertexIndexMap = new HashMap<>();

    public MeshGenerationTask(VoxelShape[] voxelShapes) {
        if (voxelShapes.length != 4096) {
            throw new IllegalArgumentException("VoxelShape array must have a size of 4096 (16x16x16)");
        }
        this.voxelShapes = voxelShapes;
        this.voxels = new boolean[SECTION_DIM][SECTION_DIM][SECTION_DIM];
    }

    @Override
    public ShapeSettings get() {
        try {

            voxelizeSectionOptimized();

            generateGreedyMesh();

            if (triangles.empty()) {
                return new EmptyShapeSettings();
            }

            return new MeshShapeSettings(vertices, triangles);
        } finally {
            triangles.close();
        }
    }

    private void voxelizeSectionOptimized() {
        for (int blockY = 0; blockY < 16; blockY++) {
            for (int blockZ = 0; blockZ < 16; blockZ++) {
                for (int blockX = 0; blockX < 16; blockX++) {
                    int blockIndex = (blockY * 16 + blockZ) * 16 + blockX;
                    VoxelShape shape = voxelShapes[blockIndex];
                    if (shape.isEmpty()) {
                        continue;
                    }

                    for (AABB aabb : shape.toAabbs()) {

                        int startX = (int) Math.floor(aabb.minX * RESOLUTION) + blockX * RESOLUTION;
                        int startY = (int) Math.floor(aabb.minY * RESOLUTION) + blockY * RESOLUTION;
                        int startZ = (int) Math.floor(aabb.minZ * RESOLUTION) + blockZ * RESOLUTION;
                        int endX = (int) Math.ceil(aabb.maxX * RESOLUTION) + blockX * RESOLUTION;
                        int endY = (int) Math.ceil(aabb.maxY * RESOLUTION) + blockY * RESOLUTION;
                        int endZ = (int) Math.ceil(aabb.maxZ * RESOLUTION) + blockZ * RESOLUTION;

                        for (int z = startZ; z < endZ; z++) {
                            for (int y = startY; y < endY; y++) {
                                for (int x = startX; x < endX; x++) {

                                    if (x >= 0 && x < SECTION_DIM && y >= 0 && y < SECTION_DIM && z >= 0 && z < SECTION_DIM) {
                                        voxels[x][y][z] = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void generateGreedyMesh() {

        for (int d = 0; d < 3; d++) {
            int u = (d + 1) % 3;
            int v = (d + 2) % 3;

            int[] x = new int[3];
            int[] q = new int[3];
            q[d] = 1;

            boolean[][] mask = new boolean[SECTION_DIM][SECTION_DIM];

            for (x[d] = -1; x[d] < SECTION_DIM; x[d]++) {

                for (x[u] = 0; x[u] < SECTION_DIM; x[u]++) {
                    for (x[v] = 0; x[v] < SECTION_DIM; x[v]++) {

                        boolean voxel1 = getVoxel(x[0], x[1], x[2]);
                        boolean voxel2 = getVoxel(x[0] + q[0], x[1] + q[1], x[2] + q[2]);

                        mask[x[u]][x[v]] = voxel1 != voxel2;
                    }
                }

                for (int j = 0; j < SECTION_DIM; j++) {
                    for (int k = 0; k < SECTION_DIM; ) {
                        if (mask[j][k]) {

                            int w = 1;
                            while (k + w < SECTION_DIM && mask[j][k + w]) {
                                w++;
                            }

                            int h = 1;
                            boolean done = false;
                            while (j + h < SECTION_DIM) {
                                for (int m = 0; m < w; m++) {
                                    if (!mask[j + h][k + m]) {
                                        done = true;
                                        break;
                                    }
                                }
                                if (done) break;
                                h++;
                            }

                            x[u] = j;
                            x[v] = k;
                            boolean isBackFace = !getVoxel(x[0], x[1], x[2]);

                            int[] du = new int[3]; du[u] = h;
                            int[] dv = new int[3]; dv[v] = w;

                            BlockPos v1 = new BlockPos(x[0] + q[0], x[1] + q[1], x[2] + q[2]);
                            BlockPos v2 = new BlockPos(x[0] + q[0] + dv[0], x[1] + q[1] + dv[1], x[2] + q[2] + dv[2]);
                            BlockPos v3 = new BlockPos(x[0] + q[0] + du[0] + dv[0], x[1] + q[1] + du[1] + dv[1], x[2] + q[2] + du[2] + dv[2]);
                            BlockPos v4 = new BlockPos(x[0] + q[0] + du[0], x[1] + q[1] + du[1], x[2] + q[2] + du[2]);

                            if (isBackFace) {
                                addQuad(v1, v2, v3, v4);
                            } else {
                                addQuad(v4, v3, v2, v1);
                            }

                            for (int l = 0; l < h; l++) {
                                for (int m = 0; m < w; m++) {
                                    mask[j + l][k + m] = false;
                                }
                            }
                            k += w;
                        } else {
                            k++;
                        }
                    }
                }
            }
        }
    }

    private boolean getVoxel(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= SECTION_DIM || y >= SECTION_DIM || z >= SECTION_DIM) {
            return false;
        }
        return voxels[x][y][z];
    }

    private void addQuad(BlockPos p1, BlockPos p2, BlockPos p3, BlockPos p4) {
        int i1 = getOrAddVertex(p1);
        int i2 = getOrAddVertex(p2);
        int i3 = getOrAddVertex(p3);
        int i4 = getOrAddVertex(p4);

        try (IndexedTriangle t1 = new IndexedTriangle(i1, i2, i3);
             IndexedTriangle t2 = new IndexedTriangle(i1, i3, i4)) {
            triangles.pushBack(t1);
            triangles.pushBack(t2);
        }
    }

    private int getOrAddVertex(BlockPos voxelPos) {
        return vertexIndexMap.computeIfAbsent(voxelPos.immutable(), p -> {
            float worldX = (float) p.getX() / RESOLUTION;
            float worldY = (float) p.getY() / RESOLUTION;
            float worldZ = (float) p.getZ() / RESOLUTION;

            float localX = worldX - 8.0f;
            float localY = worldY - 8.0f;
            float localZ = worldZ - 8.0f;

            vertices.add(new Float3(localX, localY, localZ));
            return vertices.size() - 1;
        });
    }
}