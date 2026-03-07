/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.behavior;

/**
 * Central registry of all built-in {@link VxBehaviorId} constants.
 * <p>
 * Each constant represents a unique behavior that can be attached to a physics body.
 * The bit positions are assigned automatically in class-loading order.
 * <p>
 * Third-party mods can create their own {@link VxBehaviorId} instances, which will be
 * assigned the next available bit position (up to 64 total).
 *
 * @author xI-Mx-Ix
 */
public final class VxBehaviors {

    private VxBehaviors() {} // No instantiation

    // --- Physics Type Behaviors ---

    /**
     * Indicates the body uses rigid body physics simulation.
     * Bodies with this behavior are created and simulated as Jolt Rigid Bodies.
     */
    public static final VxBehaviorId RIGID_PHYSICS = new VxBehaviorId("RigidPhysics");

    /**
     * Indicates the body uses soft body physics simulation.
     * Bodies with this behavior are created and simulated as Jolt Soft Bodies
     * and have per-vertex deformation data.
     */
    public static final VxBehaviorId SOFT_PHYSICS = new VxBehaviorId("SoftPhysics");

    // --- Core System Behaviors ---

    /**
     * Enables persistence (save/load) for a body.
     * Bodies without this behavior are discarded on chunk unload.
     */
    public static final VxBehaviorId PERSISTENCE = new VxBehaviorId("Persistence");

    /**
     * Enables network synchronization for a body.
     * Bodies with this behavior have their transform and custom data
     * replicated to connected clients.
     */
    public static final VxBehaviorId NET_SYNC = new VxBehaviorId("NetSync");

    /**
     * Extracts physics data from Jolt back into the Java DataStore.
     * Required for any moving body.
     */
    public static final VxBehaviorId PHYSICS_SYNC = new VxBehaviorId("PhysicsSync");

    /**
     * Enables buoyancy simulation for a body.
     * Bodies with this behavior are affected by fluid forces (water, lava).
     */
    public static final VxBehaviorId BUOYANCY = new VxBehaviorId("Buoyancy");

    // --- User-Facing Behaviors ---

    /**
     * Enables server-tick callbacks for a body.
     * Bodies with this behavior receive per-game-tick updates on the server thread.
     */
    public static final VxBehaviorId SERVER_TICK = new VxBehaviorId("ServerTick");

    /**
     * Enables pre-physics-tick callbacks for a body.
     * Bodies with this behavior receive updates before each physics step.
     */
    public static final VxBehaviorId PRE_PHYSICS_TICK = new VxBehaviorId("PrePhysicsTick");

    /**
     * Enables post-physics-tick callbacks for a body.
     * Bodies with this behavior receive updates after each physics step.
     */
    public static final VxBehaviorId PHYSICS_TICK = new VxBehaviorId("PhysicsTick");
}