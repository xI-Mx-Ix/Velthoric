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
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.client.VxRenderState;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.vehicle.VxVehicle;
import net.xmx.velthoric.physics.vehicle.part.VxPart;

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
public class VxDebugRenderer {

    // Reusable objects to avoid allocations in the render loop.
    private final VxRenderState renderState = new VxRenderState();
    private final RVec3 interpolatedPosition = new RVec3();
    private final Quat interpolatedRotation = new Quat();

    /**
     * Renders debug visualizations for all relevant physics components.
     * Currently, this includes the Oriented Bounding Boxes (OBBs) for all vehicle parts.
     *
     * @param poseStack    The current pose stack.
     * @param bufferSource The buffer source for drawing lines.
     * @param manager      The client body manager containing the physics bodies.
     * @param partialTicks The current partial tick for interpolation.
     * @param cameraPos    The absolute position of the camera in the world, used for relative rendering.
     */
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, VxClientBodyManager manager, float partialTicks, Vec3 cameraPos) {
        renderPartHitboxes(poseStack, bufferSource, manager, partialTicks, cameraPos);
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
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());

        for (VxBody body : manager.getAllBodies()) {
            if (!body.isInitialized()) continue;

            // Only process vehicles as they have the modular part system
            if (body instanceof VxVehicle vehicle) {
                // Calculate the interpolated render state for the parent body.
                vehicle.calculateRenderState(partialTicks, this.renderState, this.interpolatedPosition, this.interpolatedRotation);

                // Iterate over all attached parts (wheels, seats, doors, custom parts)
                for (VxPart part : vehicle.getParts()) {
                    // Get the precise Oriented Bounding Box for the part in absolute world space.
                    VxOBB obb = part.getGlobalOBB(this.renderState);

                    // Draw the OBB's wireframe in yellow, adjusting for the camera position.
                    drawOBB(vertexConsumer, poseStack, obb, cameraPos, 1.0f, 1.0f, 0.0f, 1.0f);
                }
            }
        }
    }

    /**
     * Draws the wireframe of an Oriented Bounding Box (OBB).
     *
     * @param consumer  The vertex consumer for drawing lines.
     * @param poseStack The current pose stack.
     * @param obb       The OBB to draw containing absolute world coordinates.
     * @param cameraPos The camera position to subtract for relative rendering.
     * @param r,g,b,a   Color components for the lines.
     */
    private void drawOBB(VertexConsumer consumer, PoseStack poseStack, VxOBB obb, Vec3 cameraPos, float r, float g, float b, float a) {
        Vec3[] corners = obb.getCorners();

        // Edges of the bottom face
        drawLine(consumer, poseStack, corners[0], corners[1], cameraPos, r, g, b, a);
        drawLine(consumer, poseStack, corners[1], corners[5], cameraPos, r, g, b, a);
        drawLine(consumer, poseStack, corners[5], corners[4], cameraPos, r, g, b, a);
        drawLine(consumer, poseStack, corners[4], corners[0], cameraPos, r, g, b, a);

        // Edges of the top face
        drawLine(consumer, poseStack, corners[3], corners[2], cameraPos, r, g, b, a);
        drawLine(consumer, poseStack, corners[2], corners[6], cameraPos, r, g, b, a);
        drawLine(consumer, poseStack, corners[6], corners[7], cameraPos, r, g, b, a);
        drawLine(consumer, poseStack, corners[7], corners[3], cameraPos, r, g, b, a);

        // Vertical edges connecting top and bottom faces
        drawLine(consumer, poseStack, corners[0], corners[3], cameraPos, r, g, b, a);
        drawLine(consumer, poseStack, corners[1], corners[2], cameraPos, r, g, b, a);
        drawLine(consumer, poseStack, corners[4], corners[7], cameraPos, r, g, b, a);
        drawLine(consumer, poseStack, corners[5], corners[6], cameraPos, r, g, b, a);
    }

    /**
     * Helper method to draw a single line between two absolute world points.
     * <p>
     * This method performs the subtraction of the camera position using double precision
     * before casting to float for the vertex buffer. This prevents vertex jitter
     * that occurs when rendering geometry far from the world origin.
     *
     * @param consumer  The vertex consumer.
     * @param poseStack The current pose stack.
     * @param p1        The start point of the line in absolute world coordinates.
     * @param p2        The end point of the line in absolute world coordinates.
     * @param cameraPos The absolute camera position.
     * @param r,g,b,a   Color components.
     */
    private static void drawLine(VertexConsumer consumer, PoseStack poseStack, Vec3 p1, Vec3 p2, Vec3 cameraPos, float r, float g, float b, float a) {
        PoseStack.Pose last = poseStack.last();

        // Convert absolute world coordinates to camera-relative coordinates.
        // We use double precision for the subtraction to maintain accuracy.
        float x1 = (float) (p1.x - cameraPos.x);
        float y1 = (float) (p1.y - cameraPos.y);
        float z1 = (float) (p1.z - cameraPos.z);

        float x2 = (float) (p2.x - cameraPos.x);
        float y2 = (float) (p2.y - cameraPos.y);
        float z2 = (float) (p2.z - cameraPos.z);

        consumer.addVertex(last.pose(), x1, y1, z1)
                .setColor(r, g, b, a)
                .setNormal(last, 0, 1, 0);

        consumer.addVertex(last.pose(), x2, y2, z2)
                .setColor(r, g, b, a)
                .setNormal(last, 0, 1, 0);
    }
}