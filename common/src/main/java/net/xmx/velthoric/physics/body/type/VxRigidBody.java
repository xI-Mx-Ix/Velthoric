/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.type;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.client.VxRenderState;
import net.xmx.velthoric.physics.body.type.factory.VxRigidBodyFactory;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * An abstract base class for all rigid body physics bodies.
 * A rigid body has a fixed shape and is simulated using rigid body dynamics (e.g., it can rotate and translate).
 * Users should inherit from this class to create custom rigid bodies.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxRigidBody extends VxBody {

    /**
     * Server-side constructor for a rigid body.
     *
     * @param type  The body type definition.
     * @param world The physics world this body belongs to.
     * @param id    The unique UUID for this body.
     */
    protected VxRigidBody(VxBodyType<? extends VxRigidBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    /**
     * Client-side constructor for a rigid body.
     *
     * @param type The body type definition.
     * @param id The unique UUID for this body.
     */
    @Environment(EnvType.CLIENT)
    protected VxRigidBody(VxBodyType<? extends VxRigidBody> type, UUID id) {
        super(type, id);
    }

    /**
     * Defines and creates the Jolt physics body using the provided factory.
     * This method must be implemented by subclasses to define the shape and
     * properties of the rigid body.
     *
     * @param factory The factory provided by the VxBodyManager to create the body.
     * @return The body ID assigned by Jolt.
     */
    public abstract int createJoltBody(VxRigidBodyFactory factory);

    @Override
    @Environment(EnvType.CLIENT)
    public void calculateRenderState(float partialTicks, VxRenderState outState, RVec3 tempPos, Quat tempRot) {
        VxClientBodyManager manager = VxClientBodyManager.getInstance();
        // Calculate the interpolated transform (position and rotation). This is generic for all rigid bodies.
        manager.getInterpolator().interpolateFrame(manager.getStore(), this.getDataStoreIndex(), partialTicks, tempPos, tempRot);
        outState.transform.getTranslation().set(tempPos);
        outState.transform.getRotation().set(tempRot);
        // Rigid bodies do not have vertex data.
        outState.vertexData = null;
    }
}