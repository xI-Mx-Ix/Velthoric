/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.persistence;

import net.xmx.velthoric.init.VxMainClass;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A highly efficient, single-threaded I/O processor dedicated to performing
 * physical disk operations for a specific storage domain.
 * <p>
 * <b>Architectural Purpose:</b>
 * File systems (especially on Windows) often lock files during write operations.
 * Attempting to write to the same file from multiple threads in the common ForkJoinPool
 * can lead to {@link java.nio.file.FileSystemException}.
 * <p>
 * This processor solves that by serializing all disk access into a single background queue.
 * It accepts pre-serialized data payloads, ensuring that the heavy CPU work of serialization
 * happens elsewhere, leaving this thread to strictly handle I/O throughput.
 *
 * @author xI-Mx-Ix
 */
public class VxIOProcessor implements AutoCloseable {

    private final ExecutorService executor;
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private final String workerName;

    /**
     * Creates a new I/O processor with a dedicated background thread.
     *
     * @param workerName A label for the worker thread (e.g., "body-io"), useful for debugging logs.
     */
    public VxIOProcessor(String workerName) {
        this.workerName = workerName;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "VxIO-" + workerName);
            t.setDaemon(true); // Ensure the JVM can exit even if this thread is idle
            t.setPriority(Thread.NORM_PRIORITY - 1); // Slightly lower priority to minimize impact on game ticks
            return t;
        });
    }

    /**
     * Queues a write operation to disk.
     * <p>
     * This method returns immediately (non-blocking). The actual file I/O happens asynchronously
     * on the worker thread. If the worker is busy, tasks accumulate in an unbounded queue.
     *
     * @param targetPath The final absolute path where the file should be written.
     * @param data       The byte array to write.
     * @return A {@link CompletableFuture} that completes when the file is physically written to disk,
     * or completes exceptionally if an I/O error occurs.
     */
    public CompletableFuture<Void> submitWrite(Path targetPath, byte[] data) {
        if (isShutdown.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Worker is shut down"));
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        executor.submit(() -> {
            try {
                performAtomicWrite(targetPath, data);
                future.complete(null);
            } catch (Exception e) {
                VxMainClass.LOGGER.error("[{}] Failed to write file: {}", workerName, targetPath, e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Queues a read operation from disk.
     *
     * @param path The absolute path to read.
     * @return A {@link CompletableFuture} containing the file bytes, or {@code null} if the file does not exist.
     */
    public CompletableFuture<byte[]> submitRead(Path path) {
        if (isShutdown.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Worker is shut down"));
        }

        return CompletableFuture.supplyAsync(() -> {
            if (!Files.exists(path)) return null;
            try {
                return Files.readAllBytes(path);
            } catch (IOException e) {
                VxMainClass.LOGGER.error("[{}] Failed to read file: {}", workerName, path, e);
                return null;
            }
        }, executor);
    }

    /**
     * Performs a safe atomic write using the "Write-to-Temp-and-Rename" strategy.
     * <p>
     * 1. Writes data to {@code filename.tmp}.<br>
     * 2. Forces the OS to flush buffers to the physical disk.<br>
     * 3. Atomically moves/renames the temp file to the target file.
     * <p>
     * This ensures that the target file is never in a corrupted or partial state,
     * even if the server loses power during the write.
     *
     * @param targetPath The destination path.
     * @param data       The data to write.
     * @throws IOException If the write or move operation fails.
     */
    private void performAtomicWrite(Path targetPath, byte[] data) throws IOException {
        Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");

        // Ensure parent directory exists
        Files.createDirectories(targetPath.getParent());

        // 1. Write data to a temporary file
        try (FileChannel channel = FileChannel.open(tempPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.write(ByteBuffer.wrap(data));
            channel.force(true); // Critical: Force OS to flush buffers to physical disk
        }

        // 2. Atomically move temp file to target file, replacing if exists
        Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Shuts down the worker thread gracefully.
     * Any tasks currently running will finish, but new tasks will be rejected.
     */
    @Override
    public void close() {
        isShutdown.set(true);
        executor.shutdown();
        try {
            // Wait a reasonable amount of time for pending I/O to finish
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}