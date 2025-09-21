/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.client.body;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.object.type.VxRigidBody;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * A client-side handle for a rigid body physics object.
 *
 * @author xI-Mx-Ix
 */
public class VxClientRigidBody extends VxClientBody {

    /**
     * Constructs a new client-side rigid body handle.
     *
     * @param id             The unique identifier of the physics object.
     * @param manager        The manager that oversees all client-side objects.
     * @param dataStoreIndex The index of this object's data in the data store arrays.
     * @param objectType     The type of the body.
     * @param renderer       The client-side renderer instance for this object.
     */
    public VxClientRigidBody(UUID id, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType, VxBody.Renderer renderer) {
        super(id, manager, dataStoreIndex, objectType, renderer);
    }

    @Override
    public void calculateRenderState(float partialTicks, VxRenderState outState, RVec3 tempPos, Quat tempRot) {
        // Calculate the interpolated transform (position and rotation).
        manager.getInterpolator().interpolateFrame(manager.getStore(), this.dataStoreIndex, partialTicks, tempPos, tempRot);
        outState.transform.getTranslation().set(tempPos);
        outState.transform.getRotation().set(tempRot);
        // Rigid bodies do not have vertex data.
        outState.vertexData = null;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, int packedLight, VxRenderState renderState) {
        // Cast to the specific renderer type.
        VxRigidBody.Renderer specificRenderer = (VxRigidBody.Renderer) this.renderer;
        if (specificRenderer == null) {
            return;
        }

        // Retrieve custom data from the store for this frame.
        ByteBuffer customData = manager.getStore().customData[dataStoreIndex];

        // Call the renderer's render method.
        specificRenderer.render(this.id, renderState, customData, poseStack, bufferSource, partialTicks, packedLight);
    }
}