/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.terrain.generation;

import com.github.stephengold.joltjni.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.velthoric.core.terrain.material.VxTerrainMaterial;
import net.xmx.velthoric.jni.TerrainMesher;

import java.nio.ByteBuffer;

/**
 * Generates physics shapes for terrain chunks using a native greedy mesher.
 * This class interfaces directly with the native C++ mesher which generates
 * and caches optimized Jolt shapes natively, significantly improving performance
 * and eliminating JNI overhead. No Shape references are held in Java.
 *
 * @author xI-Mx-Ix
 */
public final class VxTerrainGenerator implements AutoCloseable {

    /**
     * Constructs a new native terrain generator.
     */
    public VxTerrainGenerator() {
    }

    /**
     * A thread-local, direct byte buffer used to efficiently pass the 16x16x16 voxel grid to native C++.
     * Using a DirectByteBuffer avoids costly array copying during the JNI transition.
     * Stores 16-bit material IDs (4096 voxels * 2 bytes = 8192 bytes).
     */
    private static final ThreadLocal<ByteBuffer> voxelBuffer = ThreadLocal.withInitial(() -> 
        Jolt.newDirectByteBuffer(8192)
    );

    /**
     * Generates a native mesh for the given chunk snapshot and caches it in C++.
     * <p>
     * This method offloads the shape generation to native C++ code using a greedy meshing algorithm
     * on a 16x16x16 voxel grid. Data is passed efficiently via a DirectByteBuffer to minimize JNI overhead.
     * </p>
     *
     * @param level       The server level, used to get context-aware collision shapes.
     * @param snapshot    An immutable snapshot of the chunk section's block data.
     * @param contentHash The unique hash of the snapshot used for native caching.
     * @return {@code true} if the chunk has valid shapes (solid), {@code false} if it's completely empty.
     */
    public boolean generateShape(ServerLevel level, VxChunkSnapshot snapshot, int contentHash) {
        if (snapshot.count() == 0) {
            return false;
        }

        ByteBuffer voxels = voxelBuffer.get();
        // Clear buffer efficiently (8192 bytes / 8 = 1024 longs)
        for (int i = 0; i < 1024; i++) {
            voxels.putLong(i * 8, 0L);
        }

        boolean hasShapes = false;
        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();

        // Extract the origin of the section from the bit-packed long coordinate.
        long packedPos = snapshot.packedSectionPos();
        int originX = SectionPos.sectionToBlockCoord(SectionPos.x(packedPos));
        int originY = SectionPos.sectionToBlockCoord(SectionPos.y(packedPos));
        int originZ = SectionPos.sectionToBlockCoord(SectionPos.z(packedPos));

        // Iterate using the count and primitive arrays.
        for (int i = 0; i < snapshot.count(); i++) {
            short packed = snapshot.packedPositions()[i];
            // Unpack coordinates
            int x = (packed >> 8) & 0xF;
            int y = (packed >> 4) & 0xF;
            int z = packed & 0xF;

            worldPos.set(originX + x, originY + y, originZ + z);
            VoxelShape voxelShape = snapshot.states()[i].getCollisionShape(level, worldPos);

            if (voxelShape.isEmpty()) continue;

            short materialId = VxTerrainMaterial.getMaterialId(snapshot.states()[i].getBlock());
            int index = x | (z << 4) | (y << 8);
            voxels.putShort(index * 2, materialId);
            hasShapes = true;
        }

        if (!hasShapes) {
            return false;
        }

        return TerrainMesher.nGenerateAndCache(contentHash, voxels);
    }

    /**
     * Closes the generator and releases resources held by its native caches.
     */
    @Override
    public void close() {
        TerrainMesher.nClearCache();
    }
}