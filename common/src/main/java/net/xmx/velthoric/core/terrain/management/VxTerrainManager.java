/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.terrain.management;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.xmx.velthoric.core.physics.VxPhysicsLayers;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.core.terrain.generation.VxChunkSnapshot;
import net.xmx.velthoric.core.terrain.generation.VxTerrainGenerator;
import net.xmx.velthoric.core.terrain.job.VxTerrainJobSystem;
import net.xmx.velthoric.core.terrain.storage.VxChunkDataStore;
import net.xmx.velthoric.init.VxMainClass;

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
     *
     * @param packedPos The bit-packed section coordinate.
     */
    public void requestChunk(long packedPos) {
        int index = chunkDataStore.addChunk(packedPos);
        if (chunkDataStore.incrementAndGetRefCount(index) == 1) {
            scheduleShapeGeneration(packedPos, index, true);
        }
    }

    /**
     * Releases a terrain chunk. Decrements its reference count.
     * If the reference count reaches zero, the chunk is unloaded.
     *
     * @param packedPos The bit-packed section coordinate.
     */
    public void releaseChunk(long packedPos) {
        int index = chunkDataStore.getIndexForPackedPos(packedPos);
        if (index != -1 && chunkDataStore.decrementAndGetRefCount(index) == 0) {
            unloadChunkPhysicsInternal(packedPos);
        }
    }

    /**
     * Schedules a chunk for a rebuild, for example after a block update.
     *
     * @param packedPos The bit-packed section coordinate to rebuild.
     */
    public void rebuildChunk(long packedPos) {
        int index = chunkDataStore.getIndexForPackedPos(packedPos);
        if (index != -1) {
            scheduleShapeGeneration(packedPos, index, false);
        }
    }

    /**
     * Prioritizes the generation of a chunk if it is not yet ready or is a placeholder.
     *
     * @param packedPos The bit-packed section coordinate to prioritize.
     */
    public void prioritizeChunk(long packedPos) {
        int index = chunkDataStore.getIndexForPackedPos(packedPos);
        if (index != -1 && (chunkDataStore.isPlaceholder(index) || !isReady(index))) {
            scheduleShapeGeneration(packedPos, index, false);
        }
    }

    /**
     * Activates a chunk, adding its physics body to the simulation.
     * If the chunk is a placeholder, it schedules a high-priority regeneration.
     *
     * @param packedPos The bit-packed section coordinate to activate.
     */
    public void activateChunk(long packedPos) {
        int index = chunkDataStore.getIndexForPackedPos(packedPos);
        if (index == -1) return;

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

        if (chunkDataStore.isPlaceholder(index) && chunkDataStore.getBodyId(index) != VxChunkDataStore.UNUSED_BODY_ID) {
            scheduleShapeGeneration(packedPos, index, false);
        }
    }

    /**
     * Deactivates a chunk, removing its physics body from the simulation.
     *
     * @param packedPos The bit-packed section coordinate to deactivate.
     */
    public void deactivateChunk(long packedPos) {
        int index = chunkDataStore.getIndexForPackedPos(packedPos);
        if (index == -1) return;

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
     *
     * @param packedPos      The bit-packed section coordinate.
     * @param index          The data store index of the chunk.
     * @param isInitialBuild True if this is the first time the chunk is being built.
     */
    private void scheduleShapeGeneration(long packedPos, int index, boolean isInitialBuild) {
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

            LevelChunk chunk = level.getChunkSource().getChunk(SectionPos.x(packedPos), SectionPos.z(packedPos), false);
            if (chunk == null) {
                // If chunk is not available, reset state to UNLOADED so it can be retried later.
                chunkDataStore.setState(index, STATE_UNLOADED);
                return;
            }

            // Create the snapshot on the main thread as required.
            VxChunkSnapshot snapshot = VxChunkSnapshot.snapshotFromChunk(level, chunk, packedPos);

            // Only proceed if the state is still what we set it to. This prevents conflicts.
            if (chunkDataStore.getState(index) == STATE_LOADING_SCHEDULED) {
                // If we successfully transition to the next state, we "own" the generation process.
                chunkDataStore.setState(index, STATE_GENERATING_SHAPE);
                jobSystem.submit(() -> processShapeGenerationOnWorker(packedPos, index, version, snapshot, isInitialBuild));
            }
        });
    }


    /**
     * Executes the shape generation on a worker thread. This method is called by the job system.
     */
    private void processShapeGenerationOnWorker(long packedPos, int index, int version, VxChunkSnapshot snapshot, boolean isInitialBuild) {
        if (chunkDataStore.isVersionStale(index, version)) {
            if (chunkDataStore.getState(index) == STATE_GENERATING_SHAPE) {
                chunkDataStore.setState(index, STATE_UNLOADED);
            }
            return;
        }

        try {
            ShapeRefC generatedShape = terrainGenerator.generateShape(level, snapshot);
            physicsWorld.execute(() -> applyGeneratedShape(packedPos, index, version, generatedShape, isInitialBuild));
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Exception during terrain shape generation for {}:{}", SectionPos.x(packedPos), SectionPos.z(packedPos), e);
            chunkDataStore.setState(index, STATE_UNLOADED);
        }
    }

    /**
     * Applies the newly generated shape to the chunk's physics body on the main physics thread.
     */
    private void applyGeneratedShape(long packedPos, int index, int version, ShapeRefC shape, boolean isInitialBuild) {
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
            RVec3 position = new RVec3(SectionPos.sectionToBlockCoord(SectionPos.x(packedPos)), SectionPos.sectionToBlockCoord(SectionPos.y(packedPos)), SectionPos.sectionToBlockCoord(SectionPos.z(packedPos)));
            try (BodyCreationSettings bcs = new BodyCreationSettings(shape, position, Quat.sIdentity(), EMotionType.Static, VxPhysicsLayers.TERRAIN)) {
                Body body = bodyInterface.createBody(bcs);
                if (body != null) {
                    body.setFriction(0.75f);
                    chunkDataStore.setBodyId(index, body.getId());
                    chunkDataStore.setShape(index, shape);
                    chunkDataStore.setState(index, wasActive ? STATE_READY_ACTIVE : STATE_READY_INACTIVE);
                } else {
                    VxMainClass.LOGGER.error("Failed to create terrain body for chunk {}:{}:{}", SectionPos.x(packedPos), SectionPos.y(packedPos), SectionPos.z(packedPos));
                    shape.close();
                    chunkDataStore.setState(index, STATE_UNLOADED);
                }
            }
        } else {
            chunkDataStore.setState(index, STATE_AIR_CHUNK);
        }

        chunkDataStore.setPlaceholder(index, isInitialBuild);
        if (wasActive) {
            activateChunk(packedPos);
        }
    }

    /**
     * Internal logic to unload a chunk's physics resources.
     */
    private void unloadChunkPhysicsInternal(long packedPos) {
        int index = chunkDataStore.getIndexForPackedPos(packedPos);
        if (index == -1) return;

        chunkDataStore.setState(index, STATE_REMOVING);

        physicsWorld.execute(() -> {
            removeBodyAndShape(index, physicsWorld.getPhysicsSystem().getBodyInterface());
            chunkDataStore.removeChunk(packedPos);
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
            chunkDataStore.getManagedPackedPositions().forEach(packed -> {
                int index = chunkDataStore.getIndexForPackedPos(packed);
                if (index != -1) {
                    removeBodyAndShape(index, bi);
                }
            });
        }
    }

    public boolean isManaged(long packedPos) {
        return chunkDataStore.getIndexForPackedPos(packedPos) != -1;
    }

    public boolean isReady(long packedPos) {
        int index = chunkDataStore.getIndexForPackedPos(packedPos);
        return index != -1 && isReady(index);
    }

    private boolean isReady(int index) {
        int state = chunkDataStore.getState(index);
        return state == STATE_READY_ACTIVE || state == STATE_READY_INACTIVE || state == STATE_AIR_CHUNK;
    }

    public boolean isPlaceholder(long packedPos) {
        int index = chunkDataStore.getIndexForPackedPos(packedPos);
        if (index == -1) return true;
        return isPlaceholder(index);
    }

    private boolean isPlaceholder(int index) {
        return chunkDataStore.isPlaceholder(index);
    }
}