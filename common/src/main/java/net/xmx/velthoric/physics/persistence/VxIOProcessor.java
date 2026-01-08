/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.persistence;

import net.xmx.velthoric.init.VxMainClass;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A dedicated, single-threaded I/O orchestrator for handling region file persistence.
 * <p>
 * <b>Problem Solved:</b>
 * On systems like Windows, attempting to read/write/rename the same file from multiple threads
 * concurrently often leads to {@link java.nio.file.AccessDeniedException}. Additionally,
 * rapid updates (e.g., auto-save followed immediately by a pause menu save) can cause race
 * conditions or unnecessary disk churn.
 * <p>
 * <b>Solution Architecture:</b>
 * 1. <b>Sequential Execution:</b> All disk operations run on a single background thread. This
 *    eliminates file locking conflicts within the application.
 * 2. <b>Write-Behind Cache:</b> When data is saved, it is not written to disk immediately.
 *    Instead, it is stored in a {@code ConcurrentHashMap}. If new data arrives for the same
 *    region before the disk write occurs, the cache entry is simply updated. This "coalescing"
 *    ensures we only write the latest state.
 *
 * @author xI-Mx-Ix
 */
public class VxIOProcessor implements AutoCloseable {

    /**
     * The root directory where region files managed by this worker are stored.
     */
    private final Path storageFolder;

    /**
     * A name used for thread identification and logging (e.g., "body", "constraint").
     */
    private final String workerName;

    /**
     * Flag indicating if the worker is in the process of shutting down or closed.
     */
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    /**
     * The Write-Behind Cache.
     * Maps a region position to a pending write task. Access to this map is thread-safe.
     * If a key exists here, it means there is data waiting to be flushed to disk.
     */
    private final Map<VxAbstractRegionStorage.RegionPos, WriteTaskCache> pendingWrites = new ConcurrentHashMap<>();

    /**
     * The FIFO queue of tasks waiting to be processed by the worker thread.
     */
    private final Queue<Runnable> workQueue = new ConcurrentLinkedQueue<>();

    /**
     * The raw executor service that powers the single worker thread.
     */
    private final ExecutorService internalExecutor;

    /**
     * Atomic state of the worker loop.
     * Bit 0: Closed
     * Bit 1: Scheduled (Thread is currently running or scheduled to run)
     */
    private final AtomicInteger runState = new AtomicInteger(0);
    private static final int STATE_CLOSED = 1;
    private static final int STATE_SCHEDULED = 2;

    /**
     * Creates a new I/O worker.
     *
     * @param storageFolder The folder to store files in.
     * @param workerName    The identifier for this worker.
     */
    public VxIOProcessor(Path storageFolder, String workerName) {
        this.storageFolder = storageFolder;
        this.workerName = workerName;
        this.internalExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "VxIOProcessor-" + workerName);
            t.setPriority(Thread.NORM_PRIORITY - 1); // Slightly lower priority for background I/O
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Queues a write operation for a specific region.
     * <p>
     * This method updates the pending write cache immediately. It allows for "Write Coalescing":
     * if a write for this region is already pending, the data is swapped with the newer version,
     * and the previous Future is chained to the new one (or kept if appropriate).
     *
     * @param pos  The position of the region.
     * @param data The serialized byte array to write.
     * @return A Future that completes when this specific data version is physically written to disk.
     */
    public CompletableFuture<Void> store(VxAbstractRegionStorage.RegionPos pos, byte[] data) {
        // Create or update the pending entry in the cache map.
        // compute() ensures atomicity for the map operation.
        WriteTaskCache task = pendingWrites.compute(pos, (k, existing) -> {
            if (existing != null) {
                // If there was already a pending write, update the data to the new version.
                // The future remains the same so that anyone waiting on the *previous* save
                // will effectively wait for this *new* save to complete, which ensures consistency.
                existing.data = data;
                return existing;
            } else {
                return new WriteTaskCache(data);
            }
        });

        // Notify the worker thread that there is pending work (flushing the map).
        scheduleFlush();

        // Safe check: strictly speaking task cannot be null here due to compute logic.
        return task.completionFuture;
    }

    /**
     * Requests data for a region.
     * <p>
     * This method first checks the pending write cache. If the data is currently waiting to be
     * written, it is returned directly from memory (Fast Path). This ensures we never read
     * stale data from disk while a newer version sits in RAM.
     * <p>
     * If not in cache, a read task is queued for the background thread.
     *
     * @param pos The position of the region.
     * @return A Future containing the byte array, or null if no file exists.
     */
    public CompletableFuture<byte[]> load(VxAbstractRegionStorage.RegionPos pos) {
        // 1. Fast Path: Check Write Cache (Write-Behind)
        WriteTaskCache pending = pendingWrites.get(pos);
        if (pending != null && pending.data != null) {
            // Return a copy or the direct reference (byte arrays are mutable, but usually treated as immutable here).
            // Since this is generic storage, returning the ref is acceptable if the caller doesn't mutate it.
            return CompletableFuture.completedFuture(pending.data);
        }

        // 2. Slow Path: Queue a disk read
        CompletableFuture<byte[]> resultFuture = new CompletableFuture<>();
        
        enqueueTask(() -> {
            // This runs on the worker thread
            byte[] bytes = performDiskRead(pos);
            resultFuture.complete(bytes);
        });

        return resultFuture;
    }

    /**
     * Synchronizes the worker, ensuring all currently queued tasks are finished.
     *
     * @param flushToDisk If true, explicitly forces a flush of the pending write cache to disk.
     * @return A Future that completes when synchronization is done.
     */
    public CompletableFuture<Void> synchronize(boolean flushToDisk) {
        CompletableFuture<Void> syncFuture = new CompletableFuture<>();

        // Add a barrier task to the queue. When this task runs, we know all previous tasks are done.
        enqueueTask(() -> {
            if (flushToDisk) {
                // Force execution of the write-cache flushing logic immediately
                processPendingWrites();
            }
            syncFuture.complete(null);
        });

        return syncFuture;
    }

    /**
     * Adds a generic runnable to the work queue and wakes up the worker thread.
     *
     * @param task The task to run.
     */
    private void enqueueTask(Runnable task) {
        if (shutdownRequested.get()) {
            // If shutting down, we drop the task or log it.
            return;
        }
        workQueue.add(task);
        ensureRunning();
    }

    /**
     * Schedules a check to flush pending writes.
     * This adds a specific task to the queue that iterates the map and writes files.
     */
    private void scheduleFlush() {
        // We add a task that calls processPendingWrites.
        // Optimization: We could check if such a task is already in queue, but the overhead is low.
        enqueueTask(this::processPendingWrites);
    }

    /**
     * The core logic to wake up the executor if it is idle.
     * Handles the atomic state transitions to ensure we only submit one runner to the ExecutorService.
     */
    private void ensureRunning() {
        if (canRun() && trySetScheduled()) {
            try {
                internalExecutor.execute(this::runLoop);
            } catch (RejectedExecutionException e) {
                // Should only happen during shutdown. Reset state to allow retry if logical.
                setIdle();
            }
        }
    }

    /**
     * The main loop running on the background thread.
     * It processes the queue until empty.
     */
    private void runLoop() {
        try {
            while (true) {
                Runnable task = workQueue.poll();
                if (task == null) {
                    break;
                }
                try {
                    task.run();
                } catch (Exception e) {
                    VxMainClass.LOGGER.error("Velthoric I/O Worker '{}' crashed processing task", workerName, e);
                }
            }
        } finally {
            setIdle();
            // Double-check: If a task was added exactly while we were exiting the loop/setting idle,
            // we must restart the loop to prevent the task from being stuck.
            if (!workQueue.isEmpty()) {
                ensureRunning();
            }
        }
    }

    /**
     * Iterates over the {@code pendingWrites} map and persists entries to disk.
     * This method MUST be called from the worker thread to maintain sequential I/O safety.
     */
    private void processPendingWrites() {
        Iterator<Map.Entry<VxAbstractRegionStorage.RegionPos, WriteTaskCache>> iterator = pendingWrites.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<VxAbstractRegionStorage.RegionPos, WriteTaskCache> entry = iterator.next();
            // Removing from map transfers ownership to this local scope.
            // Any subsequent store() calls will create a NEW entry in the map.
            iterator.remove();

            VxAbstractRegionStorage.RegionPos pos = entry.getKey();
            WriteTaskCache task = entry.getValue();

            try {
                performDiskWrite(pos, task.data);
                // Complete successfully
                task.completionFuture.complete(null);
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Failed to write region file for {} at {}", workerName, pos, e);
                task.completionFuture.completeExceptionally(e);
            }
        }
    }

    // ========================================================================================
    // Low-Level Disk Operations (Synchronous)
    // ========================================================================================

    private byte[] performDiskRead(VxAbstractRegionStorage.RegionPos pos) {
        Path file = getRegionPath(pos);
        if (!Files.exists(file)) return null;

        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to read region file {}", file, e);
            return null;
        }
    }

    private void performDiskWrite(VxAbstractRegionStorage.RegionPos pos, byte[] data) throws IOException {
        Path finalFile = getRegionPath(pos);
        Path tempFile = finalFile.resolveSibling(finalFile.getFileName() + ".tmp");

        // Logic for deleting the file if data is empty/null (removal)
        if (data == null || data.length == 0) {
            Files.deleteIfExists(tempFile);
            Files.deleteIfExists(finalFile);
            return;
        }

        Files.createDirectories(finalFile.getParent());

        // 1. Write to temporary file first (Atomic Write pattern)
        try (FileChannel channel = FileChannel.open(tempFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.write(ByteBuffer.wrap(data));
            channel.force(true); // Force OS to flush buffer to physical disk
        }

        // 2. Atomically rename temp file to final file
        // This replaces the old file in one operation. If the OS crashes, we have either old or new, never corrupted partial.
        try {
            Files.move(tempFile, finalFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Fallback for filesystems (or specific Windows scenarios) where ATOMIC_MOVE fails despite our precautions
            if (Files.exists(finalFile)) {
                Files.delete(finalFile);
            }
            Files.move(tempFile, finalFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path getRegionPath(VxAbstractRegionStorage.RegionPos pos) {
        return storageFolder.resolve(String.format("%s.%d.%d.vxdat", workerName, pos.x(), pos.z()));
    }

    // ========================================================================================
    // Atomic State Management
    // ========================================================================================

    private boolean canRun() {
        return (runState.get() & STATE_CLOSED) == 0 && !workQueue.isEmpty();
    }

    private boolean trySetScheduled() {
        int current;
        do {
            current = runState.get();
            if ((current & (STATE_CLOSED | STATE_SCHEDULED)) != 0) {
                return false; // Already running or closed
            }
        } while (!runState.compareAndSet(current, current | STATE_SCHEDULED));
        return true;
    }

    private void setIdle() {
        int current;
        do {
            current = runState.get();
        } while (!runState.compareAndSet(current, current & ~STATE_SCHEDULED));
    }

    @Override
    public void close() {
        // Set CLOSED bit atomically
        int current;
        do {
            current = runState.get();
        } while (!runState.compareAndSet(current, current | STATE_CLOSED));

        shutdownRequested.set(true);

        // Graceful shutdown of the internal executor
        internalExecutor.shutdown();
        try {
            if (!internalExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                internalExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            internalExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * A simple data holder for the write-behind cache.
     */
    private static class WriteTaskCache {
        /**
         * The serialized data to write. Mutable, so it can be updated if new data arrives.
         */
        volatile byte @Nullable [] data;
        
        /** The future that completes when this data hits the disk. */
        final CompletableFuture<Void> completionFuture = new CompletableFuture<>();

        WriteTaskCache(byte @Nullable [] data) {
            this.data = data;
        }
    }
}