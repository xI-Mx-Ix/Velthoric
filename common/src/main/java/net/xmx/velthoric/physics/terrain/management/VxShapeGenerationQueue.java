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
import net.xmx.velthoric.physics.terrain.chunk.VxSectionPos;
import net.xmx.velthoric.physics.terrain.chunk.VxChunkSnapshot;
import net.xmx.velthoric.physics.terrain.job.VxTaskPriority;
import net.xmx.velthoric.physics.terrain.job.VxTerrainJobSystem;
import net.xmx.velthoric.physics.terrain.chunk.VxTerrainGenerator;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the asynchronous generation of terrain physics shapes.
 * <p>
 * This class handles the entire pipeline from scheduling a chunk for an update,
 * taking a snapshot of its block data on the main thread, generating the shape on a
 * worker thread, and finally applying the result via the {@link VxChunkManager} on the
 * physics thread. It uses a versioning system to prevent race conditions from outdated tasks.
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
     * Processes a batch of chunks from the rebuild queue.
     */
    public void processRebuildQueue() {
        if (chunksToRebuild.isEmpty()) return;

        Set<VxSectionPos> batch = new HashSet<>(chunksToRebuild);
        chunksToRebuild.removeAll(batch);

        for (VxSectionPos pos : batch) {
            Integer index = chunkDataStore.getIndexForPos(pos);
            if (index != null) {
                scheduleShapeGeneration(pos, index, false, VxTaskPriority.MEDIUM);
            }
        }
    }

    /**
     * Schedules the asynchronous generation of a physics shape for a chunk.
     *
     * @param pos            The position of the chunk section.
     * @param index          The data store index for the chunk.
     * @param isInitialBuild True if this is the first time a shape is being built.
     * @param priority       The priority of the task.
     */
    public void scheduleShapeGeneration(VxSectionPos pos, int index, boolean isInitialBuild, VxTaskPriority priority) {
        if (jobSystem.isShutdown()) return;

        int currentState = chunkDataStore.states[index];
        if (currentState == VxChunkManager.STATE_REMOVING || currentState == VxChunkManager.STATE_LOADING_SCHEDULED || currentState == VxChunkManager.STATE_GENERATING_SHAPE) {
            return;
        }

        if (chunkDataStore.states[index] == currentState) {
            chunkDataStore.states[index] = VxChunkManager.STATE_LOADING_SCHEDULED;
            final int version = ++chunkDataStore.rebuildVersions[index];

            level.getServer().execute(() -> takeSnapshotAndSubmit(pos, index, version, isInitialBuild, currentState));
        }
    }

    /**
     * Takes a snapshot of the chunk on the main server thread and submits the generation task.
     */
    private void takeSnapshotAndSubmit(VxSectionPos pos, int index, int version, boolean isInitialBuild, int previousState) {
        if (version < chunkDataStore.rebuildVersions[index] || chunkDataStore.states[index] != VxChunkManager.STATE_LOADING_SCHEDULED) {
            if (chunkDataStore.states[index] == VxChunkManager.STATE_LOADING_SCHEDULED) chunkDataStore.states[index] = previousState;
            return;
        }

        LevelChunk chunk = level.getChunkSource().getChunk(pos.x(), pos.z(), false);
        if (chunk == null) {
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
     */
    private void processShapeGenerationOnWorker(VxSectionPos pos, int index, int version, VxChunkSnapshot snapshot, boolean isInitialBuild) {
        if (version < chunkDataStore.rebuildVersions[index]) {
            // A newer task has been scheduled, so this one is obsolete.
            return;
        }

        try {
            @Nullable ShapeRefC generatedShape = terrainGenerator.generateShape(level, snapshot);
            // The 'generatedShape' is a new resource. Ownership is transferred to the chunk manager.
            chunkManager.applyGeneratedShape(pos, index, generatedShape, isInitialBuild);
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Exception during terrain shape generation for {}", pos, e);
            chunkDataStore.states[index] = VxChunkManager.STATE_UNLOADED;
        }
    }
}