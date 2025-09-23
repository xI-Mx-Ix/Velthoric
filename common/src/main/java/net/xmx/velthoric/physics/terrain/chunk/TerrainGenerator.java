/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.chunk;

import com.github.stephengold.joltjni.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.terrain.cache.TerrainShapeCache;
import net.xmx.velthoric.physics.terrain.cache.TerrainStorage;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * @author xI-Mx-Ix
 */
public final class TerrainGenerator {

    private final TerrainShapeCache shapeCache;
    private final TerrainStorage terrainStorage;
    private final Executor jobExecutor;
    private static final int SUBDIVISIONS = 16;
    private static final int MASK_DIM = 16 * SUBDIVISIONS;
    private static final float VOXEL_SIZE = 1.0f / SUBDIVISIONS;
    private static final float PRECISION = 1e5f;

    private static class MeshingContext {
        private static final int INITIAL_VERTICES_CAPACITY = 12288 * 3;
        private static final int INITIAL_TRIANGLES_CAPACITY = 12288 * 2 * 3;

        float[] vertices = new float[INITIAL_VERTICES_CAPACITY];
        int vertexCount = 0;

        int[] triangles = new int[INITIAL_TRIANGLES_CAPACITY];
        int triangleIndexCount = 0;

        final Map<Long, Integer> vertexMap = new HashMap<>(8192);

        void reset() {
            vertexCount = 0;
            triangleIndexCount = 0;
            vertexMap.clear();
        }

        private void ensureVertexCapacity(int requiredCount) {
            if (requiredCount * 3 > vertices.length) {
                vertices = Arrays.copyOf(vertices, Math.max(requiredCount * 3, vertices.length * 2));
            }
        }

        private void ensureTriangleCapacity(int requiredIndexCount) {
            if (requiredIndexCount > triangles.length) {
                triangles = Arrays.copyOf(triangles, Math.max(requiredIndexCount, triangles.length * 2));
            }
        }

        int addVertex(float x, float y, float z) {
            long key = ((long) (x * PRECISION) << 42) | ((long) (y * PRECISION) << 21) | (long) (z * PRECISION);
            Integer index = vertexMap.get(key);
            if (index != null) {
                return index;
            }

            ensureVertexCapacity(vertexCount + 1);
            int baseIndex = vertexCount * 3;
            vertices[baseIndex] = x;
            vertices[baseIndex + 1] = y;
            vertices[baseIndex + 2] = z;

            int newIndex = vertexCount++;
            vertexMap.put(key, newIndex);
            return newIndex;
        }
    }

    private final ThreadLocal<MeshingContext> MESHING_CONTEXT = ThreadLocal.withInitial(MeshingContext::new);

    public TerrainGenerator(TerrainShapeCache shapeCache, TerrainStorage terrainStorage, Executor jobExecutor) {
        this.shapeCache = shapeCache;
        this.terrainStorage = terrainStorage;
        this.jobExecutor = jobExecutor;
    }

    /**
     * Asynchronously generates or retrieves a physics shape for a chunk snapshot.
     * This process is fully non-blocking and follows a cache hierarchy:
     * 1. In-memory LRU cache (fastest)
     * 2. On-disk storage
     * 3. Full mesh generation (slowest)
     *
     * @param level    The server level, used for context.
     * @param snapshot The chunk data to process.
     * @return A CompletableFuture that will complete with the ShapeRefC, or null if the chunk is empty.
     */
    public CompletableFuture<@Nullable ShapeRefC> generateShape(ServerLevel level, ChunkSnapshot snapshot) {
        if (snapshot.shapes().isEmpty()) {
            terrainStorage.removeShape(snapshot.pos());
            return CompletableFuture.completedFuture(null);
        }

        int contentHash = computeContentHash(snapshot);

        // 1. Check in-memory cache
        ShapeRefC cached = shapeCache.get(contentHash);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        // 2. Asynchronously check disk storage and chain the next step
        return terrainStorage.getShape(snapshot.pos(), contentHash)
                .thenComposeAsync(storedShape -> {
                    if (storedShape != null) {
                        // Found on disk, put it in memory cache and return
                        shapeCache.put(contentHash, storedShape.getPtr().toRefC());
                        return CompletableFuture.completedFuture(storedShape);
                    } else {
                        // Not on disk, must generate a new one
                        return generateAndStoreShape(level, snapshot, contentHash);
                    }
                }, jobExecutor); // Continue the composition on the job system thread
    }

    /**
     * Generates a new shape, stores it to disk, caches it in memory, and returns it.
     */
    private CompletableFuture<@Nullable ShapeRefC> generateAndStoreShape(ServerLevel level, ChunkSnapshot snapshot, int contentHash) {
        return CompletableFuture.supplyAsync(() -> generateShapeFromVoxels(level, snapshot), jobExecutor)
                .thenApply(generatedShape -> {
                    if (generatedShape != null) {
                        // Store on disk (fire-and-forget)
                        terrainStorage.storeShape(snapshot.pos(), contentHash, generatedShape);
                        // Put in memory cache
                        shapeCache.put(contentHash, generatedShape.getPtr().toRefC());
                        // Return a new reference for the caller, and close the one we created
                        ShapeRefC returnShape = generatedShape.getPtr().toRefC();
                        generatedShape.close();
                        return returnShape;
                    } else {
                        terrainStorage.removeShape(snapshot.pos());
                        return null;
                    }
                });
    }

    // --- Meshing logic (unchanged) ---

    private ShapeRefC generateShapeFromVoxels(ServerLevel level, ChunkSnapshot snapshot) {
        MeshingContext context = MESHING_CONTEXT.get();
        context.reset();
        @SuppressWarnings("unchecked") Map<Integer, BitSet>[] facesByAxisAndDir = new Map[3];
        for (int i = 0; i < 3; i++) facesByAxisAndDir[i] = new HashMap<>();
        for (ChunkSnapshot.ShapeInfo info : snapshot.shapes()) {
            BlockPos worldPos = snapshot.pos().getOrigin().offset(info.localPos());
            VoxelShape voxelShape = info.state().getCollisionShape(level, worldPos);
            for (AABB aabb : voxelShape.toAabbs()) {
                extractFacesToMasks(info.localPos(), aabb, facesByAxisAndDir);
            }
        }
        for (int axis = 0; axis < 3; axis++) {
            for (Map.Entry<Integer, BitSet> entry : facesByAxisAndDir[axis].entrySet()) {
                greedyMeshMask(entry.getValue(), axis, (entry.getKey() & 1) == 0 ? -1 : 1, entry.getKey() >> 1, context);
            }
        }
        if (context.vertexCount == 0 || context.triangleIndexCount == 0) return null;
        ShapeRefC resultShape = null;
        VertexList vlist = new VertexList();
        try (IndexedTriangleList ilist = new IndexedTriangleList()) {
            vlist.resize(context.vertexCount);
            for (int i = 0; i < context.vertexCount; i++) vlist.set(i, context.vertices[i * 3], context.vertices[i * 3 + 1], context.vertices[i * 3 + 2]);
            int triangleCount = context.triangleIndexCount / 3;
            ilist.resize(triangleCount);
            for (int i = 0; i < triangleCount; i++) {
                try (IndexedTriangle tri = new IndexedTriangle(context.triangles[i * 3], context.triangles[i * 3 + 1], context.triangles[i * 3 + 2])) {
                    ilist.set(i, tri);
                }
            }
            try (MeshShapeSettings mss = new MeshShapeSettings(vlist, ilist); ShapeResult res = mss.create()) {
                if (res.isValid()) resultShape = res.get();
                else VxMainClass.LOGGER.error("Failed to create terrain mesh for {}: {}", snapshot.pos(), res.getError());
            }
        }
        return resultShape;
    }

    private void extractFacesToMasks(BlockPos localPos, AABB aabb, Map<Integer, BitSet>[] facesByAxis) {
        float[] min = {(float) (localPos.getX() + aabb.minX), (float) (localPos.getY() + aabb.minY), (float) (localPos.getZ() + aabb.minZ)};
        float[] max = {(float) (localPos.getX() + aabb.maxX), (float) (localPos.getY() + aabb.maxY), (float) (localPos.getZ() + aabb.maxZ)};

        for (int axis = 0; axis < 3; axis++) {
            int u = (axis + 1) % 3;
            int v = (axis + 2) % 3;

            int minPos = Math.round(min[axis] * SUBDIVISIONS);
            int maxPos = Math.round(max[axis] * SUBDIVISIONS);

            int u0 = Math.max(0, Math.round(min[u] * SUBDIVISIONS));
            int v0 = Math.max(0, Math.round(min[v] * SUBDIVISIONS));
            int u1 = Math.min(MASK_DIM, Math.round(max[u] * SUBDIVISIONS));
            int v1 = Math.min(MASK_DIM, Math.round(max[v] * SUBDIVISIONS));

            int keyNeg = (minPos << 1);
            BitSet maskNeg = facesByAxis[axis].computeIfAbsent(keyNeg, k -> new BitSet(MASK_DIM * MASK_DIM));
            for (int i = u0; i < u1; i++) {
                maskNeg.flip(i * MASK_DIM + v0, i * MASK_DIM + v1);
            }

            int keyPos = (maxPos << 1) | 1;
            BitSet maskPos = facesByAxis[axis].computeIfAbsent(keyPos, k -> new BitSet(MASK_DIM * MASK_DIM));
            for (int i = u0; i < u1; i++) {
                maskPos.flip(i * MASK_DIM + v0, i * MASK_DIM + v1);
            }
        }
    }

    private void greedyMeshMask(BitSet mask, int axis, int dir, int pos, MeshingContext context) {
        for (int u = 0; u < MASK_DIM; u++) {
            for (int v = 0; v < MASK_DIM; ) {
                if (!mask.get(u * MASK_DIM + v)) {
                    v++;
                    continue;
                }

                int w = 1;
                while (u + w < MASK_DIM && mask.get((u + w) * MASK_DIM + v)) {
                    w++;
                }

                int h = 1;
                outer:
                while (v + h < MASK_DIM) {
                    for (int k = 0; k < w; k++) {
                        if (!mask.get((u + k) * MASK_DIM + v + h)) {
                            break outer;
                        }
                    }
                    h++;
                }

                addQuad(axis, dir, pos * VOXEL_SIZE, u * VOXEL_SIZE, v * VOXEL_SIZE, w * VOXEL_SIZE, h * VOXEL_SIZE, context);

                for (int du = 0; du < w; du++) {
                    mask.clear((u + du) * MASK_DIM + v, (u + du) * MASK_DIM + v + h);
                }

                v += h;
            }
        }
    }

    private void addQuad(int axis, int dir, float a, float u, float v, float w, float h, MeshingContext context) {
        float[][] corners = new float[4][3];
        float[][] uv = {{u, v}, {u + w, v}, {u + w, v + h}, {u, v + h}};

        for (int i = 0; i < 4; i++) {
            corners[i][axis] = a;
            corners[i][(axis + 1) % 3] = uv[i][0];
            corners[i][(axis + 2) % 3] = uv[i][1];
        }

        int i1 = context.addVertex(corners[0][0], corners[0][1], corners[0][2]);
        int i2 = context.addVertex(corners[1][0], corners[1][1], corners[1][2]);
        int i3 = context.addVertex(corners[2][0], corners[2][1], corners[2][2]);
        int i4 = context.addVertex(corners[3][0], corners[3][1], corners[3][2]);

        context.ensureTriangleCapacity(context.triangleIndexCount + 6);

        if (dir > 0) {
            context.triangles[context.triangleIndexCount++] = i1;
            context.triangles[context.triangleIndexCount++] = i2;
            context.triangles[context.triangleIndexCount++] = i3;
            context.triangles[context.triangleIndexCount++] = i1;
            context.triangles[context.triangleIndexCount++] = i3;
            context.triangles[context.triangleIndexCount++] = i4;
        } else {
            context.triangles[context.triangleIndexCount++] = i1;
            context.triangles[context.triangleIndexCount++] = i3;
            context.triangles[context.triangleIndexCount++] = i2;
            context.triangles[context.triangleIndexCount++] = i1;
            context.triangles[context.triangleIndexCount++] = i4;
            context.triangles[context.triangleIndexCount++] = i3;
        }
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
