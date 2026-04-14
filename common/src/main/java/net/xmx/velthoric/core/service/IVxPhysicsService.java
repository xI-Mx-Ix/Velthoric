/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.service;

import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

/**
 * Base interface for optional physics subsystem services.
 * <p>
 * Services registered with {@link VxServiceManager} must implement this interface.
 * These subsystems allow for extending the physics engine's functionality with global systems
 * (e.g., custom spatial querying, specialized debugging subsystems, or third-party integrations)
 * in a modular fashion.
 * <p>
 * <b>Note:</b> For logic that applies to specific physics bodies (like buoyancy, aerodynamics, or engines),
 * use the {@code VxBehavior} system instead.
 * <p>
 * <b>Performance Note:</b> Core managers (bodies, constraints, etc.) remain as direct fields in
 * {@link VxPhysicsWorld} for maximum cache efficiency and reduced pointer indirection. High-frequency
 * logic within these services should prioritize SoA data access where possible.
 *
 * @author xI-Mx-Ix
 */
public interface IVxPhysicsService {

    /**
     * Returns a unique identifier for this service type.
     * This identifier is used for debugging, logging, and potentially networking.
     *
     * @return The unique service identification string.
     */
    String getIdentification();

    /**
     * Called during the initialization phase of the physics world.
     * Use this to allocate resources, register listeners, or perform initial state setup.
     * <p>
     * <b>Threading:</b> Called from the Main Server Thread.
     */
    void initialize();

    /**
     * Called during the shutdown sequence of the physics world.
     * Implementations must release all resources and unregister any hooks to prevent memory leaks.
     * <p>
     * <b>Threading:</b> Called from the Main Server Thread.
     */
    void shutdown();

    /**
     * Optional: Invoked at the start of each physics simulation frame.
     * Useful for applying forces or modifying body states before Jolt performs the integration step.
     * <p>
     * <b>Threading:</b> Called from the Physics Thread (High Frequency: 60Hz default).
     *
     * @param world The physics world instance being simulated.
     * @implSpec The default implementation is a no-op.
     */
    default void onPrePhysicsTick(VxPhysicsWorld world) {
    }

    /**
     * Optional: Invoked after each physics simulation frame has completed.
     * Useful for post-processing results, updating custom spatial indexes, or triggering collision callbacks.
     * <p>
     * <b>Threading:</b> Called from the Physics Thread (High Frequency: 60Hz default).
     *
     * @param world The physics world instance that was just simulated.
     * @implSpec The default implementation is a no-op.
     */
    default void onPhysicsTick(VxPhysicsWorld world) {
    }

    /**
     * Optional: Invoked once per Minecraft game tick.
     * Useful for synchronization between the physics world and the Minecraft level (e.g., entity spawning, block updates).
     * <p>
     * <b>Threading:</b> Called from the Main Server Thread (Frequency: 20Hz).
     *
     * @param level The Minecraft server level associated with the physics world.
     * @implSpec The default implementation is a no-op.
     */
    default void onGameTick(ServerLevel level) {
    }
}