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

    private VxBehaviors() {
        // No instantiation
    }

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

    /**
     * Enables ticking callbacks for a body.
     * Bodies with this behavior receive updates on the server thread (per-game-tick),
     * and on the physics thread (pre and post simulation step).
     */
    public static final VxBehaviorId TICK = new VxBehaviorId("Tick");

    /**
     * Enables custom data synchronization between client and server.
     * Bodies with this behavior can use the VxSynchronizedData system to replicate custom properties.
     */
    public static final VxBehaviorId CUSTOM_DATA_SYNC = new VxBehaviorId("CustomDataSync");

    /**
     * Marks a body as mountable by players.
     * Bodies with this behavior have their seats registered in the {@link net.xmx.velthoric.core.mounting.behavior.VxMountBehavior}
     * when they are added to the world, enabling player mounting interaction.
     * The body must implement {@link net.xmx.velthoric.core.mounting.VxMountable} to define its seats.
     */
    public static final VxBehaviorId MOUNTABLE = new VxBehaviorId("Mountable");
}