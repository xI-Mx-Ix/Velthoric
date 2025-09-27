/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A highly optimized utility class for compressing and decompressing packet data using ZLIB.
 * It leverages ThreadLocal instances of Deflater, Inflater, and buffers to avoid
 * repeated memory allocations, significantly reducing garbage collection pressure under high load.
 */
public class VxPacketUtils {

    // A thread-local Deflater instance. Each thread gets its own, which is reset and reused.
    private static final ThreadLocal<Deflater> DEFLATER = ThreadLocal.withInitial(Deflater::new);

    // A thread-local Inflater instance.
    private static final ThreadLocal<Inflater> INFLATER = ThreadLocal.withInitial(Inflater::new);

    // A thread-local reusable output stream. Its internal buffer grows as needed and is not
    // deallocated between uses, preventing constant reallocation.
    private static final ThreadLocal<ByteArrayOutputStream> OUTPUT_STREAM = ThreadLocal.withInitial(ByteArrayOutputStream::new);

    // A thread-local reusable byte buffer for chunking data during compression/decompression.
    // 8KB is a good general-purpose size.
    private static final ThreadLocal<byte[]> BUFFER = ThreadLocal.withInitial(() -> new byte[8192]);

    /**
     * Compresses a byte array using a reusable, thread-local Deflater.
     * This method is optimized to minimize memory allocations.
     *
     * @param data The data to compress.
     * @return The compressed data.
     * @throws IOException If a compression error occurs.
     */
    public static byte[] compress(byte[] data) throws IOException {
        // Get the Deflater and buffers for the current thread.
        Deflater deflater = DEFLATER.get();
        ByteArrayOutputStream outputStream = OUTPUT_STREAM.get();
        byte[] buffer = BUFFER.get();

        // Reset the state of the reusable objects before use.
        deflater.reset();
        outputStream.reset();

        // Set the data to be compressed.
        deflater.setInput(data);
        deflater.finish();

        // Compress the data in chunks into the reusable output stream.
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            outputStream.write(buffer, 0, count);
        }

        // The toByteArray() call is the only necessary allocation, creating the final result array.
        return outputStream.toByteArray();
        // Note: We do NOT call deflater.end() as we want to reuse the object.
    }

    /**
     * Decompresses a byte array using a reusable, thread-local Inflater.
     * This method is optimized to minimize memory allocations.
     *
     * @param data The data to decompress.
     * @return The original, decompressed data.
     * @throws IOException If a decompression error occurs.
     * @throws DataFormatException If the compressed data format is invalid.
     */
    public static byte[] decompress(byte[] data) throws IOException, DataFormatException {
        // Get the Inflater and buffers for the current thread.
        Inflater inflater = INFLATER.get();
        ByteArrayOutputStream outputStream = OUTPUT_STREAM.get();
        byte[] buffer = BUFFER.get();

        // Reset the state of the reusable objects before use.
        inflater.reset();
        outputStream.reset();

        // Set the data to be decompressed.
        inflater.setInput(data);

        // Decompress the data in chunks into the reusable output stream.
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            outputStream.write(buffer, 0, count);
        }

        // The toByteArray() call creates the final result array.
        return outputStream.toByteArray();
        // Note: We do NOT call inflater.end() as we want to reuse the object.
    }
}