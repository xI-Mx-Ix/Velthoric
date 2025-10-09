/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.generation;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.natives.VxLayers;
import net.xmx.velthoric.physics.terrain.chunk.VxChunkSnapshot;
import net.xmx.velthoric.physics.terrain.data.VxChunkDataStore;
import net.xmx.velthoric.physics.terrain.data.VxSectionPos;
import net.xmx.velthoric.physics.terrain.job.VxTerrainJobSystem;
import net.xmx.velthoric.physics.terrain.management.VxTerrainManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * Manages the asynchronous generation of terrain physics shapes. It accepts packed
 * long positions to schedule tasks and applies the generated shapes to the physics world.
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
     * @param packedPos The packed long position of the chunk section.
     * @param index     The data store index for the section.
     */
    public void scheduleShapeGeneration(long packedPos, int index) {
        if (jobSystem.isShutdown()) {
            return;
        }

        final int currentState = chunkDataStore.states[index];
        if (currentState == VxChunkDataStore.STATE_UNLOADED || currentState == VxChunkDataStore.STATE_READY_INACTIVE ||
                currentState == VxChunkDataStore.STATE_READY_ACTIVE || currentState == VxChunkDataStore.STATE_AIR_CHUNK) {

            chunkDataStore.states[index] = VxChunkDataStore.STATE_LOADING_SCHEDULED;
            final int version = ++chunkDataStore.rebuildVersions[index];

            jobSystem.submit(() -> {
                if (version < chunkDataStore.rebuildVersions[index] || jobSystem.isShutdown()) {
                    if (chunkDataStore.states[index] == VxChunkDataStore.STATE_LOADING_SCHEDULED) {
                        chunkDataStore.states[index] = currentState;
                    }
                    return;
                }

                int chunkX = VxSectionPos.unpackX(packedPos);
                int chunkZ = VxSectionPos.unpackZ(packedPos);

                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (chunk == null) {
                    if (chunkDataStore.states[index] == VxChunkDataStore.STATE_LOADING_SCHEDULED) {
                        chunkDataStore.states[index] = currentState;
                    }
                    return;
                }

                VxChunkSnapshot snapshot = VxChunkSnapshot.snapshotFromChunk(level, chunk, packedPos);
                if (chunkDataStore.states[index] == VxChunkDataStore.STATE_LOADING_SCHEDULED) {
                    chunkDataStore.states[index] = VxChunkDataStore.STATE_GENERATING_SHAPE;
                    processShapeGenerationOnWorker(packedPos, index, version, snapshot, currentState);
                }
            });
        }
    }

    private void processShapeGenerationOnWorker(long packedPos, int index, int version, VxChunkSnapshot snapshot, int previousState) {
        if (version < chunkDataStore.rebuildVersions[index]) {
            if (chunkDataStore.states[index] == VxChunkDataStore.STATE_GENERATING_SHAPE) chunkDataStore.states[index] = previousState;
            return;
        }

        try {
            ShapeRefC generatedShape = greedyMesher.generateShape(snapshot);
            physicsWorld.execute(() -> applyGeneratedShape(packedPos, index, version, generatedShape));
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Exception during terrain shape generation for {}", VxSectionPos.unpackToSectionPos(packedPos), e);
            chunkDataStore.states[index] = VxChunkDataStore.STATE_UNLOADED;
        }
    }

    private void applyGeneratedShape(long packedPos, int index, int version, ShapeRefC shape) {
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

        if (oldBodyId != VxChunkDataStore.UNUSED_BODY_ID) {
            if (wasAddedToSimulation) {
                bodyInterface.removeBody(oldBodyId);
            }

            if (shape != null) {
                bodyInterface.setShape(oldBodyId, shape, true, EActivation.DontActivate);
                chunkDataStore.setShape(index, shape);
                chunkDataStore.states[index] = wasAddedToSimulation ? VxChunkDataStore.STATE_READY_ACTIVE : VxChunkDataStore.STATE_READY_INACTIVE;
            } else {
                bodyInterface.destroyBody(oldBodyId);
                chunkDataStore.bodyIds[index] = VxChunkDataStore.UNUSED_BODY_ID;
                chunkDataStore.setShape(index, null);
                chunkDataStore.states[index] = VxChunkDataStore.STATE_AIR_CHUNK;
            }
        } else if (shape != null) {
            BlockPos origin = VxSectionPos.unpackToOrigin(packedPos);
            RVec3 position = new RVec3(origin.getX(), origin.getY(), origin.getZ());
            try (BodyCreationSettings bcs = new BodyCreationSettings(shape, position, Quat.sIdentity(), EMotionType.Static, VxLayers.TERRAIN)) {
                bcs.setUserData(VxTerrainManager.TERRAIN_BODY_USER_DATA);
                Body newBody = bodyInterface.createBody(bcs);
                if (newBody != null) {
                    chunkDataStore.bodyIds[index] = newBody.getId();
                    chunkDataStore.setShape(index, shape);
                    chunkDataStore.states[index] = wasAddedToSimulation ? VxChunkDataStore.STATE_READY_ACTIVE : VxChunkDataStore.STATE_READY_INACTIVE;
                } else {
                    VxMainClass.LOGGER.error("Failed to create terrain body for chunk {}", VxSectionPos.unpackToSectionPos(packedPos));
                    shape.close();
                    chunkDataStore.states[index] = VxChunkDataStore.STATE_UNLOADED;
                }
            }
        } else {
            chunkDataStore.states[index] = VxChunkDataStore.STATE_AIR_CHUNK;
        }

        int finalBodyId = chunkDataStore.bodyIds[index];
        if (wasAddedToSimulation && finalBodyId != VxChunkDataStore.UNUSED_BODY_ID) {
            bodyInterface.addBody(finalBodyId, EActivation.Activate);
        }
    }
}