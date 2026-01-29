/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.network;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;

import java.io.IOException;
import java.util.Arrays;

/**
 * A highly optimized utility class for compressing and decompressing packet data using Zstd via zstd-jni.
 * <p>
 * To achieve maximum performance and minimum Garbage Collector (GC) pressure, this class utilizes
 * {@link ThreadLocal} instances of Zstd compression and decompression contexts. This avoids the
 * significant overhead of allocating native memory for contexts on every operation.
 * <p>
 * This implementation is specifically designed for high-concurrency environments, supporting
 * direct compression into reusable buffers to eliminate intermediate byte array allocations.
 *
 * @author xI-Mx-Ix
 */
public class VxPacketUtils {

    /**
     * The default compression level for Zstd.
     * Level 3 provides an optimal trade-off between CPU usage and compression ratio
     * for high-frequency game state updates.
     */
    private static final int COMPRESSION_LEVEL = 3;

    /**
     * Thread-local compression context to prevent allocation and ensure thread safety
     * during the compression process.
     */
    private static final ThreadLocal<ZstdCompressCtx> COMPRESS_CTX = ThreadLocal.withInitial(() ->
            new ZstdCompressCtx().setLevel(COMPRESSION_LEVEL));

    /**
     * Thread-local decompression context to prevent allocation and ensure thread safety
     * during the decompression process.
     */
    private static final ThreadLocal<ZstdDecompressCtx> DECOMPRESS_CTX = ThreadLocal.withInitial(ZstdDecompressCtx::new);

    /**
     * Calculates the maximum possible size of compressed data for a given input size.
     * This is used to pre-allocate or verify the size of destination buffers.
     *
     * @param srcSize The size of the uncompressed source data in bytes.
     * @return The maximum size the compressed data could occupy (worst-case scenario).
     * @throws IllegalArgumentException if the size exceeds Integer.MAX_VALUE.
     */
    public static int getCompressBound(int srcSize) {
        long bound = Zstd.compressBound(srcSize);
        if (bound > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Source data size exceeds Zstd compression limits");
        }
        return (int) bound;
    }

    /**
     * Compresses the provided byte array into a new, exactly-sized byte array.
     * Note: This method allocates a new array and should be used sparingly in hot loops.
     *
     * @param data The uncompressed data array.
     * @return A new byte array containing the compressed data.
     * @throws IOException If the Zstd compression engine encounters an error.
     */
    public static byte[] compress(byte[] data) throws IOException {
        return compress(data, data.length);
    }

    /**
     * Compresses a specific length of the provided byte array into a new byte array.
     *
     * @param data   The source data array.
     * @param length The number of bytes from the source to compress.
     * @return A new byte array containing the compressed data.
     * @throws IOException If the Zstd compression engine encounters an error.
     */
    public static byte[] compress(byte[] data, int length) throws IOException {
        int maxBound = getCompressBound(length);
        byte[] destination = new byte[maxBound];
        int compressedSize = compressInto(data, 0, length, destination, 0);
        return Arrays.copyOf(destination, compressedSize);
    }

    /**
     * Compresses data from a source array directly into a destination array.
     * This is the most performance-efficient compression method as it allows for
     * complete buffer recycling, resulting in zero heap allocations.
     *
     * @param src       The source array containing uncompressed data.
     * @param srcOffset The starting position in the source array.
     * @param srcLen    The number of bytes to compress.
     * @param dst       The destination array where compressed data will be written.
     * @param dstOffset The starting position in the destination array.
     * @return The actual number of bytes written to the destination array.
     * @throws IOException If the destination buffer is too small or Zstd fails.
     */
    public static int compressInto(byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset) throws IOException {
        ZstdCompressCtx ctx = COMPRESS_CTX.get();
        long result = ctx.compressByteArray(dst, dstOffset, dst.length - dstOffset, src, srcOffset, srcLen);

        if (Zstd.isError(result)) {
            throw new IOException("Zstd compression failed: " + Zstd.getErrorName(result));
        }
        return (int) result;
    }

    /**
     * Decompresses a byte array using the thread-local decompression context.
     * The exact uncompressed size must be known beforehand.
     *
     * @param data             The compressed data.
     * @param uncompressedSize The expected size of the data after decompression.
     * @return A new byte array containing the decompressed data.
     * @throws IOException If the data is corrupt or the size mismatch occurs.
     */
    public static byte[] decompress(byte[] data, int uncompressedSize) throws IOException {
        ZstdDecompressCtx ctx = DECOMPRESS_CTX.get();
        byte[] decompressed = new byte[uncompressedSize];

        long decompressedSize = ctx.decompress(decompressed, data);

        if (Zstd.isError(decompressedSize)) {
            throw new IOException("Zstd decompression failed: " + Zstd.getErrorName(decompressedSize));
        }
        if (decompressedSize != uncompressedSize) {
            throw new IOException("Decompressed size mismatch. Expected " + uncompressedSize + " bytes, but got " + decompressedSize);
        }

        return decompressed;
    }
}