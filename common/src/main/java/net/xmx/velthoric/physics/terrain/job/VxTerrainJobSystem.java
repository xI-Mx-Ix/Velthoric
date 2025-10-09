/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.job;

import net.xmx.velthoric.init.VxMainClass;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A dedicated thread pool for handling asynchronous terrain generation tasks.
 * It manages a fixed number of worker threads to process jobs in parallel without
 * blocking the main server thread.
 *
 * @author xI-Mx-Ix
 */
public final class VxTerrainJobSystem {

    private final ExecutorService executorService;

    /**
     * Initializes the job system with an optimal number of threads based on available processors.
     */
    public VxTerrainJobSystem() {
        // Use a balanced number of threads, leaving resources for the main server and other tasks.
        // This prevents the terrain system from monopolizing the CPU.
        int threadCount = Math.max(2, Math.min(Runtime.getRuntime().availableProcessors() - 2, 6));
        this.executorService = new ThreadPoolExecutor(
                threadCount, threadCount,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new JobThreadFactory()
        );
    }

    /**
     * Submits a task for asynchronous execution.
     *
     * @param task The task to run.
     * @return A {@link CompletableFuture} that completes when the task is done.
     */
    public CompletableFuture<Void> submit(Runnable task) {
        if (isShutdown()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(task, executorService);
    }

    /**
     * Shuts down the job system, attempting a graceful termination before forcing it.
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    VxMainClass.LOGGER.error("Terrain JobSystem did not terminate.");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Gets the underlying executor service.
     *
     * @return The executor service.
     */
    public Executor getExecutor() {
        return this.executorService;
    }

    /**
     * A custom thread factory to name the worker threads for easier debugging.
     */
    private static class JobThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix = "Velthoric Terrain Job - ";

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

    /**
     * Checks if the job system has been shut down.
     *
     * @return True if the system is shut down or terminated, false otherwise.
     */
    public boolean isShutdown() {
        return executorService.isShutdown() || executorService.isTerminated();
    }
}