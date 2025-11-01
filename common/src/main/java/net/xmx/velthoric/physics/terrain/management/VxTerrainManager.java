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
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.terrain.VxSectionPos;
import net.xmx.velthoric.physics.terrain.generation.VxChunkSnapshot;
import net.xmx.velthoric.physics.terrain.generation.VxTerrainGenerator;
import net.xmx.velthoric.physics.terrain.job.VxTerrainJobSystem;
import net.xmx.velthoric.physics.terrain.storage.VxChunkDataStore;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * Manages the lifecycle of terrain chunks, including loading, unloading, activation,
 * deactivation, and rebuilding. It acts as the bridge between the high-level tracking logic
 * and the low-level physics and data storage operations, ensuring thread-safe state transitions.
 *
 * @author xI-Mx-Ix
 */
public final class VxTerrainManager {

    // --- Chunk State Constants ---
    public static final int STATE_UNLOADED = 0;
    public static final int STATE_LOADING_SCHEDULED = 1;
    public static final int STATE_GENERATING_SHAPE = 2;
    public static final int STATE_READY_INACTIVE = 3;
    public static final int STATE_READY_ACTIVE = 4;
    public static final int STATE_REMOVING = 5;
    public static final int STATE_AIR_CHUNK = 6;

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
     * If this is the first request, it schedules the chunk for shape generation.
     * @param pos The position of the chunk section.
     */
    public void requestChunk(VxSectionPos pos) {
        int index = chunkDataStore.addChunk(pos);
        if (chunkDataStore.incrementAndGetRefCount(index) == 1) {
            scheduleShapeGeneration(pos, index, true);
        }
    }

    /**
     * Releases a terrain chunk. Decrements its reference count.
     * If the reference count reaches zero, the chunk is unloaded.
     * @param pos The position of the chunk section.
     */
    public void releaseChunk(VxSectionPos pos) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index != null && chunkDataStore.decrementAndGetRefCount(index) == 0) {
            unloadChunkPhysicsInternal(pos);
        }
    }

    /**
     * Schedules a chunk for a rebuild, for example after a block update.
     * @param pos The position of the chunk to rebuild.
     */
    public void rebuildChunk(VxSectionPos pos) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index != null) {
            scheduleShapeGeneration(pos, index, false);
        }
    }

    /**
     * Prioritizes the generation of a chunk if it is not yet ready or is a placeholder.
     * @param pos The position of the chunk to prioritize.
     */
    public void prioritizeChunk(VxSectionPos pos) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index != null && (isPlaceholder(index) || !isReady(index))) {
            scheduleShapeGeneration(pos, index, false);
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

        if (chunkDataStore.getState(index) == STATE_AIR_CHUNK) {
            return;
        }

        if (chunkDataStore.getBodyId(index) != VxChunkDataStore.UNUSED_BODY_ID && chunkDataStore.getState(index) == STATE_READY_INACTIVE) {
            chunkDataStore.setState(index, STATE_READY_ACTIVE);
            physicsWorld.execute(() -> {
                BodyInterface bodyInterface = physicsWorld.getPhysicsSystem().getBodyInterface();
                int bodyId = chunkDataStore.getBodyId(index);
                if (bodyInterface != null && bodyId != VxChunkDataStore.UNUSED_BODY_ID && !bodyInterface.isAdded(bodyId)) {
                    bodyInterface.addBody(bodyId, EActivation.Activate);
                }
            });
        }

        if (isPlaceholder(index) && chunkDataStore.getBodyId(index) != VxChunkDataStore.UNUSED_BODY_ID) {
            scheduleShapeGeneration(pos, index, false);
        }
    }

    /**
     * Deactivates a chunk, removing its physics body from the simulation.
     * @param pos The position of the chunk to deactivate.
     */
    public void deactivateChunk(VxSectionPos pos) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index == null) return;

        if (chunkDataStore.getBodyId(index) != VxChunkDataStore.UNUSED_BODY_ID && chunkDataStore.getState(index) == STATE_READY_ACTIVE) {
            chunkDataStore.setState(index, STATE_READY_INACTIVE);
            physicsWorld.execute(() -> {
                BodyInterface bodyInterface = physicsWorld.getPhysicsSystem().getBodyInterface();
                int bodyId = chunkDataStore.getBodyId(index);
                if (bodyInterface != null && bodyId != VxChunkDataStore.UNUSED_BODY_ID && bodyInterface.isAdded(bodyId)) {
                    bodyInterface.removeBody(bodyId);
                }
            });
        }
    }

    /**
     * Atomically schedules the generation of a physics shape for a chunk.
     * @param pos The position of the chunk.
     * @param index The data store index of the chunk.
     * @param isInitialBuild True if this is the first time the chunk is being built.
     */
    private void scheduleShapeGeneration(VxSectionPos pos, int index, boolean isInitialBuild) {
        if (jobSystem.isShutdown()) return;

        final int version = chunkDataStore.scheduleForGeneration(index);
        if (version == -1) {
            return; // Chunk is already being processed or removed.
        }

        level.getServer().execute(() -> {
            // Check if a newer generation task has already been scheduled. If so, this one is stale.
            if (chunkDataStore.isVersionStale(index, version)) {
                // Do not reset the state here. A newer task is now responsible for it.
                // Just let this stale task die quietly.
                return;
            }

            LevelChunk chunk = level.getChunkSource().getChunk(pos.x(), pos.z(), false);
            if (chunk == null) {
                // If chunk is not available, reset state to UNLOADED so it can be retried later.
                chunkDataStore.setState(index, STATE_UNLOADED);
                return;
            }

            // Create the snapshot on the main thread as required.
            VxChunkSnapshot snapshot = VxChunkSnapshot.snapshotFromChunk(level, chunk, pos);

            // Only proceed if the state is still what we set it to. This prevents conflicts.
            if (chunkDataStore.getState(index) == STATE_LOADING_SCHEDULED) {
                // If we successfully transition to the next state, we "own" the generation process.
                chunkDataStore.setState(index, STATE_GENERATING_SHAPE);
                jobSystem.submit(() -> processShapeGenerationOnWorker(pos, index, version, snapshot, isInitialBuild));
            }
        });
    }


    /**
     * Executes the shape generation on a worker thread. This method is called by the job system.
     */
    private void processShapeGenerationOnWorker(VxSectionPos pos, int index, int version, VxChunkSnapshot snapshot, boolean isInitialBuild) {
        if (chunkDataStore.isVersionStale(index, version)) {
            if (chunkDataStore.getState(index) == STATE_GENERATING_SHAPE) {
                chunkDataStore.setState(index, STATE_UNLOADED);
            }
            return;
        }

        try {
            ShapeRefC generatedShape = terrainGenerator.generateShape(level, snapshot);
            physicsWorld.execute(() -> applyGeneratedShape(pos, index, version, generatedShape, isInitialBuild));
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Exception during terrain shape generation for {}", pos, e);
            chunkDataStore.setState(index, STATE_UNLOADED);
        }
    }

    /**
     * Applies the newly generated shape to the chunk's physics body on the main physics thread.
     */
    private void applyGeneratedShape(VxSectionPos pos, int index, int version, ShapeRefC shape, boolean isInitialBuild) {
        if (chunkDataStore.isVersionStale(index, version) || chunkDataStore.getState(index) == STATE_REMOVING) {
            if (shape != null) shape.close();
            if (chunkDataStore.getState(index) != STATE_REMOVING) {
                chunkDataStore.setState(index, STATE_UNLOADED);
            }
            return;
        }

        boolean wasActive = chunkDataStore.getState(index) == STATE_READY_ACTIVE;

        BodyInterface bodyInterface = physicsWorld.getPhysicsSystem().getBodyInterface();
        if (bodyInterface == null) {
            if (shape != null) shape.close();
            chunkDataStore.setState(index, STATE_UNLOADED);
            return;
        }

        int bodyId = chunkDataStore.getBodyId(index);
        if (bodyId != VxChunkDataStore.UNUSED_BODY_ID) {
            if (shape != null) {
                bodyInterface.setShape(bodyId, shape, true, EActivation.DontActivate);
                chunkDataStore.setShape(index, shape);
                chunkDataStore.setState(index, wasActive ? STATE_READY_ACTIVE : STATE_READY_INACTIVE);
            } else {
                removeBodyAndShape(index, bodyInterface);
                chunkDataStore.setState(index, STATE_AIR_CHUNK);
            }
        } else if (shape != null) {
            RVec3 position = new RVec3(pos.getOrigin().getX(), pos.getOrigin().getY(), pos.getOrigin().getZ());
            try (BodyCreationSettings bcs = new BodyCreationSettings(shape, position, Quat.sIdentity(), EMotionType.Static, VxLayers.TERRAIN)) {
                Body body = bodyInterface.createBody(bcs);
                if (body != null) {
                    body.setFriction(0.75f);
                    chunkDataStore.setBodyId(index, body.getId());
                    chunkDataStore.setShape(index, shape);
                    chunkDataStore.setState(index, wasActive ? STATE_READY_ACTIVE : STATE_READY_INACTIVE);
                } else {
                    VxMainClass.LOGGER.error("Failed to create terrain body for chunk {}", pos);
                    shape.close();
                    chunkDataStore.setState(index, STATE_UNLOADED);
                }
            }
        } else {
            chunkDataStore.setState(index, STATE_AIR_CHUNK);
        }

        chunkDataStore.setPlaceholder(index, isInitialBuild);
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

        chunkDataStore.setState(index, STATE_REMOVING);

        physicsWorld.execute(() -> {
            removeBodyAndShape(index, physicsWorld.getPhysicsSystem().getBodyInterface());
            chunkDataStore.removeChunk(pos);
        });
    }

    /**
     * Removes the physics body and shape for a chunk at a given index.
     */
    private void removeBodyAndShape(int index, BodyInterface bodyInterface) {
        int bodyId = chunkDataStore.getBodyId(index);
        if (bodyId != VxChunkDataStore.UNUSED_BODY_ID && bodyInterface != null) {
            if (bodyInterface.isAdded(bodyId)) {
                bodyInterface.removeBody(bodyId);
            }
            bodyInterface.destroyBody(bodyId);
        }
        chunkDataStore.setBodyId(index, VxChunkDataStore.UNUSED_BODY_ID);
        chunkDataStore.setShape(index, null);
    }

    /**
     * Cleans up all managed terrain bodies during shutdown.
     */
    public void cleanupAllBodies() {
        BodyInterface bi = physicsWorld.getPhysicsSystem().getBodyInterface();
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
        int state = chunkDataStore.getState(index);
        return state == STATE_READY_ACTIVE || state == STATE_READY_INACTIVE || state == STATE_AIR_CHUNK;
    }

    public boolean isPlaceholder(VxSectionPos pos) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index == null) return true;
        return isPlaceholder(index);
    }

    private boolean isPlaceholder(int index) {
        return chunkDataStore.isPlaceholder(index);
    }
}