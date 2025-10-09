/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.terrain.cache.VxTerrainShapeCache;
import net.xmx.velthoric.physics.terrain.data.VxChunkDataStore;
import net.xmx.velthoric.physics.terrain.generation.VxGreedyMesher;
import net.xmx.velthoric.physics.terrain.generation.VxTerrainShapeGenerator;
import net.xmx.velthoric.physics.terrain.job.VxTerrainJobSystem;
import net.xmx.velthoric.physics.terrain.management.VxTerrainManager;
import net.xmx.velthoric.physics.terrain.management.VxTerrainTracker;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The main orchestrator for the terrain physics system. It initializes, manages, and shuts down
 * all related components, including the terrain manager, object tracker, and shape generator.
 *
 * @author xI-Mx-Ix
 */
public class VxTerrainSystem implements Runnable {

    private final ServerLevel level;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final Thread workerThread;

    private final VxTerrainJobSystem jobSystem;
    private final VxChunkDataStore chunkDataStore;
    private final VxTerrainShapeCache shapeCache;
    private final VxGreedyMesher greedyMesher;
    private final VxTerrainShapeGenerator shapeGenerator;
    private final VxTerrainManager terrainManager;
    private final VxTerrainTracker terrainTracker;

    public VxTerrainSystem(VxPhysicsWorld physicsWorld, ServerLevel level) {
        this.level = level;

        this.jobSystem = new VxTerrainJobSystem();
        this.chunkDataStore = new VxChunkDataStore();
        this.shapeCache = new VxTerrainShapeCache(2048); // Increased cache size
        this.greedyMesher = new VxGreedyMesher(this.shapeCache);
        this.shapeGenerator = new VxTerrainShapeGenerator(physicsWorld, level, jobSystem, chunkDataStore, greedyMesher);
        this.terrainManager = new VxTerrainManager(physicsWorld, level, chunkDataStore, shapeGenerator);
        this.terrainTracker = new VxTerrainTracker(physicsWorld, terrainManager, chunkDataStore);

        this.workerThread = new Thread(this, "Velthoric Terrain Worker - " + level.dimension().location().getPath());
        this.workerThread.setDaemon(true);
    }

    /**
     * Initializes the terrain system and starts its worker thread.
     */
    public void initialize() {
        if (isInitialized.compareAndSet(false, true)) {
            this.workerThread.start();
        }
    }

    /**
     * Shuts down the terrain system, stops the worker thread, and cleans up all resources.
     */
    public void shutdown() {
        if (isInitialized.compareAndSet(true, false)) {
            jobSystem.shutdown();
            workerThread.interrupt();
            VxMainClass.LOGGER.debug("Shutting down Terrain System for '{}'.", level.dimension().location());

            try {
                workerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            terrainManager.cleanupAllBodies();
            chunkDataStore.clear();
            terrainTracker.clear();
            shapeCache.clear();

            VxMainClass.LOGGER.debug("Terrain system for '{}' has been fully shut down.", level.dimension().location());
        }
    }

    /**
     * The main loop for the worker thread, which periodically updates the terrain system.
     */
    @Override
    public void run() {
        while (isInitialized.get() && !Thread.currentThread().isInterrupted()) {
            try {
                terrainTracker.update();
                terrainManager.processRebuildQueue();
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Error in TerrainSystem worker thread", e);
            }
        }
    }

    // --- Public API ---

    public void onBlockUpdate(BlockPos worldPos) {
        if (isInitialized.get()) {
            terrainManager.onBlockUpdate(worldPos);
        }
    }

    public void onChunkLoadedFromVanilla(@NotNull LevelChunk chunk) {
        if (isInitialized.get() && !jobSystem.isShutdown()) {
            // The manager logic is now submitted to the job system to avoid blocking the main thread.
            jobSystem.submit(() -> terrainManager.onChunkLoadedFromVanilla(chunk));
        }
    }

    public void onChunkUnloaded(@NotNull ChunkPos chunkPos) {
        if (isInitialized.get() && !jobSystem.isShutdown()) {
            jobSystem.submit(() -> terrainManager.onChunkUnloaded(chunkPos));
        }
    }

    public void onObjectRemoved(UUID id) {
        if (isInitialized.get() && !jobSystem.isShutdown()) {
            jobSystem.submit(() -> terrainTracker.removeObjectTracking(id));
        }
    }

    public boolean isTerrainBody(int bodyId) {
        return terrainManager.isTerrainBody(bodyId);
    }

    public ServerLevel getLevel() {
        return level;
    }
}