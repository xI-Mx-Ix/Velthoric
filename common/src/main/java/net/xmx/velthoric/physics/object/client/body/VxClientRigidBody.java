/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.client.body;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.VxRenderState;

import java.util.UUID;

/**
 * An abstract client-side handle for a rigid body physics object.
 * This class provides the generic logic for calculating the render state of any rigid body.
 * Subclasses must implement the specific rendering logic and data handling.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxClientRigidBody extends VxClientBody {

    protected VxClientRigidBody(UUID id, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType) {
        super(id, manager, dataStoreIndex, objectType);
    }

    @Override
    public void calculateRenderState(float partialTicks, VxRenderState outState, RVec3 tempPos, Quat tempRot) {
        // Calculate the interpolated transform (position and rotation). This is generic for all rigid bodies.
        manager.getInterpolator().interpolateFrame(manager.getStore(), this.dataStoreIndex, partialTicks, tempPos, tempRot);
        outState.transform.getTranslation().set(tempPos);
        outState.transform.getRotation().set(tempRot);
        // Rigid bodies do not have vertex data.
        outState.vertexData = null;
    }
}