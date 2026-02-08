/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.network.internal;

import com.github.luben.zstd.Zstd;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.core.body.manager.VxBodyManager;
import net.xmx.velthoric.core.body.manager.VxServerBodyDataStore;
import net.xmx.velthoric.core.network.internal.packet.S2CUpdateBodyStateBatchPacket;
import net.xmx.velthoric.core.network.internal.packet.S2CUpdateVerticesBatchPacket;

import java.nio.ByteBuffer;

/**
 * A factory for creating high-performance network packets with zero-allocation strategies.
 * <p>
 * This class utilizes Netty's {@link PooledByteBufAllocator} to acquire direct memory buffers,
 * writes raw data into them, performs Zstd compression directly using NIO buffers,
 * and packages the result into packets without creating intermediate byte arrays.
 * <p>
 * This architecture is essential for handling 10k+ physics bodies, as it completely avoids
 * Java Heap allocations during the serialization and compression phase, relying instead
 * on off-heap direct memory which significantly reduces Garbage Collection pressure.
 *
 * @author xI-Mx-Ix
 */
public class VxPacketFactory {

    /**
     * The default Netty allocator used to pool direct buffers.
     * Direct buffers are allocated outside the Java Heap.
     */
    private static final ByteBufAllocator ALLOCATOR = PooledByteBufAllocator.DEFAULT;

    /**
     * Standard Zstd compression level. Level 3 offers a good balance between
     * CPU usage and compression ratio for real-time physics data.
     */
    private static final int ZSTD_COMPRESSION_LEVEL = 3;

    /**
     * The physics body manager containing the global body list.
     */
    private final VxBodyManager manager;

    /**
     * The raw data store containing Structure-of-Arrays (SoA) physics data.
     */
    private final VxServerBodyDataStore dataStore;

    /**
     * Constructs the packet factory.
     *
     * @param manager The physics body manager.
     */
    public VxPacketFactory(VxBodyManager manager) {
        this.manager = manager;
        this.dataStore = manager.getDataStore();
    }

    /**
     * Creates a compressed state update packet for a specific chunk using direct buffers.
     * <p>
     * This method:
     * 1. Acquires a pooled direct ByteBuf.
     * 2. Writes the raw physics state (pos, rot, vel) into the buffer.
     * 3. Compresses the buffer into a new pooled direct ByteBuf using Zstd.
     * 4. Releases the raw buffer.
     * 5. Returns a packet containing the compressed buffer.
     *
     * @param chunkPosLong The chunk position key.
     * @param indices      The indices of the bodies to serialize.
     * @param serverLevel  The server level (used for calculating relative coordinates).
     * @return The constructed packet containing the compressed buffer.
     */
    public S2CUpdateBodyStateBatchPacket createStatePacket(long chunkPosLong, IntArrayList indices, net.minecraft.server.level.ServerLevel serverLevel) {
        // Allocate a direct buffer from the pool.
        // Size estimation: Header (24 bytes) + per body (~64 bytes).
        // We estimate conservatively to avoid resizing, but ByteBuf grows automatically if needed.
        int estimatedSize = 24 + (indices.size() * 64);
        ByteBuf rawBuf = ALLOCATOR.directBuffer(estimatedSize);

        try {
            ChunkPos chunkPos = new ChunkPos(chunkPosLong);
            double chunkBaseX = chunkPos.getMinBlockX();
            double chunkBaseY = serverLevel.getMinBuildHeight();
            double chunkBaseZ = chunkPos.getMinBlockZ();

            // Write Header
            rawBuf.writeInt(indices.size());
            rawBuf.writeLong(System.nanoTime());
            rawBuf.writeLong(chunkPosLong);

            // Write Body Data (Structure of Arrays -> Stream)
            // We iterate over the indices and write primitives directly to off-heap memory.
            for (int i = 0; i < indices.size(); i++) {
                int idx = indices.getInt(i);

                rawBuf.writeInt(dataStore.networkId[idx]);
                
                // Relative positions are sent as floats to save bandwidth (double -> float precision loss is acceptable for rendering relative to chunk)
                rawBuf.writeFloat((float) (dataStore.posX[idx] - chunkBaseX));
                rawBuf.writeFloat((float) (dataStore.posY[idx] - chunkBaseY));
                rawBuf.writeFloat((float) (dataStore.posZ[idx] - chunkBaseZ));
                
                // Rotations (quaternion)
                rawBuf.writeFloat(dataStore.rotX[idx]);
                rawBuf.writeFloat(dataStore.rotY[idx]);
                rawBuf.writeFloat(dataStore.rotZ[idx]);
                rawBuf.writeFloat(dataStore.rotW[idx]);

                boolean active = dataStore.isActive[idx];
                rawBuf.writeBoolean(active);
                
                // Only send velocity if the body is active/awake
                if (active) {
                    rawBuf.writeFloat(dataStore.velX[idx]);
                    rawBuf.writeFloat(dataStore.velY[idx]);
                    rawBuf.writeFloat(dataStore.velZ[idx]);
                }
            }

            // Compress directly from rawBuf to a new compressedBuf using Zstd
            return new S2CUpdateBodyStateBatchPacket(compressDirect(rawBuf));

        } finally {
            // Ensure the raw buffer is returned to the pool immediately after compression.
            // This is crucial for the "Zero Allocation" strategy to work (recycling memory).
            rawBuf.release();
        }
    }

    /**
     * Creates a compressed vertex update packet for soft bodies.
     * <p>
     * Similar to the state packet, this serializes vertex arrays directly into
     * off-heap memory and compresses them.
     *
     * @param chunkPosLong The chunk position key.
     * @param indices      The indices of the bodies.
     * @return The constructed packet.
     */
    public S2CUpdateVerticesBatchPacket createVertexPacket(long chunkPosLong, IntArrayList indices) {
        // Estimate size: Header + approx 128 bytes per soft body (variable)
        ByteBuf rawBuf = ALLOCATOR.directBuffer(16 + indices.size() * 128); 

        try {
            rawBuf.writeInt(indices.size());
            rawBuf.writeLong(chunkPosLong);

            for (int i = 0; i < indices.size(); i++) {
                int idx = indices.getInt(i);
                rawBuf.writeInt(dataStore.networkId[idx]);
                
                float[] vData = dataStore.vertexData[idx];
                if (vData != null && vData.length > 0) {
                    rawBuf.writeBoolean(true);
                    rawBuf.writeInt(vData.length);
                    // Bulk write would be faster if we had FloatBuffer access to source, 
                    // but loop is necessary for float[] array.
                    for (float v : vData) {
                        rawBuf.writeFloat(v);
                    }
                } else {
                    rawBuf.writeBoolean(false);
                }
            }

            return new S2CUpdateVerticesBatchPacket(compressDirect(rawBuf));

        } finally {
            rawBuf.release();
        }
    }

    /**
     * Compresses the data from the source buffer into a new pooled buffer using Zstd.
     * <p>
     * This method uses the {@link Zstd#compressDirectByteBuffer} API to avoid copying
     * data into Java byte arrays.
     *
     * @param source The uncompressed data (readable part is compressed).
     * @return A new ByteBuf containing the compressed data. The caller is responsible for releasing it (usually via the Packet).
     */
    private ByteBuf compressDirect(ByteBuf source) {
        int uncompressedLen = source.readableBytes();
        // Calculate the maximum possible size of compressed data to allocate sufficient buffer
        int maxCompressedLen = (int) Zstd.compressBound(uncompressedLen);

        ByteBuf dest = ALLOCATOR.directBuffer(maxCompressedLen);
        
        // Use NIO buffers for Zstd-JNI to avoid array copies.
        // nioBuffer() returns a wrapper, it does NOT copy the memory.
        ByteBuffer srcNio = source.nioBuffer(source.readerIndex(), uncompressedLen);
        ByteBuffer dstNio = dest.nioBuffer(0, maxCompressedLen);

        // Perform compression directly in native memory
        long compressedSize = Zstd.compressDirectByteBuffer(
            dstNio, 
            0, // dst offset
            maxCompressedLen, // dst size
            srcNio, 
            0, // src offset
            uncompressedLen, // src size
            ZSTD_COMPRESSION_LEVEL
        );
        
        if (Zstd.isError(compressedSize)) {
            dest.release(); // Prevent leak on error
            throw new RuntimeException("Compression failed: " + Zstd.getErrorName(compressedSize));
        }

        // Set the writer index to the actual compressed size so Netty knows how much data is in there
        dest.writerIndex((int) compressedSize);
        return dest;
    }
}