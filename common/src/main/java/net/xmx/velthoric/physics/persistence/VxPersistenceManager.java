/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.persistence;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages a shared, global I/O worker pool for all physics persistence operations.
 * This centralizes thread management, preventing the creation of excessive threads
 * when multiple dimensions and storage types are active.
 *
 * <p>This class should be initialized once when the server starts and shut down
 * when the server stops.
 *
 * @author xI-Mx-Ix
 */
public final class VxPersistenceManager {

    private static ExecutorService ioWorker;

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private VxPersistenceManager() {}

    /**
     * Initializes the shared I/O worker pool. If already initialized, this method
     * does nothing.
     */
    public static void initialize() {
        if (ioWorker != null && !ioWorker.isShutdown()) {
            return; // Already initialized
        }

        int threadCount = Math.max(2, Runtime.getRuntime().availableProcessors() / 4);
        ioWorker = Executors.newFixedThreadPool(threadCount, new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "Velthoric IOWorker-" + threadNumber.getAndIncrement());
                t.setDaemon(true); // Ensure threads do not prevent JVM shutdown
                return t;
            }
        });
    }

    /**
     * Shuts down the shared I/O worker pool, waiting a short period for tasks
     * to complete. This should be called during server shutdown, after all
     * save operations have been initiated.
     */
    public static void shutdown() {
        if (ioWorker != null && !ioWorker.isShutdown()) {
            ioWorker.shutdown();
            try {
                if (!ioWorker.awaitTermination(10, TimeUnit.SECONDS)) {
                    ioWorker.shutdownNow();
                }
            } catch (InterruptedException e) {
                ioWorker.shutdownNow();
                Thread.currentThread().interrupt();
            }
            ioWorker = null;
        }
    }

    /**
     * Gets the shared {@link ExecutorService} for I/O tasks.
     *
     * @return The shared executor service.
     * @throws IllegalStateException if the manager has not been initialized or has been shut down.
     */
    public static ExecutorService getExecutor() {
        if (ioWorker == null || ioWorker.isShutdown()) {
            throw new IllegalStateException("VxPersistenceManager has not been initialized or is already shut down.");
        }
        return ioWorker;
    }
}