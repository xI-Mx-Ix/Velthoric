/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.management;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.natives.VxLayers;
import net.xmx.velthoric.physics.terrain.VxSectionPos;
import net.xmx.velthoric.physics.terrain.generation.VxChunkSnapshot;
import net.xmx.velthoric.physics.terrain.generation.VxTerrainGenerator;
import net.xmx.velthoric.physics.terrain.job.VxTaskPriority;
import net.xmx.velthoric.physics.terrain.job.VxTerrainJobSystem;
import net.xmx.velthoric.physics.terrain.storage.VxChunkDataStore;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * Manages the lifecycle of terrain chunks, including loading, unloading, activation,
 * deactivation, and rebuilding. It acts as the bridge between the high-level tracking logic
 * and the low-level physics and data storage operations.
 *
 * @author xI-Mx-Ix
 */
public final class VxTerrainManager {

    // Chunk state constants
    private static final int STATE_UNLOADED = 0;
    private static final int STATE_LOADING_SCHEDULED = 1;
    private static final int STATE_GENERATING_SHAPE = 2;
    private static final int STATE_READY_INACTIVE = 3;
    private static final int STATE_READY_ACTIVE = 4;
    private static final int STATE_REMOVING = 5;
    private static final int STATE_AIR_CHUNK = 6;

    private final VxPhysicsWorld physicsWorld;
    private final ServerLevel level;
    private final VxTerrainGenerator terrainGenerator;
    private final VxChunkDataStore chunkDataStore;
    private final VxTerrainJobSystem jobSystem;

    public VxTerrainManager(VxPhysicsWorld physicsWorld, ServerLevel level, VxTerrainGenerator terrainGenerator, VxChunkDataStore chunkDataStore, VxTerrainJobSystem jobSystem) {
        this.physicsWorld = physicsWorld;
        this.level = level;
        this.terrainGenerator = terrainGenerator;
        this.chunkDataStore = chunkDataStore;
        this.jobSystem = jobSystem;
    }

    /**
     * Requests a terrain chunk to be loaded. Increments its reference count.
     * If this is the first request, it schedules the chunk for generation.
     * @param pos The position of the chunk section.
     */
    public void requestChunk(VxSectionPos pos) {
        int index = chunkDataStore.addChunk(pos);
        if (++chunkDataStore.referenceCounts[index] == 1) {
            scheduleShapeGeneration(pos, index, true, VxTaskPriority.HIGH);
        }
    }

    /**
     * Releases a terrain chunk. Decrements its reference count.
     * If the reference count reaches zero, the chunk is unloaded.
     * @param pos The position of the chunk section.
     */
    public void releaseChunk(VxSectionPos pos) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index != null && --chunkDataStore.referenceCounts[index] == 0) {
            unloadChunkPhysicsInternal(pos);
        }
    }

    /**
     * Schedules a chunk for a rebuild, for example after a block update.
     * @param pos The position of the chunk to rebuild.
     * @param priority The priority of the rebuild task.
     */
    public void rebuildChunk(VxSectionPos pos, VxTaskPriority priority) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index != null) {
            scheduleShapeGeneration(pos, index, false, priority);
        }
    }

    /**
     * Prioritizes the generation of a chunk if it is not yet ready or is a placeholder.
     * @param pos The position of the chunk to prioritize.
     * @param priority The priority for the generation task.
     */
    public void prioritizeChunk(VxSectionPos pos, VxTaskPriority priority) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index != null && (isPlaceholder(index) || !isReady(index))) {
            scheduleShapeGeneration(pos, index, false, priority);
        }
    }

    /**
     * Activates a chunk, adding its physics body to the simulation.
     * If the chunk is a placeholder, it schedules a high-priority regeneration.
     * @param pos The position of the chunk to activate.
     */
    public void activateChunk(VxSectionPos pos) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index == null) return;

        if (chunkDataStore.states[index] == STATE_AIR_CHUNK) {
            return;
        }

        if (chunkDataStore.bodyIds[index] != VxChunkDataStore.UNUSED_BODY_ID && chunkDataStore.states[index] == STATE_READY_INACTIVE) {
            chunkDataStore.states[index] = STATE_READY_ACTIVE;
            physicsWorld.execute(() -> {
                BodyInterface bodyInterface = physicsWorld.getBodyInterface();
                int bodyId = chunkDataStore.bodyIds[index];
                if (bodyInterface != null && bodyId != VxChunkDataStore.UNUSED_BODY_ID && !bodyInterface.isAdded(bodyId)) {
                    bodyInterface.addBody(bodyId, EActivation.Activate);
                }
            });
        }

        if (isPlaceholder(index) && chunkDataStore.bodyIds[index] != VxChunkDataStore.UNUSED_BODY_ID) {
            scheduleShapeGeneration(pos, index, false, VxTaskPriority.CRITICAL);
        }
    }

    /**
     * Deactivates a chunk, removing its physics body from the simulation.
     * @param pos The position of the chunk to deactivate.
     */
    public void deactivateChunk(VxSectionPos pos) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index == null) return;

        if (chunkDataStore.bodyIds[index] != VxChunkDataStore.UNUSED_BODY_ID && chunkDataStore.states[index] == STATE_READY_ACTIVE) {
            chunkDataStore.states[index] = STATE_READY_INACTIVE;
            physicsWorld.execute(() -> {
                BodyInterface bodyInterface = physicsWorld.getBodyInterface();
                int bodyId = chunkDataStore.bodyIds[index];
                if (bodyInterface != null && bodyId != VxChunkDataStore.UNUSED_BODY_ID && bodyInterface.isAdded(bodyId)) {
                    bodyInterface.removeBody(bodyId);
                }
            });
        }
    }

    /**
     * Schedules the generation of a physics shape for a chunk.
     * @param pos The position of the chunk.
     * @param index The data store index of the chunk.
     * @param isInitialBuild True if this is the first time the chunk is being built.
     * @param priority The priority of the generation task.
     */
    private void scheduleShapeGeneration(VxSectionPos pos, int index, boolean isInitialBuild, VxTaskPriority priority) {
        if (jobSystem.isShutdown()) return;

        int currentState = chunkDataStore.states[index];
        if (currentState == STATE_REMOVING || currentState == STATE_LOADING_SCHEDULED || currentState == STATE_GENERATING_SHAPE) {
            return;
        }

        if (chunkDataStore.states[index] == currentState) {
            chunkDataStore.states[index] = STATE_LOADING_SCHEDULED;
            final int version = ++chunkDataStore.rebuildVersions[index];

            level.getServer().execute(() -> {
                if (version < chunkDataStore.rebuildVersions[index]) {
                    if (chunkDataStore.states[index] == STATE_LOADING_SCHEDULED) chunkDataStore.states[index] = currentState;
                    return;
                }

                LevelChunk chunk = level.getChunkSource().getChunk(pos.x(), pos.z(), false);
                if (chunk == null) {
                    chunkDataStore.states[index] = STATE_UNLOADED;
                    return;
                }

                VxChunkSnapshot snapshot = VxChunkSnapshot.snapshotFromChunk(level, chunk, pos);
                if (chunkDataStore.states[index] == STATE_LOADING_SCHEDULED) {
                    chunkDataStore.states[index] = STATE_GENERATING_SHAPE;
                    jobSystem.submit(() -> processShapeGenerationOnWorker(pos, index, version, snapshot, isInitialBuild, currentState));
                }
            });
        }
    }

    /**
     * Executes the shape generation on a worker thread.
     */
    private void processShapeGenerationOnWorker(VxSectionPos pos, int index, int version, VxChunkSnapshot snapshot, boolean isInitialBuild, int previousState) {
        if (version < chunkDataStore.rebuildVersions[index]) {
            if (chunkDataStore.states[index] == STATE_GENERATING_SHAPE) chunkDataStore.states[index] = previousState;
            return;
        }

        try {
            ShapeRefC generatedShape = terrainGenerator.generateShape(level, snapshot);
            physicsWorld.execute(() -> applyGeneratedShape(pos, index, version, generatedShape, isInitialBuild));
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Exception during terrain shape generation for {}", pos, e);
            chunkDataStore.states[index] = STATE_UNLOADED;
        }
    }

    /**
     * Applies the newly generated shape to the chunk's physics body on the main physics thread.
     */
    private void applyGeneratedShape(VxSectionPos pos, int index, int version, ShapeRefC shape, boolean isInitialBuild) {
        boolean wasActive = chunkDataStore.states[index] == STATE_READY_ACTIVE;

        if (version < chunkDataStore.rebuildVersions[index] || chunkDataStore.states[index] == STATE_REMOVING) {
            if (shape != null) shape.close();
            if (chunkDataStore.states[index] != STATE_REMOVING) chunkDataStore.states[index] = STATE_UNLOADED;
            return;
        }

        BodyInterface bodyInterface = physicsWorld.getBodyInterface();
        if (bodyInterface == null) {
            if (shape != null) shape.close();
            chunkDataStore.states[index] = STATE_UNLOADED;
            return;
        }

        int bodyId = chunkDataStore.bodyIds[index];
        if (bodyId != VxChunkDataStore.UNUSED_BODY_ID) {
            if (shape != null) {
                bodyInterface.setShape(bodyId, shape, true, EActivation.DontActivate);
                chunkDataStore.setShape(index, shape);
                chunkDataStore.states[index] = wasActive ? STATE_READY_ACTIVE : STATE_READY_INACTIVE;
            } else {
                removeBodyAndShape(index, bodyInterface);
                chunkDataStore.states[index] = STATE_AIR_CHUNK;
            }
        } else if (shape != null) {
            RVec3 position = new RVec3(pos.getOrigin().getX(), pos.getOrigin().getY(), pos.getOrigin().getZ());
            try (BodyCreationSettings bcs = new BodyCreationSettings(shape, position, Quat.sIdentity(), EMotionType.Static, VxLayers.TERRAIN)) {
                Body body = bodyInterface.createBody(bcs);
                if (body != null) {
                    body.setFriction(0.65f);
                    chunkDataStore.bodyIds[index] = body.getId();
                    chunkDataStore.setShape(index, shape);
                    chunkDataStore.states[index] = wasActive ? STATE_READY_ACTIVE : STATE_READY_INACTIVE;
                } else {
                    VxMainClass.LOGGER.error("Failed to create terrain body for chunk {}", pos);
                    shape.close();
                    chunkDataStore.states[index] = STATE_UNLOADED;
                }
            }
        } else {
            chunkDataStore.states[index] = STATE_AIR_CHUNK;
        }

        chunkDataStore.isPlaceholder[index] = isInitialBuild;
        if (wasActive) {
            activateChunk(pos);
        }
    }

    /**
     * Internal logic to unload a chunk's physics resources.
     */
    private void unloadChunkPhysicsInternal(VxSectionPos pos) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index == null) return;

        chunkDataStore.states[index] = STATE_REMOVING;
        chunkDataStore.rebuildVersions[index]++;

        physicsWorld.execute(() -> {
            removeBodyAndShape(index, physicsWorld.getBodyInterface());
            chunkDataStore.removeChunk(pos);
        });
    }

    /**
     * Removes the physics body and shape for a chunk at a given index.
     */
    private void removeBodyAndShape(int index, BodyInterface bodyInterface) {
        int bodyId = chunkDataStore.bodyIds[index];
        if (bodyId != VxChunkDataStore.UNUSED_BODY_ID && bodyInterface != null) {
            if (bodyInterface.isAdded(bodyId)) {
                bodyInterface.removeBody(bodyId);
            }
            bodyInterface.destroyBody(bodyId);
        }
        chunkDataStore.bodyIds[index] = VxChunkDataStore.UNUSED_BODY_ID;
        chunkDataStore.setShape(index, null);
    }

    /**
     * Cleans up all managed terrain bodies during shutdown.
     */
    public void cleanupAllBodies() {
        BodyInterface bi = physicsWorld.getBodyInterface();
        if (bi != null) {
            chunkDataStore.getManagedPositions().forEach(pos -> {
                Integer index = chunkDataStore.getIndexForPos(pos);
                if (index != null) {
                    removeBodyAndShape(index, bi);
                }
            });
        }
    }

    public boolean isManaged(VxSectionPos pos) {
        return chunkDataStore.getIndexForPos(pos) != null;
    }

    public boolean isReady(VxSectionPos pos) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        return index != null && isReady(index);
    }

    private boolean isReady(int index) {
        int state = chunkDataStore.states[index];
        return state == STATE_READY_ACTIVE || state == STATE_READY_INACTIVE || state == STATE_AIR_CHUNK;
    }

    public boolean isPlaceholder(VxSectionPos pos) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        return index == null || isPlaceholder(index);
    }

    private boolean isPlaceholder(int index) {
        return chunkDataStore.isPlaceholder[index];
    }
}