/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.buoyancy;

import com.github.stephengold.joltjni.BodyLockMultiWrite;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.Map;

/**
 * Manages buoyancy physics for all objects in a {@link VxPhysicsWorld}.
 * This class coordinates a two-phase approach across the game and physics threads.
 *
 * @author xI-Mx-Ix
 */
public final class VxBuoyancyManager {

    private final VxPhysicsWorld physicsWorld;

    // --- Phase Handlers ---
    private final VxBuoyancyBroadPhase broadPhase;
    private final VxBuoyancyNarrowPhase narrowPhase;

    // --- Thread-Safe Communication ---
    /**
     * A thread-safe, volatile array of body IDs identified by the broad-phase as being potentially in a fluid.
     * This array is written by the main game thread and read by the physics thread.
     * The volatile keyword ensures that changes are published correctly across threads.
     */
    private volatile int[] bodiesInFluid = new int[0];

    /**
     * A thread-safe map that associates a body ID with its corresponding fluid surface height.
     * It is written by the game thread and read by the physics thread.
     */
    private final Map<Integer, Float> fluidSurfaceHeights;

    /**
     * A thread-safe map that associates a body ID with the type of fluid it is in.
     * It is written by the game thread and read by the physics thread.
     */
    private final Map<Integer, VxFluidType> fluidTypes;

    /**
     * Constructs a new buoyancy manager for a given physics world.
     *
     * @param physicsWorld The physics world this manager will operate on.
     */
    public VxBuoyancyManager(VxPhysicsWorld physicsWorld) {
        this.physicsWorld = physicsWorld;
        this.broadPhase = new VxBuoyancyBroadPhase(physicsWorld);
        this.narrowPhase = new VxBuoyancyNarrowPhase(physicsWorld);

        // Use synchronized maps for thread-safe access
        this.fluidSurfaceHeights = java.util.Collections.synchronizedMap(new it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap());
        this.fluidTypes = java.util.Collections.synchronizedMap(new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>());
    }

    /**
     * BROAD-PHASE: Called on the main game thread each tick.
     * Delegates to the broad-phase handler to efficiently identify all bodies that are
     * potentially inside a fluid and prepares the results for the narrow-phase.
     * This method ensures data is published to the physics thread in a thread-safe manner.
     */
    public void updateFluidStates() {
        // The broad phase finds all potential contacts and returns the necessary data.
        VxBuoyancyResult result = broadPhase.findPotentialFluidContacts();

        // Ensure that the data maps are fully updated *before* publishing the new list of body IDs.
        // The synchronized block guarantees that clearing and repopulating the maps is an atomic operation.
        synchronized (this) {
            this.fluidSurfaceHeights.clear();
            this.fluidTypes.clear();
            this.fluidSurfaceHeights.putAll(result.getSurfaceHeights());
            this.fluidTypes.putAll(result.getFluidTypes());
        }

        // This volatile write acts as a memory barrier. It signals to the physics thread that
        // not only is the new array of IDs ready, but all the data in the maps associated with
        // those IDs is also ready and consistent.
        this.bodiesInFluid = result.getBodyIds();
    }

    /**
     * NARROW-PHASE: Called on the dedicated physics thread during the simulation step.
     * This method applies buoyancy and drag impulses to the pre-filtered list of bodies
     * identified by the broad-phase.
     *
     * @param deltaTime The fixed time step for the physics simulation.
     */
    public void applyBuoyancyForces(float deltaTime) {
        // This volatile read ensures that we see the most up-to-date list of bodies. Because of the
        // memory barrier established by the write in updateFluidStates(), we are also guaranteed
        // to see the consistent, up-to-date data within the fluidSurfaceHeights and fluidTypes maps.
        int[] currentBodiesInFluid = this.bodiesInFluid;
        if (currentBodiesInFluid.length == 0) {
            return;
        }

        if (physicsWorld.getPhysicsSystem().getBodyInterfaceNoLock() == null) {
            return;
        }

        try (BodyLockMultiWrite lock = new BodyLockMultiWrite(physicsWorld.getPhysicsSystem().getBodyLockInterfaceNoLock(), currentBodiesInFluid)) {
            // It is now safe to pass the maps directly, as their state is consistent with the list of bodies.
            narrowPhase.applyForces(lock, deltaTime, this.fluidSurfaceHeights, this.fluidTypes);
        }
    }

    /**
     * Shuts down the manager, clearing all internal state. This should be called
     * when the physics world is being destroyed to prevent memory leaks.
     */
    public void shutdown() {
        this.bodiesInFluid = new int[0];
        synchronized (this) {
            this.fluidSurfaceHeights.clear();
            this.fluidTypes.clear();
        }
    }
}