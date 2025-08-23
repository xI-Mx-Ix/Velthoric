package net.xmx.velthoric.physics.terrain.loader;

import com.github.stephengold.joltjni.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.terrain.cache.TerrainShapeCache;
import net.xmx.velthoric.physics.terrain.cache.TerrainStorage;

import java.util.*;

public final class TerrainGenerator {

    private final TerrainShapeCache shapeCache;
    private final TerrainStorage terrainStorage;
    private static final int SUBDIVISIONS = 16;
    private static final int MASK_DIM = 16 * SUBDIVISIONS;
    private static final float VOXEL_SIZE = 1.0f / SUBDIVISIONS;
    private static final float PRECISION = 1e5f;

    private float[] vertices = new float[4096 * 3];
    private int vertexCount = 0;
    private final ArrayList<Integer> triangles = new ArrayList<>(4096 * 2);

    private int[] vmapKeysX = new int[4096];
    private int[] vmapKeysY = new int[4096];
    private int[] vmapKeysZ = new int[4096];
    private int[] vmapValues = new int[4096];
    private int vmapSize = 0;

    public TerrainGenerator(TerrainShapeCache shapeCache, TerrainStorage terrainStorage) {
        this.shapeCache = shapeCache;
        this.terrainStorage = terrainStorage;
    }

    public synchronized ShapeRefC generateShape(ServerLevel level, ChunkSnapshot snapshot) {
        if (snapshot.shapes().isEmpty()) {
            return null;
        }

        int contentHash = computeContentHash(snapshot);

        ShapeRefC cached = shapeCache.get(contentHash);
        if (cached != null) {
            return cached;
        }

        ShapeRefC preloadedShape = terrainStorage.shapeFromPreload(contentHash);
        if (preloadedShape != null) {
            shapeCache.put(contentHash, preloadedShape.getPtr().toRefC());
            return preloadedShape;
        }

        return generateShapeFromVoxels(level, snapshot, contentHash);
    }

    private ShapeRefC generateShapeFromVoxels(ServerLevel level, ChunkSnapshot snapshot, int contentHash) {
        vertexCount = 0;
        triangles.clear();
        vmapSize = 0;

        @SuppressWarnings("unchecked")
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
                greedyMeshMask(mask, axis, dir, pos);
            }
        }

        if (vertexCount == 0 || triangles.isEmpty()) {
            return null;
        }

        VertexList vlist = new VertexList();
        try (IndexedTriangleList ilist = new IndexedTriangleList()) {
            vlist.resize(vertexCount);
            for (int i = 0; i < vertexCount; i++) {
                int baseIndex = i * 3;
                vlist.set(i, vertices[baseIndex], vertices[baseIndex + 1], vertices[baseIndex + 2]);
            }

            int triangleCount = triangles.size() / 3;
            ilist.resize(triangleCount);
            for (int i = 0; i < triangleCount; i++) {
                int i1 = triangles.get(i * 3);
                int i2 = triangles.get(i * 3 + 1);
                int i3 = triangles.get(i * 3 + 2);
                try (IndexedTriangle tri = new IndexedTriangle(i1, i2, i3)) {
                    ilist.set(i, tri);
                }
            }

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
        }
        return null;
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

            u0 = Math.max(0, u0);
            v0 = Math.max(0, v0);
            u1 = Math.min(MASK_DIM, u1);
            v1 = Math.min(MASK_DIM, v1);

            int keyNeg = (minPos << 1);
            boolean[][] maskNeg = facesByAxis[axis].computeIfAbsent(keyNeg, k -> new boolean[MASK_DIM][MASK_DIM]);
            for(int i = u0; i < u1; i++) for(int j = v0; j < v1; j++) maskNeg[i][j] = !maskNeg[i][j];

            int keyPos = (maxPos << 1) | 1;
            boolean[][] maskPos = facesByAxis[axis].computeIfAbsent(keyPos, k -> new boolean[MASK_DIM][MASK_DIM]);
            for(int i = u0; i < u1; i++) for(int j = v0; j < v1; j++) maskPos[i][j] = !maskPos[i][j];
        }
    }

    private void greedyMeshMask(boolean[][] mask, int axis, int dir, int pos) {
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

                addQuad(axis, dir, pos * VOXEL_SIZE, u * VOXEL_SIZE, v * VOXEL_SIZE, w * VOXEL_SIZE, h * VOXEL_SIZE);

                for (int du = 0; du < w; du++)
                    for (int dv = 0; dv < h; dv++)
                        mask[u + du][v + dv] = false;

                v += h;
            }
        }
    }

    private void addQuad(int axis, int dir, float a, float u, float v, float w, float h) {
        float[][] corners = new float[4][3];
        float[][] uv = {{u, v}, {u + w, v}, {u + w, v + h}, {u, v + h}};

        for (int i = 0; i < 4; i++) {
            corners[i][axis] = a;
            corners[i][(axis + 1) % 3] = uv[i][0];
            corners[i][(axis + 2) % 3] = uv[i][1];
        }

        int i1 = vid(corners[0][0], corners[0][1], corners[0][2]);
        int i2 = vid(corners[1][0], corners[1][1], corners[1][2]);
        int i3 = vid(corners[2][0], corners[2][1], corners[2][2]);
        int i4 = vid(corners[3][0], corners[3][1], corners[3][2]);

        if (dir > 0) {
            triangles.add(i1); triangles.add(i2); triangles.add(i3);
            triangles.add(i1); triangles.add(i3); triangles.add(i4);
        } else {
            triangles.add(i1); triangles.add(i3); triangles.add(i2);
            triangles.add(i1); triangles.add(i4); triangles.add(i3);
        }
    }

    private int vid(float x, float y, float z) {
        int ix = (int)(x * PRECISION);
        int iy = (int)(y * PRECISION);
        int iz = (int)(z * PRECISION);

        for (int i = 0; i < vmapSize; i++) {
            if (vmapKeysX[i] == ix && vmapKeysY[i] == iy && vmapKeysZ[i] == iz) {
                return vmapValues[i];
            }
        }

        if ((vertexCount + 1) * 3 > vertices.length) {
            vertices = Arrays.copyOf(vertices, Math.max(vertices.length * 2, (vertexCount + 1) * 3));
            int newCap = Math.max(vmapKeysX.length * 2, vertexCount + 1);
            vmapKeysX = Arrays.copyOf(vmapKeysX, newCap);
            vmapKeysY = Arrays.copyOf(vmapKeysY, newCap);
            vmapKeysZ = Arrays.copyOf(vmapKeysZ, newCap);
            vmapValues = Arrays.copyOf(vmapValues, newCap);
        }

        int baseIndex = vertexCount * 3;
        vertices[baseIndex] = x;
        vertices[baseIndex + 1] = y;
        vertices[baseIndex + 2] = z;

        int newIndex = vertexCount++;
        vmapKeysX[vmapSize] = ix;
        vmapKeysY[vmapSize] = iy;
        vmapKeysZ[vmapSize] = iz;
        vmapValues[vmapSize] = newIndex;
        vmapSize++;
        return newIndex;
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