package net.xmx.vortex.physics.terrain.job;

import net.xmx.vortex.init.VxMainClass;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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

    public CompletableFuture<Void> submit(Runnable task) {
        return CompletableFuture.runAsync(task, executorService);
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
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

    public Executor getExecutor() {
        return this.executorService;
    }

    private static class JobThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix = "Vortex Terrain JobSystem-";

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

    public boolean isShutdown() {
        return executorService.isShutdown();
    }
}