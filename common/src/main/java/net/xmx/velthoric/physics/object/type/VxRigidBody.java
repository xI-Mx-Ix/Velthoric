/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.type;

import com.github.stephengold.joltjni.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.raycasting.click.Clickable;
import net.xmx.velthoric.physics.riding.Rideable;
import net.xmx.velthoric.physics.riding.seat.Seat;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * An abstract base class for all rigid body physics objects.
 * A rigid body has a fixed shape and is simulated using rigid body dynamics (e.g., it can rotate and translate).
 * This class also implements {@link Rideable} and {@link Clickable} interfaces, providing default
 * empty implementations for subclasses to override.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxRigidBody extends VxAbstractBody implements Rideable, Clickable {

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
     * Subclasses must implement this to define the shape of the rigid body.
     *
     * @return A {@link ShapeSettings} object describing the geometry.
     */
    public abstract ShapeSettings createShapeSettings();

    /**
     * Subclasses must implement this to define the physical properties of the body.
     *
     * @param shapeRef A reference to the created shape.
     * @return A {@link BodyCreationSettings} object with properties like mass, friction, etc.
     */
    public abstract BodyCreationSettings createBodyCreationSettings(ShapeRefC shapeRef);

    /**
     * A nested interface for client-side renderers specific to rigid bodies.
     */
    public interface Renderer extends VxAbstractBody.Renderer {
        /**
         * Renders the rigid body.
         *
         * @param id           The UUID of the object being rendered.
         * @param renderState  The interpolated state for the current frame.
         * @param customData   A buffer with custom data from the server.
         * @param poseStack    The current pose stack for transformations.
         * @param bufferSource The buffer source for drawing.
         * @param partialTick  The fraction of the current tick.
         * @param packedLight  The calculated light value at the object's position.
         */
        void render(UUID id, VxRenderState renderState, ByteBuffer customData, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick, int packedLight);
    }

    // ---- Rideable Interface (Default Implementations) ---- //

    @Override
    public void onStartRiding(ServerPlayer player, Seat seat) {}

    @Override
    public void onStopRiding(ServerPlayer player) {}

    // ---- Clickable Interface (Default Implementations) ---- //

    @Override
    public void onLeftClick(ServerPlayer player, Vec3 hitPoint, Vec3 hitNormal) {}

    @Override
    public void onRightClick(ServerPlayer player, Vec3 hitPoint, Vec3 hitNormal) {}
}