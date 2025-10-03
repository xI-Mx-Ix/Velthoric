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

        int currentState = chunkDataStore.states[index];
        if (chunkDataStore.states[index] == currentState) {
            chunkDataStore.states[index] = VxChunkDataStore.STATE_LOADING_SCHEDULED;
            final int version = ++chunkDataStore.rebuildVersions[index];

            // Schedule chunk loading on the main server thread
            level.getServer().execute(() -> {
                if (version < chunkDataStore.rebuildVersions[index] || jobSystem.isShutdown()) {
                    if (chunkDataStore.states[index] == VxChunkDataStore.STATE_LOADING_SCHEDULED) chunkDataStore.states[index] = currentState;
                    return;
                }

                LevelChunk chunk = level.getChunkSource().getChunk(pos.x(), pos.z(), false);
                if (chunk == null) {
                    chunkDataStore.states[index] = VxChunkDataStore.STATE_UNLOADED;
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
        boolean wasActive = chunkDataStore.states[index] == VxChunkDataStore.STATE_READY_ACTIVE;

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

        int bodyId = chunkDataStore.bodyIds[index];
        if (bodyId != VxChunkDataStore.UNUSED_BODY_ID) {
            if (shape != null) {
                bodyInterface.setShape(bodyId, shape, true, EActivation.DontActivate);
                chunkDataStore.setShape(index, shape);
                chunkDataStore.states[index] = wasActive ? VxChunkDataStore.STATE_READY_ACTIVE : VxChunkDataStore.STATE_READY_INACTIVE;
            } else {
                // The chunk is now empty, remove its body
                if (bodyInterface.isAdded(bodyId)) bodyInterface.removeBody(bodyId);
                bodyInterface.destroyBody(bodyId);
                chunkDataStore.bodyIds[index] = VxChunkDataStore.UNUSED_BODY_ID;
                chunkDataStore.setShape(index, null);
                chunkDataStore.states[index] = VxChunkDataStore.STATE_AIR_CHUNK;
            }
        } else if (shape != null) {
            // A new body needs to be created for this chunk
            RVec3 position = new RVec3(pos.getOrigin().getX(), pos.getOrigin().getY(), pos.getOrigin().getZ());
            try (BodyCreationSettings bcs = new BodyCreationSettings(shape, position, Quat.sIdentity(), EMotionType.Static, VxLayers.TERRAIN)) {
                Body body = bodyInterface.createBody(bcs);
                if (body != null) {
                    body.setFriction(0.65f);
                    chunkDataStore.bodyIds[index] = body.getId();
                    chunkDataStore.setShape(index, shape);
                    chunkDataStore.states[index] = wasActive ? VxChunkDataStore.STATE_READY_ACTIVE : VxChunkDataStore.STATE_READY_INACTIVE;
                } else {
                    VxMainClass.LOGGER.error("Failed to create terrain body for chunk {}", pos);
                    shape.close();
                    chunkDataStore.states[index] = VxChunkDataStore.STATE_UNLOADED;
                }
            }
        } else {
            // The chunk was and remains empty
            chunkDataStore.states[index] = VxChunkDataStore.STATE_AIR_CHUNK;
        }

        if (wasActive) {
            int finalBodyId = chunkDataStore.bodyIds[index];
            if (finalBodyId != VxChunkDataStore.UNUSED_BODY_ID && !bodyInterface.isAdded(finalBodyId)) {
                bodyInterface.addBody(finalBodyId, EActivation.Activate);
            }
        }
    }
}