/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.management;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.natives.VxLayers;
import net.xmx.velthoric.physics.terrain.VxChunkDataStore;
import net.xmx.velthoric.physics.terrain.chunk.VxSectionPos;
import net.xmx.velthoric.physics.terrain.job.VxTaskPriority;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

/**
 * Manages the state and lifecycle of all terrain physics bodies.
 * <p>
 * This class is the single source of truth for chunk states (e.g., loaded, active, air).
 * It handles the creation, destruction, activation, and deactivation of Jolt bodies
 * and ensures that their associated resources, like shapes, are managed correctly.
 * All state-mutating operations are executed on the physics thread to ensure thread safety.
 *
 * @author xI-Mx-Ix
 */
public class VxChunkManager {

    // --- State Machine Constants for Chunks ---
    public static final int STATE_UNLOADED = 0;
    public static final int STATE_LOADING_SCHEDULED = 1;
    public static final int STATE_GENERATING_SHAPE = 2;
    public static final int STATE_READY_INACTIVE = 3;
    public static final int STATE_READY_ACTIVE = 4;
    public static final int STATE_REMOVING = 5;
    public static final int STATE_AIR_CHUNK = 6;

    private final VxPhysicsWorld physicsWorld;
    private final VxChunkDataStore chunkDataStore;
    private VxShapeGenerationQueue shapeGenerationQueue; // Injected via setter

    public VxChunkManager(VxPhysicsWorld physicsWorld, VxChunkDataStore chunkDataStore) {
        this.physicsWorld = physicsWorld;
        this.chunkDataStore = chunkDataStore;
    }

    /**
     * Injects the dependency for the shape generation queue. This is used to resolve a circular dependency.
     * @param shapeGenerationQueue The queue responsible for generating shapes.
     */
    public void setShapeGenerationQueue(VxShapeGenerationQueue shapeGenerationQueue) {
        this.shapeGenerationQueue = shapeGenerationQueue;
    }

    /**
     * Called when an object tracker needs a chunk. Increments the reference count and
     * schedules generation if it's the first request.
     * @param pos The position of the requested chunk section.
     */
    public void requestChunk(VxSectionPos pos) {
        int index = chunkDataStore.addChunk(pos);
        if (++chunkDataStore.referenceCounts[index] == 1) {
            // First request for this chunk, schedule its shape generation.
            shapeGenerationQueue.scheduleShapeGeneration(pos, index, true, VxTaskPriority.HIGH);
        }
    }

    /**
     * Called when an object tracker no longer needs a chunk. Decrements the reference count
     * and unloads the chunk if the count reaches zero.
     * @param pos The position of the released chunk section.
     */
    public void releaseChunk(VxSectionPos pos) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index != null && --chunkDataStore.referenceCounts[index] == 0) {
            // Last reference released, unload the chunk.
            unloadChunkPhysics(pos);
        }
    }

    /**
     * Applies a newly generated shape to a chunk. This involves creating a new body
     * or updating the shape of an existing one.
     *
     * @param pos            The position of the chunk section.
     * @param index          The data store index for the chunk.
     * @param shape          The new shape. This method takes ownership and will close it.
     * @param isInitialBuild True if this is the first time a shape is being built for this chunk.
     */
    public void applyGeneratedShape(VxSectionPos pos, int index, @Nullable ShapeRefC shape, boolean isInitialBuild) {
        // This method takes ownership of the 'shape' parameter and MUST close it before returning.
        try {
            boolean wasActive = chunkDataStore.states[index] == STATE_READY_ACTIVE;
            BodyInterface bodyInterface = physicsWorld.getBodyInterface();
            if (bodyInterface == null) {
                chunkDataStore.states[index] = STATE_UNLOADED;
                return;
            }

            int bodyId = chunkDataStore.bodyIds[index];

            if (shape != null) {
                if (bodyId != VxChunkDataStore.UNUSED_BODY_ID) {
                    bodyInterface.setShape(bodyId, shape, true, EActivation.DontActivate);
                } else {
                    bodyId = createBody(pos, shape, bodyInterface);
                    chunkDataStore.bodyIds[index] = bodyId;
                }

                if (bodyId != VxChunkDataStore.UNUSED_BODY_ID) {
                    chunkDataStore.setShape(index, shape.getPtr().toRefC());
                    chunkDataStore.states[index] = wasActive ? STATE_READY_ACTIVE : STATE_READY_INACTIVE;
                }
            } else { // No shape was generated (air chunk).
                if (bodyId != VxChunkDataStore.UNUSED_BODY_ID) {
                    removeBodyAndShape(index, bodyInterface);
                }
                chunkDataStore.setShape(index, null);
                chunkDataStore.states[index] = STATE_AIR_CHUNK;
            }

            chunkDataStore.isPlaceholder[index] = isInitialBuild;
            if (wasActive) {
                activateChunk(pos, index);
            }
        } finally {
            if (shape != null) {
                shape.close();
            }
        }
    }

    /**
     * Creates a new static physics body for a terrain chunk.
     *
     * @param pos           The position of the chunk section.
     * @param shape         The shape for the body.
     * @param bodyInterface The Jolt body interface.
     * @return The ID of the newly created body, or UNUSED_BODY_ID on failure.
     */
    private int createBody(VxSectionPos pos, ShapeRefC shape, BodyInterface bodyInterface) {
        RVec3 position = new RVec3(pos.getOrigin().getX(), pos.getOrigin().getY(), pos.getOrigin().getZ());
        try (BodyCreationSettings bcs = new BodyCreationSettings(shape, position, Quat.sIdentity(), EMotionType.Static, VxLayers.TERRAIN)) {
            bcs.setEnhancedInternalEdgeRemoval(true);
            Body body = bodyInterface.createBody(bcs);
            if (body != null) {
                return body.getId();
            } else {
                VxMainClass.LOGGER.error("Failed to create terrain body for chunk {}", pos);
                return VxChunkDataStore.UNUSED_BODY_ID;
            }
        }
    }

    /**
     * Activates a chunk, adding its physics body to the simulation if it's not already present.
     *
     * @param pos   The position of the chunk section.
     * @param index The data store index for the chunk.
     */
    public void activateChunk(VxSectionPos pos, int index) {
        if (chunkDataStore.states[index] == STATE_AIR_CHUNK) return;

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
    }

    /**
     * Deactivates a chunk, removing its physics body from the simulation.
     *
     * @param index The data store index for the chunk.
     */
    public void deactivateChunk(int index) {
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
     * Schedules the complete removal of a chunk's physics resources.
     *
     * @param pos The position of the chunk section to unload.
     */
    public void unloadChunkPhysics(VxSectionPos pos) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index == null) return;

        chunkDataStore.states[index] = STATE_REMOVING;
        chunkDataStore.rebuildVersions[index]++; // Invalidate any pending generation tasks

        physicsWorld.execute(() -> {
            removeBodyAndShape(index, physicsWorld.getBodyInterface());
            chunkDataStore.removeChunk(pos);
        });
    }

    /**
     * Immediately removes a body from the simulation, destroys it, and releases its shape reference.
     *
     * @param index         The data store index for the chunk.
     * @param bodyInterface The Jolt body interface.
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
}