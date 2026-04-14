/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.services;

import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

/**
 * Base interface for optional physics subsystem services.
 * <p>
 * Services registered with {@link ServiceManager} must implement this interface.
 * Core managers (bodies, constraints, etc.) should remain as direct fields
 * in VxPhysicsWorld for maximum performance.
 *
 * @author LOLAtom
 */
public interface IPhysicsService {

    /**
     * Returns a unique identifier for this service type.
     * Used for debugging and logging.
     *
     * @return The service identification string.
     */
    String getIdentification();

    /**
     * Called when the physics world is initializing.
     * Use this to set up resources, register events, etc.
     * <p>
     * Called from: Server thread, during {@code VxPhysicsWorld.initializeAndStart()}
     */
    void initialize();

    /**
     * Called when the physics world is shutting down.
     * Use this to clean up resources, unregister events, etc.
     * <p>
     * Called from: Server thread, during {@code VxPhysicsWorld.shutdown()}
     */
    void shutdown();

    /**
     * Optional: Called before each physics simulation step.
     * <p>
     * Called from: Physics thread, at 60Hz
     *
     * @implSpec Default implementation does nothing.
     */
    default void onPrePhysicsTick(VxPhysicsWorld world) {}

    /**
     * Optional: Called after each physics simulation step.
     * <p>
     * Called from: Physics thread, at 60Hz
     *
     * @implSpec Default implementation does nothing.
     */
    default void onPhysicsTick(VxPhysicsWorld world) {}

    /**
     * Optional: Called on game tick (server thread, not physics thread).
     * <p>
     * Called from: Server thread, at 20Hz (Minecraft tick rate)
     *
     * @implSpec Default implementation does nothing.
     */
    default void onGameTick(ServerLevel level) {}
}