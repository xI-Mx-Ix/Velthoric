/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.type;

import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.type.factory.VxRigidBodyFactory;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * An abstract base class for all rigid body physics objects.
 * A rigid body has a fixed shape and is simulated using rigid body dynamics (e.g., it can rotate and translate).
 * Users should inherit from this class to create custom rigid bodies.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxRigidBody extends VxBody {

    /**
     * Constructor for a rigid body.
     *
     * @param type  The object type definition.
     * @param world The physics world this body belongs to.
     * @param id    The unique UUID for this body.
     */
    protected VxRigidBody(VxObjectType<? extends VxRigidBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    /**
     * Defines and creates the Jolt physics body using the provided factory.
     * This method must be implemented by subclasses to define the shape and
     * properties of the rigid body.
     *
     * @param factory The factory provided by the VxObjectManager to create the body.
     * @return The body ID assigned by Jolt.
     */
    public abstract int createJoltBody(VxRigidBodyFactory factory);
}