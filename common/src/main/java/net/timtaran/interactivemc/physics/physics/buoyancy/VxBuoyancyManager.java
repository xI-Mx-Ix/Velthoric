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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages buoyancy physics for all objects in a {@link VxPhysicsWorld}.
 * This class coordinates a two-phase approach across the game and physics threads
 * using a non-blocking triple-buffered data store. This architecture prevents
 * race conditions where the physics thread reads data while the game thread is clearing it.
 *
 * @author xI-Mx-Ix
 */
public final class VxBuoyancyManager {

    private final VxPhysicsWorld physicsWorld;

    // --- Phase Handlers ---
    private final VxBuoyancyBroadPhase broadPhase;
    private final VxBuoyancyNarrowPhase narrowPhase;

    // --- Non-blocking Triple Buffering ---
    /**
     * The data store currently being populated by the game thread.
     */
    private VxBuoyancyDataStore fillingBuffer;

    /**
     * The most recent completed data store, ready to be picked up by the physics thread.
     */
    private final AtomicReference<VxBuoyancyDataStore> publishedBuffer;

    /**
     * The data store currently being processed by the physics thread.
     */
    private VxBuoyancyDataStore readingBuffer;

    /**
     * Constructs a new buoyancy manager for a given physics world.
     *
     * @param physicsWorld The physics world this manager will operate on.
     */
    public VxBuoyancyManager(VxPhysicsWorld physicsWorld) {
        this.physicsWorld = physicsWorld;
        this.broadPhase = new VxBuoyancyBroadPhase(physicsWorld);
        this.narrowPhase = new VxBuoyancyNarrowPhase(physicsWorld);

        this.fillingBuffer = new VxBuoyancyDataStore();
        this.publishedBuffer = new AtomicReference<>(new VxBuoyancyDataStore());
        this.readingBuffer = new VxBuoyancyDataStore();
    }

    /**
     * BROAD-PHASE: Called on the main game thread each tick.
     * Populates the private filling buffer with potential fluid contacts and then
     * atomically publishes the results for the physics thread.
     */
    public void updateFluidStates() {
        // Clear the private filling buffer to start a fresh scan.
        fillingBuffer.clear();
        broadPhase.findPotentialFluidContacts(fillingBuffer);

        // Swap the filling buffer with the published buffer.
        // The old published buffer returns to become the filling buffer for the next tick.
        fillingBuffer = publishedBuffer.getAndSet(fillingBuffer);
    }

    /**
     * NARROW-PHASE: Called on the dedicated physics thread during the simulation step.
     * Retrieves the latest published buffer and applies impulses based on its contents.
     *
     * @param deltaTime The fixed time step for the physics simulation.
     */
    public void applyBuoyancyForces(float deltaTime) {
        // Exchange the current reading buffer with the latest published one.
        // This ensures the physics thread has a stable snapshot that won't be modified.
        VxBuoyancyDataStore latestBuffer = publishedBuffer.getAndSet(readingBuffer);
        readingBuffer = latestBuffer;

        int bodyCount = readingBuffer.getCount();
        if (bodyCount == 0) {
            return;
        }

        // Jolt's multi-body lock requires a precisely sized native array.
        int[] bodyIds = Arrays.copyOf(readingBuffer.bodyIds, bodyCount);

        try (BodyLockMultiWrite lock = new BodyLockMultiWrite(physicsWorld.getPhysicsSystem().getBodyLockInterfaceNoLock(), bodyIds)) {
            narrowPhase.applyForces(lock, deltaTime, readingBuffer);
        }
    }

    /**
     * Shuts down the manager, clearing all internal state.
     */
    public void shutdown() {
        this.fillingBuffer.clear();
        this.readingBuffer.clear();
        this.publishedBuffer.get().clear();
    }
}