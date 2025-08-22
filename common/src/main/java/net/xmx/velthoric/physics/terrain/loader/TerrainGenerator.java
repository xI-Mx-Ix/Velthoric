package net.xmx.velthoric.physics.terrain.loader;

import com.github.stephengold.joltjni.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.terrain.cache.TerrainShapeCache;

import java.util.*;

public final class TerrainGenerator {

    private final TerrainShapeCache shapeCache;

    private static final int SUBDIVISIONS = 16;
    private static final int MASK_DIM = 16 * SUBDIVISIONS;
    private static final float VOXEL_SIZE = 1.0f / SUBDIVISIONS;

    public TerrainGenerator(TerrainShapeCache shapeCache) {
        this.shapeCache = shapeCache;
    }

    public ShapeRefC generateShape(ServerLevel level, ChunkSnapshot snapshot) {
        if (snapshot.shapes().isEmpty()) {
            return null;
        }

        int contentHash = computeContentHash(snapshot);
        ShapeRefC cached = shapeCache.get(contentHash);
        if (cached != null) {
            return cached;
        }

        final ArrayList<Float3> vertices = new ArrayList<>();
        final ArrayList<IndexedTriangle> triangles = new ArrayList<>();
        final Map<VecKey, Integer> vmap = new HashMap<>(4096);

        Map<Integer, boolean[][]>[] facesByAxisAndDir = new Map[3];
        for (int i = 0; i < 3; i++) {
            facesByAxisAndDir[i] = new HashMap<>();
        }

        for (ChunkSnapshot.ShapeInfo info : snapshot.shapes()) {
            BlockPos worldPos = snapshot.pos().getOrigin().offset(info.localPos());
            VoxelShape voxelShape = info.state().getCollisionShape(level, worldPos);

            for (AABB aabb : voxelShape.toAabbs()) {
                extractFacesToMasks(info.localPos(), aabb, facesByAxisAndDir);
            }
        }

        for (int axis = 0; axis < 3; axis++) {
            for (Map.Entry<Integer, boolean[][]> entry : facesByAxisAndDir[axis].entrySet()) {
                int packedPosAndDir = entry.getKey();
                boolean[][] mask = entry.getValue();

                int dir = (packedPosAndDir & 1) == 0 ? -1 : 1;
                int pos = packedPosAndDir >> 1;

                greedyMeshMask(mask, axis, dir, pos, vertices, triangles, vmap);
            }
        }

        if (vertices.isEmpty() || triangles.isEmpty()) {
            return null;
        }

        VertexList vlist = new VertexList();
        IndexedTriangleList ilist = new IndexedTriangleList();
        {
            vlist.resize(vertices.size());
            for (int i = 0; i < vertices.size(); i++) vlist.set(i, vertices.get(i));

            ilist.resize(triangles.size());
            for (int i = 0; i < triangles.size(); i++) ilist.set(i, triangles.get(i));

            try (MeshShapeSettings mss = new MeshShapeSettings(vlist, ilist)) {
                try (ShapeResult res = mss.create()) {
                    if (res.isValid()) {
                        ShapeRefC master = res.get();
                        shapeCache.put(contentHash, master);
                        return master.getPtr().toRefC();
                    } else {
                        VxMainClass.LOGGER.error("Failed to create terrain mesh for {}: {}", snapshot.pos(), res.getError());
                    }
                }
            } finally {
                for (IndexedTriangle t : triangles) t.close();
            }
            return null;
        }
    }

    private void extractFacesToMasks(BlockPos localPos, AABB aabb, Map<Integer, boolean[][]>[] facesByAxis) {
        float[] min = {(float) (localPos.getX() + aabb.minX), (float) (localPos.getY() + aabb.minY), (float) (localPos.getZ() + aabb.minZ)};
        float[] max = {(float) (localPos.getX() + aabb.maxX), (float) (localPos.getY() + aabb.maxY), (float) (localPos.getZ() + aabb.maxZ)};

        for (int axis = 0; axis < 3; axis++) {
            int u = (axis + 1) % 3;
            int v = (axis + 2) % 3;

            int minPos = Math.round(min[axis] * SUBDIVISIONS);
            int maxPos = Math.round(max[axis] * SUBDIVISIONS);
            int u0 = Math.round(min[u] * SUBDIVISIONS);
            int v0 = Math.round(min[v] * SUBDIVISIONS);
            int u1 = Math.round(max[u] * SUBDIVISIONS);
            int v1 = Math.round(max[v] * SUBDIVISIONS);

            int keyNeg = (minPos << 1) | 0;
            boolean[][] maskNeg = facesByAxis[axis].computeIfAbsent(keyNeg, k -> new boolean[MASK_DIM][MASK_DIM]);
            for(int i = u0; i < u1; i++) for(int j = v0; j < v1; j++) maskNeg[i][j] = !maskNeg[i][j];

            int keyPos = (maxPos << 1) | 1;
            boolean[][] maskPos = facesByAxis[axis].computeIfAbsent(keyPos, k -> new boolean[MASK_DIM][MASK_DIM]);
            for(int i = u0; i < u1; i++) for(int j = v0; j < v1; j++) maskPos[i][j] = !maskPos[i][j];
        }
    }

    private void greedyMeshMask(
            boolean[][] mask, int axis, int dir, int pos,
            ArrayList<Float3> vertices, ArrayList<IndexedTriangle> triangles, Map<VecKey, Integer> vmap) {

        for (int u = 0; u < MASK_DIM; u++) {
            for (int v = 0; v < MASK_DIM; ) {
                if (!mask[u][v]) {
                    v++;
                    continue;
                }

                int w = 1;
                while (u + w < MASK_DIM && mask[u + w][v]) w++;

                int h = 1;
                outer:
                while (v + h < MASK_DIM) {
                    for (int k = 0; k < w; k++) {
                        if (!mask[u + k][v + h]) break outer;
                    }
                    h++;
                }

                addQuad(axis, dir, pos * VOXEL_SIZE, u * VOXEL_SIZE, v * VOXEL_SIZE, w * VOXEL_SIZE, h * VOXEL_SIZE, vertices, triangles, vmap);

                for (int du = 0; du < w; du++)
                    for (int dv = 0; dv < h; dv++)
                        mask[u + du][v + dv] = false;

                v += h;
            }
        }
    }

    private static void addQuad(
            int axis, int dir, float a, float u, float v, float w, float h,
            ArrayList<Float3> vertices, ArrayList<IndexedTriangle> triangles, Map<VecKey, Integer> vmap) {

        float[][] corners = new float[4][3];
        float[][] uv = {{u, v}, {u + w, v}, {u + w, v + h}, {u, v + h}};

        for (int i = 0; i < 4; i++) {
            corners[i][axis] = a;
            corners[i][(axis + 1) % 3] = uv[i][0];
            corners[i][(axis + 2) % 3] = uv[i][1];
        }

        int i1 = vid(vertices, vmap, corners[0][0], corners[0][1], corners[0][2]);
        int i2 = vid(vertices, vmap, corners[1][0], corners[1][1], corners[1][2]);
        int i3 = vid(vertices, vmap, corners[2][0], corners[2][1], corners[2][2]);
        int i4 = vid(vertices, vmap, corners[3][0], corners[3][1], corners[3][2]);

        if (dir > 0) {
            triangles.add(new IndexedTriangle(i1, i2, i3));
            triangles.add(new IndexedTriangle(i1, i3, i4));
        } else {
            triangles.add(new IndexedTriangle(i1, i3, i2));
            triangles.add(new IndexedTriangle(i1, i4, i3));
        }
    }

    private static int vid(ArrayList<Float3> vertices, Map<VecKey, Integer> vmap, float x, float y, float z) {
        VecKey key = new VecKey(x, y, z);
        Integer idx = vmap.get(key);
        if (idx != null) return idx;
        int ni = vertices.size();
        vertices.add(new Float3(x, y, z));
        vmap.put(key, ni);
        return ni;
    }

    private int computeContentHash(ChunkSnapshot snapshot) {
        List<Integer> hashes = new ArrayList<>(snapshot.shapes().size());
        for (ChunkSnapshot.ShapeInfo info : snapshot.shapes()) {
            hashes.add(Objects.hash(info.state().hashCode(), info.localPos().hashCode()));
        }
        Collections.sort(hashes);
        return hashes.hashCode();
    }

    private static final class VecKey {
        final float x, y, z;
        VecKey(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VecKey k)) return false;
            return Float.compare(k.x, x) == 0 && Float.compare(k.y, y) == 0 && Float.compare(k.z, z) == 0;
        }
        @Override public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }
}