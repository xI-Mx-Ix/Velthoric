/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.type;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.VxRenderState;
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
     * Server-side constructor for a rigid body.
     *
     * @param type  The object type definition.
     * @param world The physics world this body belongs to.
     * @param id    The unique UUID for this body.
     */
    protected VxRigidBody(VxObjectType<? extends VxRigidBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    /**
     * Client-side constructor for a rigid body.
     *
     * @param id The unique UUID for this body.
     * @param typeId The resource location of the object's type.
     * @param objectType The Jolt body type.
     */
    @Environment(EnvType.CLIENT)
    protected VxRigidBody(UUID id, ResourceLocation typeId, EBodyType objectType) {
        super(id, typeId, objectType);
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

    @Override
    @Environment(EnvType.CLIENT)
    public void calculateRenderState(float partialTicks, VxRenderState outState, RVec3 tempPos, Quat tempRot) {
        VxClientObjectManager manager = VxClientObjectManager.getInstance();
        // Calculate the interpolated transform (position and rotation). This is generic for all rigid bodies.
        manager.getInterpolator().interpolateFrame(manager.getStore(), this.getDataStoreIndex(), partialTicks, tempPos, tempRot);
        outState.transform.getTranslation().set(tempPos);
        outState.transform.getRotation().set(tempRot);
        // Rigid bodies do not have vertex data.
        outState.vertexData = null;
    }
}