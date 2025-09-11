/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.world.pcmd;

import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * Represents a command that can be queued and executed on a specific {@link VxPhysicsWorld} physics thread.
 * This interface is the core of the command pattern used to safely interact with the physics simulation
 * from other threads (like the main server thread).
 *
 * @author xI-Mx-Ix
 */
@FunctionalInterface
public interface ICommand {

    /**
     * Executes the command's logic within the context of the given physics world.
     * This method is always called on the physics world's dedicated thread.
     *
     * @param world The physics world instance on which to execute the command.
     */
    void execute(VxPhysicsWorld world);
}