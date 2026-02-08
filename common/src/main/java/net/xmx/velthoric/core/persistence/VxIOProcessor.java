/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.persistence;

import net.xmx.velthoric.init.VxMainClass;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A dedicated, single-threaded processor for handling asynchronous Disk I/O operations.
 * <p>
 * This class wraps an {@link ExecutorService} to ensure that all physical file system interactions
 * for a specific storage domain (e.g., Bodies or Constraints) occur sequentially on a separate thread.
 * This prevents I/O blocking on the main game thread or the physics simulation thread.
 * <p>
 * <b>Threading Model:</b>
 * The underlying thread is configured as a Daemon thread with slightly lower priority,
 * ensuring it does not prevent the JVM from shutting down and has minimal impact on
 * real-time tick performance.
 *
 * @author xI-Mx-Ix
 */
public class VxIOProcessor implements AutoCloseable {

    private final ExecutorService executor;
    private final String workerName;

    /**
     * Constructs a new I/O processor.
     *
     * @param name The logical name of the worker (e.g., "Body-IO"), used for thread naming.
     */
    public VxIOProcessor(String name) {
        this.workerName = name;
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            private final AtomicInteger threadId = new AtomicInteger(1);

            @Override
            public Thread newThread(@NotNull Runnable r) {
                Thread t = new Thread(r, "VxIO-" + name + "-" + threadId.getAndIncrement());

                // Daemon threads do not prevent the JVM from exiting.
                // This is crucial if the server stops forcefully.
                t.setDaemon(true);

                // Priority is set slightly below NORM to favor tick processing
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            }
        });
    }

    /**
     * Retrieves the underlying executor service.
     * <p>
     * This allows clients (like {@link VxChunkBasedStorage}) to submit complex tasks,
     * such as {@link java.util.concurrent.CompletableFuture} chains, directly to the worker.
     *
     * @return The backing single-threaded executor.
     */
    public ExecutorService getExecutor() {
        return executor;
    }

    /**
     * Submits a simple runnable task to the I/O thread.
     *
     * @param task The task to execute.
     */
    public void execute(Runnable task) {
        if (!executor.isShutdown()) {
            executor.execute(task);
        } else {
            VxMainClass.LOGGER.warn("Attempted to execute I/O task on shut down processor: {}", workerName);
        }
    }

    /**
     * Initiates a graceful shutdown of the I/O worker.
     * <p>
     * This method waits up to 5 seconds for currently running tasks to complete before
     * forcing a shutdown. This ensures that in-progress file writes have a chance to finish.
     */
    @Override
    public void close() {
        if (executor.isShutdown()) return;

        executor.shutdown();
        try {
            // Wait a reasonable amount of time for pending writes to flush
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                VxMainClass.LOGGER.warn("I/O Processor {} did not terminate in time, forcing shutdown.", workerName);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            VxMainClass.LOGGER.error("Interrupted while shutting down I/O Processor {}", workerName, e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}