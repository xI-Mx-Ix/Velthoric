/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.persistence;

import net.xmx.velthoric.init.VxMainClass;

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

        // A small, fixed number of threads is ideal for I/O operations.
        int threadCount = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        ioWorker = Executors.newFixedThreadPool(threadCount, new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "Velthoric-IOWorker-" + threadNumber.getAndIncrement());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1); // I/O can be slightly lower priority
                return t;
            }
        });
        VxMainClass.LOGGER.info("Initialized Velthoric Persistence Manager with {} I/O threads.", threadCount);
    }

    /**
     * Shuts down the shared I/O worker pool, waiting a short period for tasks
     * to complete. This should be called during server shutdown.
     */
    public static void shutdown() {
        if (ioWorker != null && !ioWorker.isShutdown()) {
            ioWorker.shutdown();
            try {
                if (!ioWorker.awaitTermination(10, TimeUnit.SECONDS)) {
                    VxMainClass.LOGGER.warn("I/O worker did not terminate in 10 seconds, forcing shutdown.");
                    ioWorker.shutdownNow();
                }
            } catch (InterruptedException e) {
                ioWorker.shutdownNow();
                Thread.currentThread().interrupt();
            }
            ioWorker = null;
            VxMainClass.LOGGER.debug("Velthoric Persistence Manager shut down.");
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
            VxMainClass.LOGGER.warn("VxPersistenceManager was requested but was not active. Re-initializing...");
            initialize();
        }
        return ioWorker;
    }
}