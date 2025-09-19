/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.type;

import com.github.stephengold.joltjni.SoftBodyCreationSettings;
import com.github.stephengold.joltjni.SoftBodySharedSettings;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.VxBody;
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * An abstract base class for all soft body physics objects.
 * A soft body is a deformable object composed of a collection of vertices or particles,
 * simulated using techniques like mass-spring systems.
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
     * Subclasses must implement this to define the shared physical properties of the soft body material.
     *
     * @return A {@link SoftBodySharedSettings} object.
     */
    public abstract SoftBodySharedSettings createSoftBodySharedSettings();

    /**
     * Subclasses must implement this to define the initial shape and settings of the soft body instance.
     *
     * @param sharedSettings The shared settings created by {@link #createSoftBodySharedSettings()}.
     * @return A {@link SoftBodyCreationSettings} object.
     */
    public abstract SoftBodyCreationSettings createSoftBodyCreationSettings(SoftBodySharedSettings sharedSettings);

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
     * A nested interface for client-side renderers specific to soft bodies.
     */
    public interface Renderer extends VxBody.Renderer {
        /**
         * Renders the soft body.
         *
         * @param id           The UUID of the object being rendered.
         * @param renderState  The interpolated state for the current frame, including vertex data.
         * @param customData   A buffer with custom data from the server.
         * @param poseStack    The current pose stack for transformations.
         * @param bufferSource The buffer source for drawing.
         * @param partialTick  The fraction of the current tick.
         * @param packedLight  The calculated light value at the object's position.
         */
        void render(UUID id, VxRenderState renderState, ByteBuffer customData, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick, int packedLight);
    }
}