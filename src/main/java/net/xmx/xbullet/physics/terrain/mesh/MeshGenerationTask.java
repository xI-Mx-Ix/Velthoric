package net.xmx.xbullet.physics.terrain.mesh;

import com.github.stephengold.joltjni.EmptyShapeSettings;
import com.github.stephengold.joltjni.Float3;
import com.github.stephengold.joltjni.IndexedTriangle;
import com.github.stephengold.joltjni.IndexedTriangleList;
import com.github.stephengold.joltjni.MeshShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class MeshGenerationTask implements Supplier<ShapeSettings> {

    private final BlockState[] blockStates;
    private Map<BlockPos, Integer> vertexIndexMap;

    public MeshGenerationTask(BlockState[] blockStates) {
        if (blockStates.length != 4096) {
            throw new IllegalArgumentException("BlockState array must have a size of 4096 (16x16x16)");
        }
        this.blockStates = blockStates;
    }

    @Override
    public ShapeSettings get() {
        this.vertexIndexMap = new HashMap<>();
        List<Float3> vertices = new ArrayList<>();

        try (IndexedTriangleList triangles = new IndexedTriangleList()) {
            for (int d = 0; d < 3; ++d) {
                for (boolean backFace : new boolean[]{true, false}) {
                    generateGreedyMeshForDirection(d, backFace, vertices, triangles);
                }
            }

            if (triangles.empty()) {
                return new EmptyShapeSettings();
            }

            return new MeshShapeSettings(vertices, triangles);

        } finally {
            this.vertexIndexMap = null;
        }
    }

    private int getBlockIndex(int x, int y, int z) {
        return (y * 16 + z) * 16 + x;
    }

    private BlockState getBlockState(int x, int y, int z) {
        if (x < 0 || x >= 16 || y < 0 || y >= 16 || z < 0 || z >= 16) {
            return Blocks.AIR.defaultBlockState();
        }
        return blockStates[getBlockIndex(x, y, z)];
    }

    private void generateGreedyMeshForDirection(int d, boolean backFace, List<Float3> vertices, IndexedTriangleList triangles) {
        int u = (d + 1) % 3;
        int v = (d + 2) % 3;
        int[] p1 = new int[3];
        int[] p2 = new int[3];
        boolean[][] mask = new boolean[16][16];

        for (int i = -1; i < 16; ++i) {
            for (int j = 0; j < 16; ++j) {
                for (int k = 0; k < 16; ++k) {
                    setCoords(p1, d, i, u, j, v, k);
                    setCoords(p2, d, i + 1, u, j, v, k);
                    BlockState s1 = getBlockState(p1[0], p1[1], p1[2]);
                    BlockState s2 = getBlockState(p2[0], p2[1], p2[2]);
                    mask[j][k] = backFace ? (!s1.isAir() && s2.isAir()) : (s1.isAir() && !s2.isAir());
                }
            }
            for (int j = 0; j < 16; ++j) {
                for (int k = 0; k < 16; ) {
                    if (mask[j][k]) {
                        int w = 1;
                        while (k + w < 16 && mask[j][k + w]) w++;
                        int h = 1;
                        outer:
                        while (j + h < 16) {
                            for (int l = 0; l < w; ++l) {
                                if (!mask[j + h][k + l]) break outer;
                            }
                            h++;
                        }
                        int slicePos = i + 1;
                        int[] du = new int[3];
                        setCoords(du, d, 0, u, h, v, 0);
                        int[] dv = new int[3];
                        setCoords(dv, d, 0, u, 0, v, w);
                        int[] v1_coords = new int[3];
                        setCoords(v1_coords, d, slicePos, u, j, v, k);
                        BlockPos v1 = new BlockPos(v1_coords[0], v1_coords[1], v1_coords[2]);
                        BlockPos v2 = v1.offset(dv[0], dv[1], dv[2]);
                        BlockPos v3 = v1.offset(du[0] + dv[0], du[1] + dv[1], du[2] + dv[2]);
                        BlockPos v4 = v1.offset(du[0], du[1], du[2]);
                        if (backFace) {
                            addQuad(v4, v3, v2, v1, vertices, triangles);
                        } else {
                            addQuad(v1, v2, v3, v4, vertices, triangles);
                        }
                        for (int l = 0; l < h; ++l) for (int m = 0; m < w; ++m) mask[j + l][k + m] = false;
                        k += w;
                    } else {
                        k++;
                    }
                }
            }
        }
    }

    private void addQuad(BlockPos p1, BlockPos p2, BlockPos p3, BlockPos p4, List<Float3> vertices, IndexedTriangleList triangles) {
        int i1 = getOrAddVertex(p1, vertices);
        int i2 = getOrAddVertex(p2, vertices);
        int i3 = getOrAddVertex(p3, vertices);
        int i4 = getOrAddVertex(p4, vertices);

        try (IndexedTriangle t1 = new IndexedTriangle(i1, i2, i3);
             IndexedTriangle t2 = new IndexedTriangle(i1, i3, i4)) {
            triangles.pushBack(t1);
            triangles.pushBack(t2);
        }
    }

    private int getOrAddVertex(BlockPos localPos, List<Float3> vertices) {
        return vertexIndexMap.computeIfAbsent(localPos.immutable(), p -> {
            float localX = p.getX() - 8.0f;
            float localY = p.getY() - 8.0f;
            float localZ = p.getZ() - 8.0f;
            vertices.add(new Float3(localX, localY, localZ));
            return vertices.size() - 1;
        });
    }

    private void setCoords(int[] coords, int d, int i, int u, int j, int v, int k) {
        coords[d] = i;
        coords[u] = j;
        coords[v] = k;
    }
}