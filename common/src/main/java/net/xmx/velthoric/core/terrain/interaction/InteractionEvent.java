/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.terrain.interaction;

import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

/**
 * Represents a discrete physical interaction event triggered within the terrain system.
 * <p>
 * This interface is used to queue manual Java-side events that need to be processed
 * synchronously on the Minecraft server thread.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public interface InteractionEvent {
    /**
     * Executes the interaction logic within the context of the provided physics world.
     * This method is guaranteed to be called on the main server thread.
     *
     * @param world The Velthoric physics world context.
     */
    void handle(VxPhysicsWorld world);
}