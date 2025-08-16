package net.xmx.vortex.physics.terrain.loader;

import com.github.stephengold.joltjni.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.terrain.cache.TerrainShapeCache;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
        ShapeRefC cached = shapeCache.get(contentHash);
        if (cached != null) {
            return cached;
        }

        final boolean[][][] solid = new boolean[16][16][16];
        for (ChunkSnapshot.ShapeInfo info : snapshot.shapes()) {
            BlockPos p = info.localPos();
            if ((p.getX() | p.getY() | p.getZ()) < 0 || p.getX() >= 16 || p.getY() >= 16 || p.getZ() >= 16) continue;
            solid[p.getX()][p.getY()][p.getZ()] = true;
        }

        if (isEmpty(solid)) return null;

        final ArrayList<Float3> vertices = new ArrayList<>();
        final ArrayList<IndexedTriangle> triangles = new ArrayList<>();
        final Map<VecKey, Integer> vmap = new HashMap<>(4096);

        try {

            greedyAxis(solid, 0, +1, vertices, triangles, vmap);
            greedyAxis(solid, 0, -1, vertices, triangles, vmap);
            greedyAxis(solid, 1, +1, vertices, triangles, vmap);
            greedyAxis(solid, 1, -1, vertices, triangles, vmap);
            greedyAxis(solid, 2, +1, vertices, triangles, vmap);
            greedyAxis(solid, 2, -1, vertices, triangles, vmap);

            if (vertices.isEmpty() || triangles.isEmpty()) return null;

            VertexList vlist = new VertexList();
            IndexedTriangleList ilist = new IndexedTriangleList();

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
            }
        } finally {
            for (IndexedTriangle t : triangles) t.close();
        }
        return null;
    }

    private static void greedyAxis(
            boolean[][][] solid,
            int axis,
            int dir,
            ArrayList<Float3> vertices,
            ArrayList<IndexedTriangle> triangles,
            Map<VecKey, Integer> vmap
    ) {
        final int U = (axis + 1) % 3;
        final int V = (axis + 2) % 3;

        final int[] dims = {16,16,16};
        final int aDim = dims[axis], uDim = dims[U], vDim = dims[V];

        final boolean[][] mask = new boolean[uDim][vDim];

        for (int a = 0; a <= aDim; a++) {

            for (int u = 0; u < uDim; u++) {
                for (int v = 0; v < vDim; v++) {
                    boolean s0 = voxel(solid, axis, a - 1, U, u, V, v);
                    boolean s1 = voxel(solid, axis, a,     U, u, V, v);

                    mask[u][v] = (dir > 0) ? (s0 && !s1) : (s1 && !s0);
                }
            }

            int u = 0;
            while (u < uDim) {
                int v = 0;
                while (v < vDim) {
                    if (!mask[u][v]) { v++; continue; }

                    int w = 1;
                    while (u + w < uDim && mask[u + w][v]) w++;

                    int h = 1;
                    outer:
                    while (v + h < vDim) {
                        for (int k = 0; k < w; k++) {
                            if (!mask[u + k][v + h]) break outer;
                        }
                        h++;
                    }

                    for (int du = 0; du < w; du++)
                        for (int dv = 0; dv < h; dv++)
                            mask[u + du][v + dv] = false;

                    addQuad(axis, dir, a, u, v, w, h, vertices, triangles, vmap);
                    v += h;
                }
                u++;
            }
        }
    }

    private static boolean voxel(
            boolean[][][] solid,
            int axis, int a,
            int U, int u,
            int V, int v
    ) {
        int x, y, z;
        int[] coords = new int[3];
        coords[axis] = a;
        coords[U]    = u;
        coords[V]    = v;
        x = coords[0]; y = coords[1]; z = coords[2];
        if (x < 0 || y < 0 || z < 0 || x >= 16 || y >= 16 || z >= 16) return false;
        return solid[x][y][z];
    }

    private static void addQuad(
            int axis, int dir, int a, int u, int v, int w, int h,
            ArrayList<Float3> vertices,
            ArrayList<IndexedTriangle> triangles,
            Map<VecKey, Integer> vmap
    ) {

        int[][] corners = new int[4][3];

        int[][] uv = {
                {u, v},
                {u + w, v},
                {u + w, v + h},
                {u, v + h}
        };

        for (int i = 0; i < 4; i++) {
            int[] c = new int[3];
            c[axis] = a;
            c[(axis + 1) % 3] = uv[i][0];
            c[(axis + 2) % 3] = uv[i][1];
            corners[i] = c;
        }

        if (dir < 0) {
            for (int i = 0; i < 4; i++) corners[i][axis] -= 1;
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

    private static int vid(ArrayList<Float3> vertices, Map<VecKey, Integer> vmap, int x, int y, int z) {
        VecKey key = new VecKey(x, y, z);
        Integer idx = vmap.get(key);
        if (idx != null) return idx;
        int ni = vertices.size();
        vertices.add(new Float3(x, y, z));
        vmap.put(key, ni);
        return ni;
    }

    private static boolean isEmpty(boolean[][][] solid) {
        for (int x = 0; x < 16; x++)
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    if (solid[x][y][z]) return false;
        return true;
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
        final int x, y, z;
        VecKey(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VecKey k)) return false;
            return x == k.x && y == k.y && z == k.z;
        }
        @Override public int hashCode() {

            return (x & 0x1F) | ((y & 0x1F) << 5) | ((z & 0x1F) << 10);
        }
    }
}