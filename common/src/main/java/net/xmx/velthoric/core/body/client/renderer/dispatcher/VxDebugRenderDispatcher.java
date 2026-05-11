/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.client.renderer.dispatcher;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.body.client.VxClientBodyManager;
import net.xmx.velthoric.core.vehicle.VxVehicle;
import net.xmx.velthoric.core.vehicle.part.VxPart;
import net.xmx.velthoric.debug.renderer.DebugBodyShapeRenderer;
import net.xmx.velthoric.math.VxOBB;

/**
 * A dedicated renderer for drawing debug information related to physics bodies,
 * such as hitboxes and collision shapes.
 * <p>
 * This functionality is only active when debug modes are enabled. It handles
 * the rendering of complex shapes by transforming absolute world coordinates
 * into camera-relative coordinates to maintain floating-point precision.
 *
 * @author xI-Mx-Ix
 */
public class VxDebugRenderDispatcher {

    /**
     * Specialized renderer for collision shapes and OBBs.
     */
    private final DebugBodyShapeRenderer shapeRenderer = new DebugBodyShapeRenderer();

    /**
     * Renders debug visualizations for all relevant physics components.
     * Currently, this includes the Oriented Bounding Boxes (OBBs) for all vehicle parts
     * and the recursive collision shapes of all physics bodies.
     *
     * @param poseStack    The current pose stack.
     * @param bufferSource The buffer source for drawing lines.
     * @param manager      The client body manager containing the physics bodies.
     * @param partialTicks The current partial tick for interpolation.
     * @param cameraPos    The absolute position of the camera in the world, used for relative rendering.
     */
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, VxClientBodyManager manager, float partialTicks, Vec3 cameraPos) {
        renderPartHitboxes(poseStack, bufferSource, manager, partialTicks, cameraPos);
        renderBodyShapes(poseStack, bufferSource, manager, partialTicks, cameraPos);
    }

    /**
     * Renders the hitboxes for all vehicle parts as wireframe OBBs.
     * All parts are rendered in yellow to distinguish them from standard hitboxes.
     *
     * @param poseStack    The current pose stack.
     * @param bufferSource The buffer source.
     * @param manager      The client body manager.
     * @param partialTicks The current partial tick.
     * @param cameraPos    The absolute position of the camera.
     */
    private void renderPartHitboxes(PoseStack poseStack, MultiBufferSource bufferSource, VxClientBodyManager manager, float partialTicks, Vec3 cameraPos) {
        for (VxBody body : manager.getAllBodies()) {
            if (!body.isInitialized()) continue;

            // Only process vehicles as they have the modular part system
            if (body instanceof VxVehicle vehicle) {
                // Calculate the interpolated render state for the parent body.
                vehicle.calculateRenderState(partialTicks, shapeRenderer.getRenderState(), null, null);

                // Iterate over all attached parts (wheels, seats, doors, custom parts)
                for (VxPart part : vehicle.getParts()) {
                    // Get the precise Oriented Bounding Box for the part in absolute world space.
                    VxOBB obb = part.getGlobalOBB(shapeRenderer.getRenderState());

                    // Draw the OBB's wireframe in yellow, adjusting for the camera position.
                    shapeRenderer.renderOBB(obb, poseStack, bufferSource, cameraPos, 1.0f, 1.0f, 0.0f, 1.0f);
                }
            }
        }
    }

    /**
     * Renders the collision shapes for all active physics bodies.
     *
     * @param poseStack    The current pose stack.
     * @param bufferSource The buffer source.
     * @param manager      The client body manager.
     * @param partialTicks The current partial tick.
     * @param cameraPos    The absolute position of the camera.
     */
    private void renderBodyShapes(PoseStack poseStack, MultiBufferSource bufferSource, VxClientBodyManager manager, float partialTicks, Vec3 cameraPos) {
        for (VxBody body : manager.getAllBodies()) {
            if (!body.isInitialized()) continue;

            if (body.getShape() != null) {
                // Delegate the recursive rendering to the specialized shape renderer.
                shapeRenderer.renderBodyShape(body, poseStack, bufferSource, partialTicks, cameraPos, 1.0f, 0.0f, 0.0f, 1.0f);
            }
        }
    }
}