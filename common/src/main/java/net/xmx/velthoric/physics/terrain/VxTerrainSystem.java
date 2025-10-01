/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain;

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.terrain.cache.VxTerrainShapeCache;
import net.xmx.velthoric.physics.terrain.chunk.VxSectionPos;
import net.xmx.velthoric.physics.terrain.chunk.VxUpdateContext;
import net.xmx.velthoric.physics.terrain.job.VxTerrainJobSystem;
import net.xmx.velthoric.physics.terrain.management.VxChunkManager;
import net.xmx.velthoric.physics.terrain.management.VxObjectTracker;
import net.xmx.velthoric.physics.terrain.management.VxShapeGenerationQueue;
import net.xmx.velthoric.physics.terrain.persistence.VxTerrainStorage;
import net.xmx.velthoric.physics.terrain.chunk.VxTerrainGenerator;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The main orchestrator for the terrain physics system.
 * <p>
 * This class serves as the public facade for the terrain system. It initializes and
 * coordinates the various subsystems (chunk management, shape generation, object tracking)
 * and manages the main worker thread that drives all asynchronous updates.
 *
 * @author xI-Mx-Ix
 */
public class VxTerrainSystem implements Runnable {

    private final VxPhysicsWorld physicsWorld;
    private final ServerLevel level;
    private final VxTerrainStorage terrainStorage;
    private final VxTerrainJobSystem jobSystem;
    private final Thread workerThread;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    private final VxChunkDataStore chunkDataStore;
    private final VxChunkManager chunkManager;
    private final VxShapeGenerationQueue shapeGenerationQueue;
    private final VxObjectTracker objectTracker;

    private static final ThreadLocal<VxUpdateContext> updateContext = ThreadLocal.withInitial(VxUpdateContext::new);

    public VxTerrainSystem(VxPhysicsWorld physicsWorld, ServerLevel level) {
        this.physicsWorld = physicsWorld;
        this.level = level;

        // Initialize core data and services
        this.chunkDataStore = new VxChunkDataStore();
        this.jobSystem = new VxTerrainJobSystem();
        this.terrainStorage = new VxTerrainStorage(this.level);
        VxTerrainShapeCache shapeCache = new VxTerrainShapeCache(1024);
        VxTerrainGenerator terrainGenerator = new VxTerrainGenerator(shapeCache, this.terrainStorage);

        // Initialize management subsystems, resolving circular dependency via setter injection.
        this.chunkManager = new VxChunkManager(physicsWorld, chunkDataStore);
        this.shapeGenerationQueue = new VxShapeGenerationQueue(level, jobSystem, chunkDataStore, terrainGenerator, chunkManager);
        this.objectTracker = new VxObjectTracker(physicsWorld, level, chunkManager, chunkDataStore, jobSystem);
        this.chunkManager.setShapeGenerationQueue(this.shapeGenerationQueue);

        this.workerThread = new Thread(this, "Velthoric Terrain Tracker - " + level.dimension().location().getPath());
        this.workerThread.setDaemon(true);
    }

    public void initialize() {
        if (isInitialized.compareAndSet(false, true)) {
            this.terrainStorage.initialize();
            this.workerThread.start();
        }
    }

    public void shutdown() {
        if (isInitialized.compareAndSet(true, false)) {
            jobSystem.shutdown();
            workerThread.interrupt();

            VxMainClass.LOGGER.debug("Shutting down Terrain Tracker for '{}'. Waiting up to 30 seconds for worker thread to exit...", level.dimension().location());
            try {
                workerThread.join(30000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                VxMainClass.LOGGER.warn("Interrupted while waiting for terrain tracker thread to stop.");
            }

            if (workerThread.isAlive()) {
                VxMainClass.LOGGER.error("Terrain tracker thread for '{}' did not terminate gracefully.", level.dimension().location());
            }

            // Final cleanup on the calling thread
            chunkDataStore.clear();
            this.terrainStorage.shutdown();
            VxMainClass.LOGGER.debug("Terrain system for '{}' has been fully shut down.", level.dimension().location());
        }
    }

    @Override
    public void run() {
        while (isInitialized.get() && !Thread.currentThread().isInterrupted()) {
            try {
                objectTracker.update();
                shapeGenerationQueue.processRebuildQueue();
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Error in TerrainSystem worker thread for dimension '{}'", level.dimension().location(), e);
            }
        }
    }

    public void saveDirtyRegions() {
        if (terrainStorage != null) {
            terrainStorage.saveDirtyRegions();
        }
    }

    public void onBlockUpdate(BlockPos worldPos, BlockState oldState, BlockState newState) {
        if (!isInitialized.get()) return;

        VxSectionPos pos = VxSectionPos.fromBlockPos(worldPos.immutable());
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index == null) return;

        int currentState = chunkDataStore.states[index];
        if (currentState == VxChunkManager.STATE_READY_ACTIVE || currentState == VxChunkManager.STATE_READY_INACTIVE || currentState == VxChunkManager.STATE_AIR_CHUNK) {
            shapeGenerationQueue.scheduleRebuild(pos);
        }

        physicsWorld.execute(() -> {
            BodyInterface bi = physicsWorld.getBodyInterface();
            if (bi == null) return;
            VxUpdateContext ctx = updateContext.get();
            ctx.aabox_1.setMin(new Vec3(worldPos.getX() - 2f, worldPos.getY() - 2f, worldPos.getZ() - 2f));
            ctx.aabox_1.setMax(new Vec3(worldPos.getX() + 3f, worldPos.getY() + 3f, worldPos.getZ() + 3f));
            bi.activateBodiesInAaBox(ctx.aabox_1, ctx.bplFilter, ctx.olFilter);
        });
    }

    public boolean isSectionReady(SectionPos sectionPos) {
        if (sectionPos == null) return false;
        VxSectionPos pos = new VxSectionPos(sectionPos.x(), sectionPos.y(), sectionPos.z());
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index == null) return false;
        int state = chunkDataStore.states[index];
        return state == VxChunkManager.STATE_READY_ACTIVE || state == VxChunkManager.STATE_READY_INACTIVE || state == VxChunkManager.STATE_AIR_CHUNK;
    }

    public ServerLevel getLevel() {
        return level;
    }
}