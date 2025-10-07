/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.type;

import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.type.factory.VxSoftBodyFactory;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * An abstract base class for all soft body physics objects.
 * A soft body is a deformable object composed of a collection of vertices or particles,
 * simulated using techniques like mass-spring systems. Users should inherit from this
 * class to create custom soft bodies.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxSoftBody extends VxBody {

    // Caches the last vertex data that was synchronized to clients.
    protected float @Nullable [] lastSyncedVertexData;

    /**
     * Constructor for a soft body.
     *
     * @param type  The object type definition.
     * @param world The physics world this body belongs to.
     * @param id    The unique UUID for this body.
     */
    protected VxSoftBody(VxObjectType<? extends VxSoftBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    /**
     * @return The last vertex data that was sent to clients.
     */
    public float @Nullable [] getLastSyncedVertexData() {
        return this.lastSyncedVertexData;
    }

    /**
     * Sets the cached last synced vertex data.
     *
     * @param data The vertex data array.
     */
    public void setLastSyncedVertexData(float @Nullable [] data) {
        this.lastSyncedVertexData = data;
    }

    /**
     * Defines and creates the Jolt soft body using the provided factory.
     * This method must be implemented by subclasses to define the properties
     * of the soft body.
     *
     * @param factory The factory provided by the VxObjectManager to create the body.
     * @return The body ID assigned by Jolt.
     */
    public abstract int createJoltBody(VxSoftBodyFactory factory);
}