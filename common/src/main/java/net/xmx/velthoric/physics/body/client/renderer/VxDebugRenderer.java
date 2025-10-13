/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.client.renderer;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.math.VxOBB;
import net.xmx.velthoric.physics.mounting.manager.VxClientMountingManager;
import net.xmx.velthoric.physics.mounting.seat.VxSeat;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.client.VxRenderState;
import net.xmx.velthoric.physics.body.type.VxBody;

/**
 * A dedicated renderer for drawing debug information related to physics bodies,
 * such as hitboxes and collision shapes.
 * This functionality is only active when debug modes are enabled.
 *
 * @author xI-Mx-Ix
 */
public class VxDebugRenderer {

    // Reusable objects to avoid allocations in the render loop.
    private final VxRenderState renderState = new VxRenderState();
    private final RVec3 interpolatedPosition = new RVec3();
    private final Quat interpolatedRotation = new Quat();

    /**
     * Renders debug visualizations for all relevant physics components.
     * Currently, this includes the Oriented Bounding Boxes (OBBs) for rideable seats.
     *
     * @param poseStack    The current pose stack, translated to camera-relative space.
     * @param bufferSource The buffer source for drawing lines.
     * @param manager      The client body manager.
     * @param partialTicks The current partial tick for interpolation.
     */
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, VxClientBodyManager manager, float partialTicks) {
        renderSeatHitboxes(poseStack, bufferSource, manager, partialTicks);
        // Future debug rendering calls (e.g., for collision shapes) can be added here.
    }

    /**
     * Renders the hitboxes for all rideable seats as wireframe OBBs.
     * Driver seats are rendered in red, and passenger seats are rendered in light blue.
     *
     * @param poseStack    The current pose stack.
     * @param bufferSource The buffer source.
     * @param manager      The client body manager.
     * @param partialTicks The current partial tick.
     */
    private void renderSeatHitboxes(PoseStack poseStack, MultiBufferSource bufferSource, VxClientBodyManager manager, float partialTicks) {
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
        VxClientMountingManager ridingManager = VxClientMountingManager.INSTANCE;

        for (VxBody body : manager.getAllBodies()) {
            if (!body.isInitialized()) continue;

            // Calculate the interpolated render state for the parent body.
            body.calculateRenderState(partialTicks, this.renderState, this.interpolatedPosition, this.interpolatedRotation);

            for (VxSeat seat : ridingManager.getSeats(body.getPhysicsId())) {
                // Get the precise Oriented Bounding Box for the seat in world space.
                VxOBB obb = seat.getGlobalOBB(this.renderState.transform);

                // Draw the OBB's wireframe with a color indicating its type.
                if (seat.isDriverSeat()) {
                    // Red for the driver seat
                    drawOBB(vertexConsumer, poseStack, obb, 1.0f, 0.0f, 0.0f, 1.0f);
                } else {
                    // Light blue for passenger seats
                    drawOBB(vertexConsumer, poseStack, obb, 0.2f, 0.6f, 1.0f, 1.0f);
                }
            }
        }
    }

    /**
     * Draws the wireframe of an Oriented Bounding Box (OBB).
     *
     * @param consumer  The vertex consumer for drawing lines.
     * @param poseStack The current pose stack.
     * @param obb       The OBB to draw.
     * @param r,g,b,a   Color components for the lines.
     */
    private void drawOBB(VertexConsumer consumer, PoseStack poseStack, VxOBB obb, float r, float g, float b, float a) {
        Vec3[] corners = obb.getCorners();

        // Edges of the bottom face
        drawLine(consumer, poseStack, corners[0], corners[1], r, g, b, a);
        drawLine(consumer, poseStack, corners[1], corners[5], r, g, b, a);
        drawLine(consumer, poseStack, corners[5], corners[4], r, g, b, a);
        drawLine(consumer, poseStack, corners[4], corners[0], r, g, b, a);

        // Edges of the top face
        drawLine(consumer, poseStack, corners[3], corners[2], r, g, b, a);
        drawLine(consumer, poseStack, corners[2], corners[6], r, g, b, a);
        drawLine(consumer, poseStack, corners[6], corners[7], r, g, b, a);
        drawLine(consumer, poseStack, corners[7], corners[3], r, g, b, a);

        // Vertical edges connecting top and bottom faces
        drawLine(consumer, poseStack, corners[0], corners[3], r, g, b, a);
        drawLine(consumer, poseStack, corners[1], corners[2], r, g, b, a);
        drawLine(consumer, poseStack, corners[4], corners[7], r, g, b, a);
        drawLine(consumer, poseStack, corners[5], corners[6], r, g, b, a);
    }

    /**
     * Helper method to draw a single line between two points.
     *
     * @param consumer  The vertex consumer.
     * @param poseStack The current pose stack.
     * @param p1        The start point of the line.
     * @param p2        The end point of the line.
     * @param r,g,b,a   Color components.
     */
    private static void drawLine(VertexConsumer consumer, PoseStack poseStack, Vec3 p1, Vec3 p2, float r, float g, float b, float a) {
        PoseStack.Pose last = poseStack.last();
        consumer.vertex(last.pose(), (float)p1.x, (float)p1.y, (float)p1.z).color(r, g, b, a).normal(last.normal(), 0, 1, 0).endVertex();
        consumer.vertex(last.pose(), (float)p2.x, (float)p2.y, (float)p2.z).color(r, g, b, a).normal(last.normal(), 0, 1, 0).endVertex();
    }
}