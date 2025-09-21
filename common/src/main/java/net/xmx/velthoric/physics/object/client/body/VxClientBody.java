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
import net.minecraft.world.phys.AABB;
import net.xmx.velthoric.physics.object.client.VxClientObjectDataStore;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.object.type.VxBody;

import java.util.UUID;

/**
 * An abstract representation of a physics object on the client side.
 * This class acts as a lightweight handle for accessing the object's data,
 * which is stored in the {@link VxClientObjectDataStore} for performance.
 * It encapsulates logic for state calculation, culling, and rendering.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxClientBody {

    protected final UUID id;
    protected final VxClientObjectManager manager;
    protected final int dataStoreIndex;
    protected final EBodyType objectType;
    protected final VxBody.Renderer renderer;

    /**
     * Constructs a new client-side body handle.
     *
     * @param id             The unique identifier of the physics object.
     * @param manager        The manager that oversees all client-side objects.
     * @param dataStoreIndex The index of this object's data in the data store arrays.
     * @param objectType     The type of the body (e.g., RigidBody, SoftBody).
     * @param renderer       The client-side renderer instance for this object.
     */
    protected VxClientBody(UUID id, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType, VxBody.Renderer renderer) {
        this.id = id;
        this.manager = manager;
        this.dataStoreIndex = dataStoreIndex;
        this.objectType = objectType;
        this.renderer = renderer;
    }

    /**
     * @return The unique identifier of this object.
     */
    public UUID getId() {
        return id;
    }

    /**
     * @return The index of this object in the {@link VxClientObjectDataStore}.
     */
    public int getDataStoreIndex() {
        return dataStoreIndex;
    }

    /**
     * Checks if the object has been fully initialized with at least one state update,
     * making it ready for rendering.
     *
     * @return {@code true} if the object is initialized, {@code false} otherwise.
     */
    public boolean isInitialized() {
        return manager.getStore().render_isInitialized[dataStoreIndex];
    }

    /**
     * Creates an Axis-Aligned Bounding Box (AABB) based on the object's last known position.
     * This is used for frustum culling to avoid rendering objects that are off-screen.
     *
     * @param inflation The amount to inflate the bounding box by, to prevent culling at screen edges.
     * @return A new {@link AABB} for the object.
     */
    public AABB getCullingAABB(float inflation) {
        RVec3 lastPos = manager.getStore().lastKnownPosition[dataStoreIndex];
        return new AABB(
                lastPos.xx() - inflation, lastPos.yy() - inflation, lastPos.zz() - inflation,
                lastPos.xx() + inflation, lastPos.yy() + inflation, lastPos.zz() + inflation
        );
    }

    /**
     * Calculates the final, interpolated render state for the object for the current frame.
     * This method populates the provided output objects with the interpolated transform.
     *
     * @param partialTicks The fraction of a tick that has passed since the last full tick.
     * @param outState     The {@link VxRenderState} object to populate with the final state.
     * @param tempPos      A reusable {@link RVec3} to store the intermediate interpolated position.
     * @param tempRot      A reusable {@link Quat} to store the intermediate interpolated rotation.
     */
    public abstract void calculateRenderState(float partialTicks, VxRenderState outState, RVec3 tempPos, Quat tempRot);

    /**
     * Dispatches the render call to this object's specific renderer.
     *
     * @param poseStack    The current pose stack for transformations.
     * @param bufferSource The buffer source for drawing.
     * @param partialTicks The fraction of the current tick.
     * @param packedLight  The calculated light value at the object's position.
     * @param renderState  The final interpolated state to be rendered.
     */
    public abstract void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, int packedLight, VxRenderState renderState);
}