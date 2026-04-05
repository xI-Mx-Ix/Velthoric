/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.physics.buoyancy.behavior;

import com.github.stephengold.joltjni.readonly.ConstBodyLockInterface;
import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.core.behavior.VxBehavior;
import net.xmx.velthoric.core.behavior.VxBehaviorId;
import net.xmx.velthoric.core.body.server.VxServerBodyDataStore;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.core.physics.buoyancy.VxBuoyancyDataStore;
import net.xmx.velthoric.core.physics.buoyancy.phase.VxBuoyancyBroadPhase;
import net.xmx.velthoric.core.physics.buoyancy.phase.VxBuoyancyNarrowPhase;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The behavior for buoyancy simulation.
 * <p>
 * Controls the triggering of broad and narrow phases of fluid physics computation
 * using a non-blocking triple-buffered data store. This architecture prevents
 * race conditions where the physics thread reads data while the game thread is clearing it.
 *
 * @author xI-Mx-Ix
 */
public class VxBuoyancyBehavior implements VxBehavior {

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

    public VxBuoyancyBehavior(VxPhysicsWorld world) {
        this.broadPhase = new VxBuoyancyBroadPhase(world);
        this.narrowPhase = new VxBuoyancyNarrowPhase(world);

        this.fillingBuffer = new VxBuoyancyDataStore();
        this.publishedBuffer = new AtomicReference<>(new VxBuoyancyDataStore());
        this.readingBuffer = new VxBuoyancyDataStore();
    }

    /**
     * The unique identifier for this behavior.
     * Consumed by the behavior manager for bitmask allocation and dispatch.
     */
    public static final VxBehaviorId ID = new VxBehaviorId(VxMainClass.MODID, "Buoyancy");

    /**
     * Retrieves the unique identifier for this behavior.
     *
     * @return The behavior ID.
     */
    @Override
    public VxBehaviorId getId() {
        return ID;
    }

    @Override
    public void onPhysicsTick(VxPhysicsWorld world, VxServerBodyDataStore store) {
        // Exchange the current reading buffer with the latest published one.
        VxBuoyancyDataStore latestBuffer = publishedBuffer.getAndSet(readingBuffer);
        readingBuffer = latestBuffer;

        if (readingBuffer.getCount() == 0) {
            return;
        }

        ConstBodyLockInterface lockInterface = world.getPhysicsSystem().getBodyLockInterfaceNoLock();
        narrowPhase.applyForces(lockInterface, VxPhysicsWorld.getFixedTimeStep(), readingBuffer);
    }

    @Override
    public void onServerTick(ServerLevel level, VxServerBodyDataStore store) {
        // Clear the private filling buffer to start a fresh scan.
        fillingBuffer.clear();
        broadPhase.findPotentialFluidContacts(fillingBuffer);

        // Swap the filling buffer with the published buffer.
        fillingBuffer = publishedBuffer.getAndSet(fillingBuffer);
    }
}