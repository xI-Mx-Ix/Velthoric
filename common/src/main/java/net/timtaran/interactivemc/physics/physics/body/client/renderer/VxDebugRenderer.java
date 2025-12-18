/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.physics.body.client.renderer;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.timtaran.interactivemc.physics.math.VxOBB;
import net.timtaran.interactivemc.physics.physics.body.client.VxClientBodyManager;
import net.timtaran.interactivemc.physics.physics.body.client.VxRenderState;
import net.timtaran.interactivemc.physics.physics.body.type.VxBody;
import net.timtaran.interactivemc.physics.physics.vehicle.VxVehicle;
import net.timtaran.interactivemc.physics.physics.vehicle.part.VxPart;

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
     * Currently, this includes the Oriented Bounding Boxes (OBBs) for all vehicle parts.
     *
     * @param poseStack    The current pose stack, translated to camera-relative space.
     * @param bufferSource The buffer source for drawing lines.
     * @param manager      The client body manager.
     * @param partialTicks The current partial tick for interpolation.
     */
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, VxClientBodyManager manager, float partialTicks) {
        renderPartHitboxes(poseStack, bufferSource, manager, partialTicks);
    }

    /**
     * Renders the hitboxes for all vehicle parts as wireframe OBBs.
     * All parts are rendered in yellow to distinguish them from standard hitboxes.
     *
     * @param poseStack    The current pose stack.
     * @param bufferSource The buffer source.
     * @param manager      The client body manager.
     * @param partialTicks The current partial tick.
     */
    private void renderPartHitboxes(PoseStack poseStack, MultiBufferSource bufferSource, VxClientBodyManager manager, float partialTicks) {
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());

        for (VxBody body : manager.getAllBodies()) {
            if (!body.isInitialized()) continue;

            // Only process vehicles as they have the modular part system
            if (body instanceof VxVehicle vehicle) {
                // Calculate the interpolated render state for the parent body.
                vehicle.calculateRenderState(partialTicks, this.renderState, this.interpolatedPosition, this.interpolatedRotation);

                // Iterate over all attached parts (wheels, seats, doors, custom parts)
                for (VxPart part : vehicle.getParts()) {
                    // Get the precise Oriented Bounding Box for the part in world space.
                    VxOBB obb = part.getGlobalOBB(this.renderState);

                    // Draw the OBB's wireframe in yellow.
                    drawOBB(vertexConsumer, poseStack, obb, 1.0f, 1.0f, 0.0f, 1.0f);
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

        consumer.addVertex(last.pose(), (float)p1.x, (float)p1.y, (float)p1.z)
                .setColor(r, g, b, a)
                .setNormal(last, 0, 1, 0);

        consumer.addVertex(last.pose(), (float)p2.x, (float)p2.y, (float)p2.z)
                .setColor(r, g, b, a)
                .setNormal(last, 0, 1, 0);
    }
}