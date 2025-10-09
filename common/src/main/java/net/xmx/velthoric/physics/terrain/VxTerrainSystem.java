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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The main orchestrator for the terrain physics system. It initializes, manages, and shuts down
 * all related components, following a proactive, state-driven terrain management approach.
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
        this.shapeCache = new VxTerrainShapeCache(2048);
        this.greedyMesher = new VxGreedyMesher(this.shapeCache);

        // The circular dependency is resolved, so we can instantiate in the correct order.
        this.shapeGenerator = new VxTerrainShapeGenerator(physicsWorld, level, jobSystem, chunkDataStore, greedyMesher);
        this.terrainManager = new VxTerrainManager(physicsWorld, level, chunkDataStore, this.shapeGenerator);

        this.terrainTracker = new VxTerrainTracker(physicsWorld, this.terrainManager);

        this.workerThread = new Thread(this, "Velthoric Terrain Worker - " + level.dimension().location().getPath());
        this.workerThread.setDaemon(true);
    }

    public void initialize() {
        if (isInitialized.compareAndSet(false, true)) {
            this.workerThread.start();
        }
    }

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
            shapeCache.clear();

            VxMainClass.LOGGER.debug("Terrain system for '{}' has been fully shut down.", level.dimension().location());
        }
    }

    @Override
    public void run() {
        while (isInitialized.get() && !Thread.currentThread().isInterrupted()) {
            try {
                terrainTracker.update();
                terrainManager.tick();
                shapeGenerator.tick();
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Error in TerrainSystem worker thread", e);
            }
        }
    }

    public void onBlockUpdate(BlockPos worldPos) {
        if (isInitialized.get()) {
            terrainManager.onBlockUpdate(worldPos);
        }
    }

    public void onChunkLoadedFromVanilla(@NotNull LevelChunk chunk) {
        if (isInitialized.get() && !jobSystem.isShutdown()) {
            jobSystem.submit(() -> terrainManager.onChunkLoadedFromVanilla(chunk));
        }
    }

    public void onChunkUnloaded(@NotNull ChunkPos chunkPos) {
        if (isInitialized.get() && !jobSystem.isShutdown()) {
            jobSystem.submit(() -> terrainManager.onChunkUnloaded(chunkPos));
        }
    }

    public boolean isTerrainBody(int bodyId) {
        return terrainManager.isTerrainBody(bodyId);
    }

    public ServerLevel getLevel() {
        return level;
    }

    public VxChunkDataStore getChunkDataStore() {
        return chunkDataStore;
    }
}