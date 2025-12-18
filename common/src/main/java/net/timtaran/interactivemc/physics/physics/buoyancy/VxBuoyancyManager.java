/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.physics.buoyancy;

import com.github.stephengold.joltjni.BodyLockMultiWrite;
import net.timtaran.interactivemc.physics.physics.buoyancy.phase.VxBuoyancyBroadPhase;
import net.timtaran.interactivemc.physics.physics.buoyancy.phase.VxBuoyancyNarrowPhase;
import net.timtaran.interactivemc.physics.physics.world.VxPhysicsWorld;

import java.util.Arrays;

/**
 * Manages buoyancy physics for all objects in a {@link VxPhysicsWorld}.
 * This class coordinates a two-phase approach across the game and physics threads
 * using a lock-free, double-buffered data store for maximum performance.
 *
 * @author xI-Mx-Ix
 */
public final class VxBuoyancyManager {

    private final VxPhysicsWorld physicsWorld;

    // --- Phase Handlers ---
    private final VxBuoyancyBroadPhase broadPhase;
    private final VxBuoyancyNarrowPhase narrowPhase;

    // --- Thread-Safe Communication using Double Buffering ---
    /**
     * The data store that is actively being written to by the main game thread.
     */
    private VxBuoyancyDataStore writeBuffer;

    /**
     * The data store that is being read by the physics thread. This is a volatile
     * reference, which ensures that when it's updated, the change is immediately
     * visible to the physics thread, acting as a memory barrier.
     */
    private volatile VxBuoyancyDataStore readBuffer;

    /**
     * Constructs a new buoyancy manager for a given physics world.
     *
     * @param physicsWorld The physics world this manager will operate on.
     */
    public VxBuoyancyManager(VxPhysicsWorld physicsWorld) {
        this.physicsWorld = physicsWorld;
        this.broadPhase = new VxBuoyancyBroadPhase(physicsWorld);
        this.narrowPhase = new VxBuoyancyNarrowPhase(physicsWorld);

        // Initialize two separate data stores for the double buffer.
        this.writeBuffer = new VxBuoyancyDataStore();
        this.readBuffer = new VxBuoyancyDataStore();
    }

    /**
     * BROAD-PHASE: Called on the main game thread each tick.
     * Delegates to the broad-phase handler to populate the current write buffer with all
     * bodies that are potentially inside a fluid. It then atomically swaps the buffers.
     */
    public void updateFluidStates() {
        // The broad phase populates the back buffer. First, clear the stale data
        // from the previous frame to ensure we start fresh.
        writeBuffer.clear();
        broadPhase.findPotentialFluidContacts(writeBuffer);

        // Atomically swap the buffers. The physics thread will now see the newly populated data.
        // The old readBuffer becomes the new writeBuffer for the next tick.
        VxBuoyancyDataStore oldReadBuffer = this.readBuffer;
        this.readBuffer = this.writeBuffer;
        this.writeBuffer = oldReadBuffer;
    }

    /**
     * NARROW-PHASE: Called on the dedicated physics thread during the simulation step.
     * This method applies buoyancy and drag impulses to the bodies listed in the current read buffer.
     *
     * @param deltaTime The fixed time step for the physics simulation.
     */
    public void applyBuoyancyForces(float deltaTime) {
        // Volatile read ensures we get the most up-to-date buffer.
        VxBuoyancyDataStore currentReadBuffer = this.readBuffer;
        int bodyCount = currentReadBuffer.getCount();
        if (bodyCount == 0) {
            return;
        }

        // Jolt's multi-body lock requires a precisely sized native array. Since our buffer
        // can be larger than the count, we create a correctly-sized copy.
        int[] bodyIds = Arrays.copyOf(currentReadBuffer.bodyIds, bodyCount);

        try (BodyLockMultiWrite lock = new BodyLockMultiWrite(physicsWorld.getPhysicsSystem().getBodyLockInterfaceNoLock(), bodyIds)) {
            // It is now safe to pass the entire data store.
            narrowPhase.applyForces(lock, deltaTime, currentReadBuffer);
        }
    }

    /**
     * Shuts down the manager, clearing all internal state. This should be called
     * when the physics world is being destroyed to prevent memory leaks.
     */
    public void shutdown() {
        this.writeBuffer.clear();
        this.readBuffer.clear();
    }
}