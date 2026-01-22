/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.job;

import net.xmx.velthoric.init.VxMainClass;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages a dedicated thread pool for handling asynchronous terrain generation tasks.
 * This isolates the expensive meshing process from the main server thread to prevent lag.
 *
 * @author xI-Mx-Ix
 */
public final class VxTerrainJobSystem {

    private final ExecutorService executorService;

    public VxTerrainJobSystem() {
        int threadCount = Math.max(3, Math.min(8, Runtime.getRuntime().availableProcessors() - 1));
        this.executorService = new ThreadPoolExecutor(
                threadCount, threadCount,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new JobThreadFactory()
        );
    }

    /**
     * Submits a task to be executed by the job system's thread pool.
     *
     * @param task The task to execute.
     * @return A CompletableFuture that completes when the task is done.
     */
    public CompletableFuture<Void> submit(Runnable task) {
        if (isShutdown()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(task, executorService);
    }

    /**
     * Shuts down the job system's executor service gracefully.
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    VxMainClass.LOGGER.error("JobSystem did not terminate.");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Checks if the job system has been shut down.
     *
     * @return True if the service is shut down or terminated.
     */
    public boolean isShutdown() {
        return executorService.isShutdown() || executorService.isTerminated();
    }

    /**
     * A custom thread factory to give worker threads descriptive names.
     */
    private static class JobThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix = "Velthoric Terrain JobSystem - ";

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}