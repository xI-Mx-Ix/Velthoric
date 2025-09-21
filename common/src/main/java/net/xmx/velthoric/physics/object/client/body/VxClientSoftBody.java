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
 * An abstract client-side handle for a soft body physics object.
 * This class provides the generic logic for calculating the render state of any soft body,
 * including its transform and vertex data.
 * Subclasses must implement the specific rendering logic and data handling.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxClientSoftBody extends VxClientBody {

    protected VxClientSoftBody(UUID id, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType) {
        super(id, manager, dataStoreIndex, objectType);
    }

    @Override
    public void calculateRenderState(float partialTicks, VxRenderState outState, RVec3 tempPos, Quat tempRot) {
        // Calculate the base interpolated transform (position and rotation).
        manager.getInterpolator().interpolateFrame(manager.getStore(), this.dataStoreIndex, partialTicks, tempPos, tempRot);
        outState.transform.getTranslation().set(tempPos);
        outState.transform.getRotation().set(tempRot);

        // Also calculate the interpolated vertex data for the soft body mesh. This is generic.
        outState.vertexData = manager.getInterpolator().getInterpolatedVertexData(manager.getStore(), this.dataStoreIndex, partialTicks);
    }
}