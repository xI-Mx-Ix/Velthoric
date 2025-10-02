/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.management;

import com.github.stephengold.joltjni.ShapeRefC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.terrain.VxChunkDataStore;
import net.xmx.velthoric.physics.terrain.chunk.VxChunkSnapshot;
import net.xmx.velthoric.physics.terrain.chunk.VxSectionPos;
import net.xmx.velthoric.physics.terrain.chunk.VxTerrainGenerator;
import net.xmx.velthoric.physics.terrain.job.VxTaskPriority;
import net.xmx.velthoric.physics.terrain.job.VxTerrainJobSystem;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the asynchronous generation of terrain physics shapes.
 * <p>
 * This class handles the entire pipeline from scheduling a chunk for an update,
 * taking a snapshot of its block data on the main thread, generating the shape on a
 * worker thread, and finally applying the result via the {@link VxChunkManager} on the
 * physics thread. It uses a versioning system to prevent race conditions from outdated tasks
 * and handles failures gracefully to ensure system stability.
 *
 * @author xI-Mx-Ix
 */
public class VxShapeGenerationQueue {

    private final ServerLevel level;
    private final VxTerrainJobSystem jobSystem;
    private final VxChunkDataStore chunkDataStore;
    private final VxTerrainGenerator terrainGenerator;
    private final VxChunkManager chunkManager;
    private final Set<VxSectionPos> chunksToRebuild = ConcurrentHashMap.newKeySet();

    public VxShapeGenerationQueue(ServerLevel level, VxTerrainJobSystem jobSystem, VxChunkDataStore chunkDataStore, VxTerrainGenerator terrainGenerator, VxChunkManager chunkManager) {
        this.level = level;
        this.jobSystem = jobSystem;
        this.chunkDataStore = chunkDataStore;
        this.terrainGenerator = terrainGenerator;
        this.chunkManager = chunkManager;
    }

    /**
     * Adds a chunk position to the queue for a shape rebuild.
     *
     * @param pos The position of the chunk section to rebuild.
     */
    public void scheduleRebuild(VxSectionPos pos) {
        chunksToRebuild.add(pos);
    }

    /**
     * Processes a batch of chunks from the rebuild queue. This is designed to be thread-safe
     * by atomically removing a batch of items to process.
     */
    public void processRebuildQueue() {
        if (chunksToRebuild.isEmpty()) return;

        Iterator<VxSectionPos> iterator = chunksToRebuild.iterator();
        while (iterator.hasNext()) {
            VxSectionPos pos = iterator.next();
            Integer index = chunkDataStore.getIndexForPos(pos);
            if (index != null) {
                scheduleShapeGeneration(pos, index, false, VxTaskPriority.MEDIUM);
            }
            iterator.remove();
        }
    }

    /**
     * Schedules the asynchronous generation of a physics shape for a chunk.
     * This method is the entry point for both initial loads and rebuilds.
     *
     * @param pos            The position of the chunk section.
     * @param index          The data store index for the chunk.
     * @param isInitialBuild True if this is the first time a shape is being built.
     * @param priority       The priority of the task.
     */
    public void scheduleShapeGeneration(VxSectionPos pos, int index, boolean isInitialBuild, VxTaskPriority priority) {
        if (jobSystem.isShutdown()) return;

        int currentState = chunkDataStore.states[index];

        if (currentState == VxChunkManager.STATE_LOADING_SCHEDULED || currentState == VxChunkManager.STATE_GENERATING_SHAPE || currentState == VxChunkManager.STATE_REMOVING) {
            return;
        }

        if (chunkDataStore.states[index] == currentState) {
            chunkDataStore.states[index] = VxChunkManager.STATE_LOADING_SCHEDULED;
            final int version = ++chunkDataStore.rebuildVersions[index];

            level.getServer().execute(() -> takeSnapshotAndSubmit(pos, index, version, isInitialBuild));
        }
    }

    /**
     * Takes a snapshot of the chunk on the main server thread and submits the generation task.
     * This is the last point of control on the main thread before going async.
     */
    private void takeSnapshotAndSubmit(VxSectionPos pos, int index, int version, boolean isInitialBuild) {
        if (version < chunkDataStore.rebuildVersions[index] || chunkDataStore.states[index] != VxChunkManager.STATE_LOADING_SCHEDULED) {
            return;
        }

        LevelChunk chunk = level.getChunkSource().getChunk(pos.x(), pos.z(), false);
        if (chunk == null) {
            // This is a common and expected case during server startup. The chunk is not yet loaded in memory.
            // Instead of treating this as a failure, we reset the state. The ObjectTracker's main loop
            // will naturally re-request the chunk on its next 50ms cycle. This provides a natural,
            // throttled retry mechanism and avoids log spam.
            chunkDataStore.states[index] = VxChunkManager.STATE_UNLOADED;
            return;
        }

        VxChunkSnapshot snapshot = VxChunkSnapshot.snapshotFromChunk(level, chunk, pos);

        if (chunkDataStore.states[index] == VxChunkManager.STATE_LOADING_SCHEDULED) {
            chunkDataStore.states[index] = VxChunkManager.STATE_GENERATING_SHAPE;
            jobSystem.submit(() -> processShapeGenerationOnWorker(pos, index, version, snapshot, isInitialBuild));
        }
    }

    /**
     * Generates the shape on a worker thread and schedules the result to be applied.
     * This runs completely asynchronously.
     */
    private void processShapeGenerationOnWorker(VxSectionPos pos, int index, int version, VxChunkSnapshot snapshot, boolean isInitialBuild) {
        if (version < chunkDataStore.rebuildVersions[index] || chunkDataStore.states[index] == VxChunkManager.STATE_REMOVING) {
            return;
        }

        try {
            @Nullable ShapeRefC generatedShape = terrainGenerator.generateShape(level, snapshot);
            chunkManager.applyGeneratedShape(pos, index, version, generatedShape, isInitialBuild);
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Exception during terrain shape generation for {}", pos, e);
            chunkManager.handleGenerationFailure(pos, index, version);
        }
    }
}