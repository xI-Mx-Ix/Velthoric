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
 * It leverages ThreadLocal instances of compression/decompression contexts to avoid repeated
 * memory allocations and reduce context-switching overhead, significantly minimizing
 * garbage collection pressure under high network load. This implementation uses the byte[] API
 * to ensure compatibility and avoid direct buffer allocation issues.
 *
 * @author xI-Mx-Ix
 */
public class VxPacketUtils {

    /**
     * The default compression level for Zstd. Level 3 is a good balance
     * between speed and compression ratio for real-time game data.
     */
    private static final int COMPRESSION_LEVEL = 3;

    private static final ThreadLocal<ZstdCompressCtx> COMPRESS_CTX = ThreadLocal.withInitial(() -> new ZstdCompressCtx().setLevel(COMPRESSION_LEVEL));
    private static final ThreadLocal<ZstdDecompressCtx> DECOMPRESS_CTX = ThreadLocal.withInitial(ZstdDecompressCtx::new);

    /**
     * Compresses a byte array using a reusable, thread-local Zstd compression context.
     * This method is optimized to minimize memory allocations.
     *
     * @param data The uncompressed data.
     * @return The compressed data.
     * @throws IOException If a compression error occurs.
     */
    public static byte[] compress(byte[] data) throws IOException {
        ZstdCompressCtx ctx = COMPRESS_CTX.get();
        // Zstd.compressBound provides the worst-case size for the compressed data.
        long maxCompressedSize = Zstd.compressBound(data.length);
        if (maxCompressedSize > Integer.MAX_VALUE) {
            throw new IOException("Data is too large to compress.");
        }

        byte[] compressed = new byte[(int) maxCompressedSize];
        long compressedSize = ctx.compress(compressed, data);

        if (Zstd.isError(compressedSize)) {
            throw new IOException("Zstd compression failed: " + Zstd.getErrorName(compressedSize));
        }

        // Return a correctly sized array, as the allocated buffer is likely larger than needed.
        return Arrays.copyOf(compressed, (int) compressedSize);
    }

    /**
     * Decompresses a byte array using a reusable, thread-local Zstd decompression context.
     * The original, uncompressed size must be known.
     *
     * @param data           The compressed data.
     * @param uncompressedSize The size of the original uncompressed data.
     * @return The original, decompressed data.
     * @throws IOException If a decompression error occurs.
     */
    public static byte[] decompress(byte[] data, int uncompressedSize) throws IOException {
        ZstdDecompressCtx ctx = DECOMPRESS_CTX.get();
        byte[] decompressed = new byte[uncompressedSize];

        long decompressedSize = ctx.decompress(decompressed, data);

        if (Zstd.isError(decompressedSize)) {
            throw new IOException("Zstd decompression failed: " + Zstd.getErrorName(decompressedSize));
        }
        if (decompressedSize != uncompressedSize) {
            throw new IOException("Decompressed size mismatch. Expected " + uncompressedSize + ", got " + decompressedSize);
        }

        return decompressed;
    }
}