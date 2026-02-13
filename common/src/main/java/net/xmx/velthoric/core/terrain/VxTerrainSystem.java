/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.terrain;

import com.github.stephengold.joltjni.BodyInterface;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.core.terrain.cache.VxTerrainShapeCache;
import net.xmx.velthoric.core.terrain.generation.VxTerrainGenerator;
import net.xmx.velthoric.core.terrain.job.VxTerrainJobSystem;
import net.xmx.velthoric.core.terrain.management.VxTerrainManager;
import net.xmx.velthoric.core.terrain.management.VxTerrainTracker;
import net.xmx.velthoric.core.terrain.storage.VxChunkDataStore;
import net.xmx.velthoric.core.terrain.util.VxUpdateContext;
import net.xmx.velthoric.init.VxMainClass;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The main entry point and orchestrator for the terrain physics system.
 * This class initializes, manages, and shuts down all terrain-related subsystems,
 * including tracking, generation, and storage. It also serves as the primary interface
 * for interactions from other parts of the application, such as block updates.
 *
 * @author xI-Mx-Ix
 */
public final class VxTerrainSystem implements Runnable {

    private final VxPhysicsWorld physicsWorld;
    private final ServerLevel level;
    private final MinecraftServer server;
    private final VxTerrainJobSystem jobSystem;
    private final Thread workerThread;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    private final VxChunkDataStore chunkDataStore;
    private final VxTerrainShapeCache shapeCache;
    private final VxTerrainGenerator terrainGenerator;
    private final VxTerrainManager terrainManager;
    private final VxTerrainTracker terrainTracker;

    /**
     * Chunks scheduled for a rebuild due to world modification.
     * Uses bit-packed long coordinates to prevent object allocations.
     */
    private final LongSet chunksToRebuild = new LongOpenHashSet();

    private static final ThreadLocal<VxUpdateContext> updateContext = ThreadLocal.withInitial(VxUpdateContext::new);

    public VxTerrainSystem(VxPhysicsWorld physicsWorld, ServerLevel level) {
        this.physicsWorld = physicsWorld;
        this.level = level;
        this.server = level.getServer();

        this.jobSystem = new VxTerrainJobSystem();
        this.shapeCache = new VxTerrainShapeCache(2048); // A reasonable capacity
        this.chunkDataStore = new VxChunkDataStore();
        this.terrainGenerator = new VxTerrainGenerator(shapeCache);
        this.terrainManager = new VxTerrainManager(physicsWorld, level, terrainGenerator, chunkDataStore, jobSystem);
        this.terrainTracker = new VxTerrainTracker(physicsWorld, terrainManager, chunkDataStore, level);

        this.workerThread = new Thread(this, "Velthoric Terrain System - " + level.dimension().location().getPath());
        this.workerThread.setDaemon(true);
    }

    /**
     * Initializes the terrain system, starts background threads, and prepares storage.
     */
    public void initialize() {
        if (isInitialized.compareAndSet(false, true)) {
            this.workerThread.start();
        }
    }

    /**
     * Shuts down the terrain system, stops all background tasks, and cleans up resources.
     */
    public void shutdown() {
        if (isInitialized.compareAndSet(true, false)) {
            jobSystem.shutdown();
            workerThread.interrupt();

            VxMainClass.LOGGER.debug("Shutting down Terrain System for '{}'. Waiting up to 30 seconds for worker thread to exit...", level.dimension().location());
            try {
                workerThread.join(30000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                VxMainClass.LOGGER.warn("Interrupted while waiting for terrain system thread to stop.");
            }

            if (workerThread.isAlive()) {
                StringBuilder stackTraceBuilder = new StringBuilder();
                stackTraceBuilder.append("Stack trace of deadlocked thread '").append(workerThread.getName()).append("':\n");
                for (StackTraceElement ste : workerThread.getStackTrace()) {
                    stackTraceBuilder.append("\tat ").append(ste).append("\n");
                }
                VxMainClass.LOGGER.fatal("Terrain system thread for '{}' did not terminate in 30 seconds. Forcing shutdown.\n{}", level.dimension().location(), stackTraceBuilder.toString());
                VxMainClass.LOGGER.warn("Skipping resource cleanup for '{}' to prevent data corruption due to the deadlocked thread.", level.dimension().location());
            } else {
                VxMainClass.LOGGER.debug("Terrain system for '{}' shut down gracefully.", level.dimension().location());
                VxMainClass.LOGGER.debug("Cleaning up terrain physics bodies for '{}'...", level.dimension().location());
                terrainManager.cleanupAllBodies();

                // Clean up caches and generator
                shapeCache.clear();
                terrainGenerator.close();

                chunkDataStore.clear();
                chunksToRebuild.clear();
                terrainTracker.clear();

                VxMainClass.LOGGER.debug("Terrain system for '{}' has been fully shut down.", level.dimension().location());
            }
        }
    }

    /**
     * The main loop for the terrain system worker thread. Periodically updates body trackers
     * and processes chunks that need to be rebuilt. It now checks if the server is running.
     */
    @Override
    public void run() {
        while (isInitialized.get() && !Thread.currentThread().isInterrupted() && server.isRunning()) {
            try {
                if (physicsWorld.isRunning()) {
                    terrainTracker.update();
                    processRebuildQueue();
                }
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (server.isRunning() && isInitialized.get()) {
                    VxMainClass.LOGGER.error("Error in TerrainSystem worker thread", e);
                }
            }
        }
    }

    /**
     * Handles a block update event from the game world. If a terrain chunk is affected,
     * it is queued for a rebuild and nearby physics bodies are woken up.
     *
     * @param worldPos The position of the block that changed.
     */
    public void onBlockUpdate(BlockPos worldPos) {
        // Guard against starting new work during shutdown.
        if (!isInitialized.get() || !server.isRunning()) {
            return;
        }

        long packedPos = SectionPos.asLong(worldPos);
        if (terrainManager.isManaged(packedPos)) {
            synchronized (chunksToRebuild) {
                chunksToRebuild.add(packedPos);
            }
        }

        physicsWorld.execute(() -> {
            BodyInterface bi = physicsWorld.getPhysicsSystem().getBodyInterface();
            if (bi == null) return;

            VxUpdateContext ctx = updateContext.get();
            ctx.vec3_1.set(worldPos.getX() - 2.0f, worldPos.getY() - 2.0f, worldPos.getZ() - 2.0f);
            ctx.vec3_2.set(worldPos.getX() + 3.0f, worldPos.getY() + 3.0f, worldPos.getZ() + 3.0f);

            ctx.aabox_1.setMin(ctx.vec3_1);
            ctx.aabox_1.setMax(ctx.vec3_2);

            bi.activateBodiesInAaBox(ctx.aabox_1, ctx.bplFilter, ctx.olFilter);
        });
    }

    /**
     * Processes the queue of chunks waiting to be rebuilt, scheduling them for regeneration.
     */
    private void processRebuildQueue() {
        LongSet batch;
        synchronized (chunksToRebuild) {
            if (chunksToRebuild.isEmpty()) return;
            batch = new LongOpenHashSet(chunksToRebuild);
            chunksToRebuild.clear();
        }

        for (long packedPos : batch) {
            terrainManager.rebuildChunk(packedPos);
        }
    }

    /**
     * Checks if a terrain chunk at a given packed position is fully loaded and ready for physics simulation.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return True if the chunk is ready, false otherwise.
     */
    public boolean isReady(long packedPos) {
        return terrainManager.isReady(packedPos);
    }

    /**
     * Checks if a terrain chunk at a given position is using a placeholder shape.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return True if the chunk is using a placeholder, false otherwise.
     */
    public boolean isPlaceholder(long packedPos) {
        return terrainManager.isPlaceholder(packedPos);
    }

    /**
     * Checks if a terrain chunk is ready based on its SectionPos.
     *
     * @param sectionPos The SectionPos of the chunk.
     * @return True if the chunk is ready.
     */
    public boolean isSectionReady(SectionPos sectionPos) {
        if (sectionPos == null) {
            return false;
        }
        return isReady(sectionPos.asLong());
    }

    /**
     * Determines if a given physics body ID belongs to a terrain chunk.
     *
     * @param bodyId The ID of the physics body.
     * @return True if the body is a terrain body, false otherwise.
     */
    public boolean isTerrainBody(int bodyId) {
        if (bodyId <= 0) return false;
        // Uses a thread-safe copy of the body ID array for safe iteration.
        for (int id : chunkDataStore.getBodyIds()) {
            if (id == bodyId) return true;
        }
        return false;
    }

    /**
     * Gets the Minecraft ServerLevel this terrain system is associated with.
     *
     * @return The ServerLevel instance.
     */
    public ServerLevel getLevel() {
        return level;
    }
}