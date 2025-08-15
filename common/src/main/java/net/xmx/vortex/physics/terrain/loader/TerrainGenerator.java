package net.xmx.vortex.physics.terrain.loader;

import com.github.stephengold.joltjni.Float3;
import com.github.stephengold.joltjni.IndexedTriangle;
import com.github.stephengold.joltjni.IndexedTriangleList;
import com.github.stephengold.joltjni.MeshShapeSettings;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.VertexList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.terrain.cache.TerrainShapeCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TerrainGenerator {

    private final TerrainShapeCache shapeCache;

    public TerrainGenerator(TerrainShapeCache shapeCache) {
        this.shapeCache = shapeCache;
    }

    public ShapeRefC generateShape(ServerLevel level, ChunkSnapshot snapshot) {
        if (snapshot.shapes().isEmpty()) {
            return null;
        }

        int contentHash = computeContentHash(snapshot);
        ShapeRefC shapeFromCache = shapeCache.get(contentHash);
        if (shapeFromCache != null) {
            return shapeFromCache;
        }

        boolean[][][] solidGrid = new boolean[16][16][16];
        for (ChunkSnapshot.ShapeInfo info : snapshot.shapes()) {
            BlockPos pos = info.localPos();
            if (pos.getX() >= 0 && pos.getX() < 16 && pos.getY() >= 0 && pos.getY() < 16 && pos.getZ() >= 0 && pos.getZ() < 16) {
                solidGrid[pos.getX()][pos.getY()][pos.getZ()] = true;
            }
        }

        List<Float3> vertices = new ArrayList<>();
        List<IndexedTriangle> triangles = new ArrayList<>();
        Map<Float3, Integer> vertexMap = new HashMap<>();

        try {
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        if (!solidGrid[x][y][z]) {
                            continue;
                        }

                        // +X (East)
                        if (x == 15 || !solidGrid[x + 1][y][z]) {
                            addFace(vertices, triangles, vertexMap, new Float3(x + 1, y, z), new Float3(x + 1, y + 1, z), new Float3(x + 1, y + 1, z + 1), new Float3(x + 1, y, z + 1));
                        }
                        // -X (West)
                        if (x == 0 || !solidGrid[x - 1][y][z]) {
                            addFace(vertices, triangles, vertexMap, new Float3(x, y, z + 1), new Float3(x, y + 1, z + 1), new Float3(x, y + 1, z), new Float3(x, y, z));
                        }
                        // +Y (Up)
                        if (y == 15 || !solidGrid[x][y + 1][z]) {
                            addFace(vertices, triangles, vertexMap, new Float3(x, y + 1, z), new Float3(x, y + 1, z + 1), new Float3(x + 1, y + 1, z + 1), new Float3(x + 1, y + 1, z));
                        }
                        // -Y (Down)
                        if (y == 0 || !solidGrid[x][y - 1][z]) {
                            addFace(vertices, triangles, vertexMap, new Float3(x + 1, y, z), new Float3(x + 1, y, z + 1), new Float3(x, y, z + 1), new Float3(x, y, z));
                        }
                        // +Z (South)
                        if (z == 15 || !solidGrid[x][y][z + 1]) {
                            addFace(vertices, triangles, vertexMap, new Float3(x + 1, y, z + 1), new Float3(x + 1, y + 1, z + 1), new Float3(x, y + 1, z + 1), new Float3(x, y, z + 1));
                        }
                        // -Z (North)
                        if (z == 0 || !solidGrid[x][y][z - 1]) {
                            addFace(vertices, triangles, vertexMap, new Float3(x, y, z), new Float3(x, y + 1, z), new Float3(x + 1, y + 1, z), new Float3(x + 1, y, z));
                        }
                    }
                }
            }

            if (vertices.isEmpty() || triangles.isEmpty()) {
                return null;
            }

            VertexList vertexList = new VertexList();
            IndexedTriangleList triangleList = new IndexedTriangleList();

            vertexList.resize(vertices.size());
            for (int i = 0; i < vertices.size(); i++) {
                vertexList.set(i, vertices.get(i));
            }

            triangleList.resize(triangles.size());
            for (int i = 0; i < triangles.size(); i++) {
                triangleList.set(i, triangles.get(i));
            }

            try (MeshShapeSettings meshSettings = new MeshShapeSettings(vertexList, triangleList)) {
                try (ShapeResult result = meshSettings.create()) {
                    if (result.isValid()) {
                        ShapeRefC masterRef = result.get();
                        shapeCache.put(contentHash, masterRef);
                        return masterRef.getPtr().toRefC();
                    } else {
                        VxMainClass.LOGGER.error("Failed to create terrain mesh for {}: {}", snapshot.pos(), result.getError());
                    }
                }
            }
        } finally {
            for (IndexedTriangle triangle : triangles) {
                triangle.close();
            }
        }
        return null;
    }

    private void addFace(List<Float3> vertices, List<IndexedTriangle> triangles, Map<Float3, Integer> vertexMap, Float3 v1, Float3 v2, Float3 v3, Float3 v4) {
        int i1 = getVertexIndex(vertices, vertexMap, v1);
        int i2 = getVertexIndex(vertices, vertexMap, v2);
        int i3 = getVertexIndex(vertices, vertexMap, v3);
        int i4 = getVertexIndex(vertices, vertexMap, v4);

        triangles.add(new IndexedTriangle(i1, i2, i3));
        triangles.add(new IndexedTriangle(i1, i3, i4));
    }

    private int getVertexIndex(List<Float3> vertices, Map<Float3, Integer> vertexMap, Float3 vertex) {
        return vertexMap.computeIfAbsent(vertex, v -> {
            vertices.add(v);
            return vertices.size() - 1;
        });
    }

    private int computeContentHash(ChunkSnapshot snapshot) {
        List<Integer> hashes = new ArrayList<>(snapshot.shapes().size());
        for (ChunkSnapshot.ShapeInfo info : snapshot.shapes()) {
            hashes.add(Objects.hash(info.state().hashCode(), info.localPos().hashCode()));
        }
        Collections.sort(hashes);
        return hashes.hashCode();
    }
}