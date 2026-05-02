/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.terrain.generation;

import com.github.stephengold.joltjni.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.velthoric.core.terrain.material.VxTerrainMaterial;
import net.xmx.velthoric.jni.TerrainGenerator;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Generates physics shapes for terrain chunks using a native greedy mesher.
 * This class interfaces directly with the native C++ generator which builds
 * and caches optimized Jolt StaticCompoundShapes natively, significantly improving performance
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
     * A thread-local, direct byte buffer used to efficiently pass the block bounding boxes to native C++.
     * Using a DirectByteBuffer avoids costly array copying during the JNI transition.
     * Stores up to 32768 BoxShapeData structs (28 bytes each), so size is 32768 * 28 = 917504 bytes.
     */
    private static final ThreadLocal<ByteBuffer> shapeBuffer = ThreadLocal.withInitial(() -> {
        ByteBuffer buf = Jolt.newDirectByteBuffer(917504);
        buf.order(ByteOrder.nativeOrder());
        return buf;
    });

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

        ByteBuffer boxes = shapeBuffer.get();
        boxes.clear();

        int boxCount = 0;
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

            int materialId = VxTerrainMaterial.getMaterialId(snapshot.states()[i].getBlock());

            for (AABB aabb : voxelShape.toAabbs()) {
                float hx = (float) (aabb.getXsize() / 2.0);
                float hy = (float) (aabb.getYsize() / 2.0);
                float hz = (float) (aabb.getZsize() / 2.0);

                if (hx <= 0.001f || hy <= 0.001f || hz <= 0.001f) {
                    continue;
                }

                // Calculate local position relative to section origin for the compound shape
                float cx = (float) (x + aabb.minX + hx);
                float cy = (float) (y + aabb.minY + hy);
                float cz = (float) (z + aabb.minZ + hz);

                // Ensure we do not overflow the buffer limit
                if (boxCount >= 32768) {
                    break;
                }

                boxes.putFloat(cx);
                boxes.putFloat(cy);
                boxes.putFloat(cz);
                boxes.putFloat(hx);
                boxes.putFloat(hy);
                boxes.putFloat(hz);
                boxes.putInt(materialId);
                
                boxCount++;
            }
        }

        if (boxCount == 0) {
            return false;
        }

        return TerrainGenerator.nGenerateAndCache(contentHash, boxes, boxCount);
    }

    /**
     * Closes the generator and releases resources held by its native caches.
     */
    @Override
    public void close() {
        TerrainGenerator.nClearCache();
    }
}