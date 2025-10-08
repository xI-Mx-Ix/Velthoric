/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.generation;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.natives.VxLayers;
import net.xmx.velthoric.physics.terrain.chunk.VxChunkSnapshot;
import net.xmx.velthoric.physics.terrain.data.VxChunkDataStore;
import net.xmx.velthoric.physics.terrain.data.VxSectionPos;
import net.xmx.velthoric.physics.terrain.job.VxTerrainJobSystem;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * Manages the asynchronous generation of terrain physics shapes. It schedules tasks on a job system
 * and applies the generated shapes to the physics world on the main thread.
 *
 * @author xI-Mx-Ix
 */
public class VxTerrainShapeGenerator {

    private final VxPhysicsWorld physicsWorld;
    private final ServerLevel level;
    private final VxTerrainJobSystem jobSystem;
    private final VxChunkDataStore chunkDataStore;
    private final VxGreedyMesher greedyMesher;

    public VxTerrainShapeGenerator(VxPhysicsWorld physicsWorld, ServerLevel level, VxTerrainJobSystem jobSystem, VxChunkDataStore chunkDataStore, VxGreedyMesher greedyMesher) {
        this.physicsWorld = physicsWorld;
        this.level = level;
        this.jobSystem = jobSystem;
        this.chunkDataStore = chunkDataStore;
        this.greedyMesher = greedyMesher;
    }

    /**
     * Schedules the generation of a physics shape for a specific chunk section.
     *
     * @param pos      The position of the chunk section.
     * @param index    The data store index for the section.
     */
    public void scheduleShapeGeneration(VxSectionPos pos, int index) {
        if (jobSystem.isShutdown()) {
            return;
        }

        final int currentState = chunkDataStore.states[index];
        // Only schedule if the chunk is in a state that allows rebuilding.
        if (currentState == VxChunkDataStore.STATE_UNLOADED || currentState == VxChunkDataStore.STATE_READY_INACTIVE ||
                currentState == VxChunkDataStore.STATE_READY_ACTIVE || currentState == VxChunkDataStore.STATE_AIR_CHUNK) {

            chunkDataStore.states[index] = VxChunkDataStore.STATE_LOADING_SCHEDULED;
            final int version = ++chunkDataStore.rebuildVersions[index];

            // Schedule chunk loading on the main server thread
            level.getServer().execute(() -> {
                // Abort if a newer version is already requested or the system is shutting down.
                if (version < chunkDataStore.rebuildVersions[index] || jobSystem.isShutdown()) {
                    if (chunkDataStore.states[index] == VxChunkDataStore.STATE_LOADING_SCHEDULED) {
                        chunkDataStore.states[index] = currentState;
                    }
                    return;
                }

                LevelChunk chunk = level.getChunkSource().getChunk(pos.x(), pos.z(), false);
                if (chunk == null) {
                    // If the chunk is not loaded yet, revert the state and wait for onChunkLoadedFromVanilla to trigger generation.
                    // This prevents "ghost chunks" where generation fails because the chunk wasn't ready.
                    if (chunkDataStore.states[index] == VxChunkDataStore.STATE_LOADING_SCHEDULED) {
                        chunkDataStore.states[index] = currentState;
                    }
                    return;
                }

                VxChunkSnapshot snapshot = VxChunkSnapshot.snapshotFromChunk(level, chunk, pos);
                if (chunkDataStore.states[index] == VxChunkDataStore.STATE_LOADING_SCHEDULED) {
                    chunkDataStore.states[index] = VxChunkDataStore.STATE_GENERATING_SHAPE;
                    jobSystem.submit(() -> processShapeGenerationOnWorker(pos, index, version, snapshot, currentState));
                }
            });
        }
    }

    /**
     * Executes the shape generation on a worker thread.
     *
     * @param pos           The position of the chunk section.
     * @param index         The data store index.
     * @param version       The generation version to prevent race conditions.
     * @param snapshot      The snapshot of the chunk data.
     * @param previousState The state before generation was scheduled.
     */
    private void processShapeGenerationOnWorker(VxSectionPos pos, int index, int version, VxChunkSnapshot snapshot, int previousState) {
        if (version < chunkDataStore.rebuildVersions[index]) {
            if (chunkDataStore.states[index] == VxChunkDataStore.STATE_GENERATING_SHAPE) chunkDataStore.states[index] = previousState;
            return;
        }

        try {
            ShapeRefC generatedShape = greedyMesher.generateShape(snapshot);
            physicsWorld.execute(() -> applyGeneratedShape(pos, index, version, generatedShape));
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Exception during terrain shape generation for {}", pos, e);
            chunkDataStore.states[index] = VxChunkDataStore.STATE_UNLOADED;
        }
    }

    /**
     * Applies the newly generated shape to the physics world on the main thread.
     *
     * @param pos     The position of the chunk section.
     * @param index   The data store index.
     * @param version The generation version.
     * @param shape   The generated shape, or null if the section is empty.
     */
    private void applyGeneratedShape(VxSectionPos pos, int index, int version, ShapeRefC shape) {
        // Abort if a newer version is pending or the chunk is being removed.
        if (version < chunkDataStore.rebuildVersions[index] || chunkDataStore.states[index] == VxChunkDataStore.STATE_REMOVING) {
            if (shape != null) shape.close();
            if (chunkDataStore.states[index] != VxChunkDataStore.STATE_REMOVING) chunkDataStore.states[index] = VxChunkDataStore.STATE_UNLOADED;
            return;
        }

        BodyInterface bodyInterface = physicsWorld.getBodyInterface();
        if (bodyInterface == null) {
            if (shape != null) shape.close();
            chunkDataStore.states[index] = VxChunkDataStore.STATE_UNLOADED;
            return;
        }

        int oldBodyId = chunkDataStore.bodyIds[index];
        boolean wasAddedToSimulation = oldBodyId != VxChunkDataStore.UNUSED_BODY_ID && bodyInterface.isAdded(oldBodyId);

        // If the body already exists, handle its update.
        if (oldBodyId != VxChunkDataStore.UNUSED_BODY_ID) {
            // If it was active, remove it from the simulation before changing the shape for safety.
            if (wasAddedToSimulation) {
                bodyInterface.removeBody(oldBodyId);
            }

            if (shape != null) {
                // A new shape exists, so update the body.
                bodyInterface.setShape(oldBodyId, shape, true, EActivation.DontActivate);
                chunkDataStore.setShape(index, shape);
                chunkDataStore.states[index] = wasAddedToSimulation ? VxChunkDataStore.STATE_READY_ACTIVE : VxChunkDataStore.STATE_READY_INACTIVE;
            } else {
                // The chunk is now empty, so destroy the old body.
                bodyInterface.destroyBody(oldBodyId);
                chunkDataStore.bodyIds[index] = VxChunkDataStore.UNUSED_BODY_ID;
                chunkDataStore.setShape(index, null);
                chunkDataStore.states[index] = VxChunkDataStore.STATE_AIR_CHUNK;
            }
        } else if (shape != null) {
            // A body does not exist, but a shape was generated, so create a new body.
            RVec3 position = new RVec3(pos.getOrigin().getX(), pos.getOrigin().getY(), pos.getOrigin().getZ());
            try (BodyCreationSettings bcs = new BodyCreationSettings(shape, position, Quat.sIdentity(), EMotionType.Static, VxLayers.TERRAIN)) {
                Body newBody = bodyInterface.createBody(bcs);
                if (newBody != null) {
                    chunkDataStore.bodyIds[index] = newBody.getId();
                    chunkDataStore.setShape(index, shape);
                    chunkDataStore.states[index] = wasAddedToSimulation ? VxChunkDataStore.STATE_READY_ACTIVE : VxChunkDataStore.STATE_READY_INACTIVE;
                } else {
                    VxMainClass.LOGGER.error("Failed to create terrain body for chunk {}", pos);
                    shape.close();
                    chunkDataStore.states[index] = VxChunkDataStore.STATE_UNLOADED;
                }
            }
        } else {
            // The chunk was empty and remains empty.
            chunkDataStore.states[index] = VxChunkDataStore.STATE_AIR_CHUNK;
        }

        // If the chunk was active before the update and still has a body, re-add it to the simulation.
        int finalBodyId = chunkDataStore.bodyIds[index];
        if (wasAddedToSimulation && finalBodyId != VxChunkDataStore.UNUSED_BODY_ID) {
            bodyInterface.addBody(finalBodyId, EActivation.Activate);
        }
    }
}