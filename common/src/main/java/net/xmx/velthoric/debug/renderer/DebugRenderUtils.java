/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.debug.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.math.VxOBB;
import org.jetbrains.annotations.NotNull;

/**
 * A utility class containing low-level primitive drawing methods for debug rendering.
 * <p>
 * This class provides standard geometric shapes (boxes, spheres, capsules, cylinders)
 * represented as wireframes. It focuses on precision and efficient vertex generation
 * within the Minecraft rendering pipeline.
 *
 * @author xI-Mx-Ix
 */
public class DebugRenderUtils {

    /**
     * Draws a wireframe box based on half-extents.
     *
     * @param consumer    The vertex consumer for the lines.
     * @param poseStack   The current pose stack (should be pre-transformed to the box center).
     * @param halfExtents The half-extents of the box (width/2, height/2, depth/2).
     * @param r,g,b,a     The color components.
     */
    public static void drawBox(@NotNull VertexConsumer consumer, @NotNull PoseStack poseStack, com.github.stephengold.joltjni.Vec3 halfExtents, float r, float g, float b, float a) {
        float hx = halfExtents.getX();
        float hy = halfExtents.getY();
        float hz = halfExtents.getZ();

        // Bottom rectangle
        drawLineLocal(consumer, poseStack, -hx, -hy, -hz, hx, -hy, -hz, r, g, b, a);
        drawLineLocal(consumer, poseStack, hx, -hy, -hz, hx, -hy, hz, r, g, b, a);
        drawLineLocal(consumer, poseStack, hx, -hy, hz, -hx, -hy, hz, r, g, b, a);
        drawLineLocal(consumer, poseStack, -hx, -hy, hz, -hx, -hy, -hz, r, g, b, a);

        // Top rectangle
        drawLineLocal(consumer, poseStack, -hx, hy, -hz, hx, hy, -hz, r, g, b, a);
        drawLineLocal(consumer, poseStack, hx, hy, -hz, hx, hy, hz, r, g, b, a);
        drawLineLocal(consumer, poseStack, hx, hy, hz, -hx, hy, hz, r, g, b, a);
        drawLineLocal(consumer, poseStack, -hx, hy, hz, -hx, hy, -hz, r, g, b, a);

        // Vertical pillars connecting top and bottom
        drawLineLocal(consumer, poseStack, -hx, -hy, -hz, -hx, hy, -hz, r, g, b, a);
        drawLineLocal(consumer, poseStack, hx, -hy, -hz, hx, hy, -hz, r, g, b, a);
        drawLineLocal(consumer, poseStack, hx, -hy, hz, hx, hy, hz, r, g, b, a);
        drawLineLocal(consumer, poseStack, -hx, -hy, hz, -hx, hy, hz, r, g, b, a);
    }

    /**
     * Draws a wireframe sphere using three orthogonal circles.
     *
     * @param consumer  The vertex consumer.
     * @param poseStack The current pose stack.
     * @param radius    The radius of the sphere.
     * @param r,g,b,a   The color components.
     */
    public static void drawSphere(@NotNull VertexConsumer consumer, @NotNull PoseStack poseStack, float radius, float r, float g, float b, float a) {
        int segments = 16;
        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (i * Math.PI * 2 / segments);
            float angle2 = (float) ((i + 1) * Math.PI * 2 / segments);

            float c1 = (float) Math.cos(angle1) * radius;
            float s1 = (float) Math.sin(angle1) * radius;
            float c2 = (float) Math.cos(angle2) * radius;
            float s2 = (float) Math.sin(angle2) * radius;

            // XY circle
            drawLineLocal(consumer, poseStack, c1, s1, 0, c2, s2, 0, r, g, b, a);
            // XZ circle
            drawLineLocal(consumer, poseStack, c1, 0, s1, c2, 0, s2, r, g, b, a);
            // YZ circle
            drawLineLocal(consumer, poseStack, 0, c1, s1, 0, c2, s2, r, g, b, a);
        }
    }

    /**
     * Draws a wireframe capsule (cylinder with hemispherical caps).
     *
     * @param consumer   The vertex consumer.
     * @param poseStack  The current pose stack.
     * @param halfHeight The half-height of the cylindrical part.
     * @param radius     The radius of the capsule.
     * @param r,g,b,a    The color components.
     */
    public static void drawCapsule(@NotNull VertexConsumer consumer, @NotNull PoseStack poseStack, float halfHeight, float radius, float r, float g, float b, float a) {
        int segments = 16;
        // Drawing top and bottom circles of the cylinder
        for (int i = 0; i < segments; i++) {
            float a1 = (float) (i * Math.PI * 2 / segments);
            float a2 = (float) ((i + 1) * Math.PI * 2 / segments);

            float c1 = (float) Math.cos(a1) * radius;
            float s1 = (float) Math.sin(a1) * radius;
            float c2 = (float) Math.cos(a2) * radius;
            float s2 = (float) Math.sin(a2) * radius;

            drawLineLocal(consumer, poseStack, c1, -halfHeight, s1, c2, -halfHeight, s2, r, g, b, a);
            drawLineLocal(consumer, poseStack, c1, halfHeight, s1, c2, halfHeight, s2, r, g, b, a);
        }

        // Vertical cylinder lines
        drawLineLocal(consumer, poseStack, radius, -halfHeight, 0, radius, halfHeight, 0, r, g, b, a);
        drawLineLocal(consumer, poseStack, -radius, -halfHeight, 0, -radius, halfHeight, 0, r, g, b, a);
        drawLineLocal(consumer, poseStack, 0, -halfHeight, radius, 0, halfHeight, radius, r, g, b, a);
        drawLineLocal(consumer, poseStack, 0, -halfHeight, -radius, 0, halfHeight, -radius, r, g, b, a);

        // Hemispherical caps arcs
        for (int i = 0; i < segments / 2; i++) {
            float a1 = (float) (i * Math.PI / (segments / 2)), a2 = (float) ((i + 1) * Math.PI / (segments / 2));
            float s1 = (float) Math.sin(a1) * radius, c1 = (float) Math.cos(a1) * radius;
            float s2 = (float) Math.sin(a2) * radius, c2 = (float) Math.cos(a2) * radius;

            // Bottom cap (inverted arcs)
            drawLineLocal(consumer, poseStack, (float) Math.sin(a1 + Math.PI) * radius, (float) Math.cos(a1 + Math.PI) * radius - halfHeight, 0, (float) Math.sin(a2 + Math.PI) * radius, (float) Math.cos(a2 + Math.PI) * radius - halfHeight, 0, r, g, b, a);
            drawLineLocal(consumer, poseStack, 0, (float) Math.cos(a1 + Math.PI) * radius - halfHeight, (float) Math.sin(a1 + Math.PI) * radius, 0, (float) Math.cos(a2 + Math.PI) * radius - halfHeight, (float) Math.sin(a2 + Math.PI) * radius, r, g, b, a);

            // Top cap
            drawLineLocal(consumer, poseStack, s1, c1 + halfHeight, 0, s2, c2 + halfHeight, 0, r, g, b, a);
            drawLineLocal(consumer, poseStack, 0, c1 + halfHeight, s1, 0, c2 + halfHeight, s2, r, g, b, a);
        }
    }

    /**
     * Draws a wireframe cylinder (represented as a tapered cylinder with equal radii).
     */
    public static void drawCylinder(@NotNull VertexConsumer consumer, @NotNull PoseStack poseStack, float halfHeight, float radius, float r, float g, float b, float a) {
        drawTaperedCylinder(consumer, poseStack, halfHeight, radius, radius, r, g, b, a);
    }

    /**
     * Draws a wireframe tapered cylinder (frustum).
     *
     * @param consumer     The vertex consumer.
     * @param poseStack    The current pose stack.
     * @param halfHeight   The half-height along the Y axis.
     * @param topRadius    The radius at +halfHeight.
     * @param bottomRadius The radius at -halfHeight.
     * @param r,g,b,a      The color components.
     */
    public static void drawTaperedCylinder(@NotNull VertexConsumer consumer, @NotNull PoseStack poseStack, float halfHeight, float topRadius, float bottomRadius, float r, float g, float b, float a) {
        int segments = 16;
        for (int i = 0; i < segments; i++) {
            float a1 = (float) (i * Math.PI * 2 / segments), a2 = (float) ((i + 1) * Math.PI * 2 / segments);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);

            // Bottom circle
            drawLineLocal(consumer, poseStack, c1 * bottomRadius, -halfHeight, s1 * bottomRadius, c2 * bottomRadius, -halfHeight, s2 * bottomRadius, r, g, b, a);
            // Top circle
            drawLineLocal(consumer, poseStack, c1 * topRadius, halfHeight, s1 * topRadius, c2 * topRadius, halfHeight, s2 * topRadius, r, g, b, a);
        }

        // Side edges (connecting top and bottom circles)
        drawLineLocal(consumer, poseStack, bottomRadius, -halfHeight, 0, topRadius, halfHeight, 0, r, g, b, a);
        drawLineLocal(consumer, poseStack, -bottomRadius, -halfHeight, 0, -topRadius, halfHeight, 0, r, g, b, a);
        drawLineLocal(consumer, poseStack, 0, -halfHeight, bottomRadius, 0, halfHeight, topRadius, r, g, b, a);
        drawLineLocal(consumer, poseStack, 0, -halfHeight, -bottomRadius, 0, halfHeight, -topRadius, r, g, b, a);
    }

    /**
     * Draws a wireframe tapered capsule.
     */
    public static void drawTaperedCapsule(@NotNull VertexConsumer consumer, @NotNull PoseStack poseStack, float halfHeight, float topRadius, float bottomRadius, float r, float g, float b, float a) {
        int segments = 16;
        for (int i = 0; i < segments; i++) {
            float a1 = (float) (i * Math.PI * 2 / segments), a2 = (float) ((i + 1) * Math.PI * 2 / segments);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);

            drawLineLocal(consumer, poseStack, c1 * bottomRadius, -halfHeight, s1 * bottomRadius, c2 * bottomRadius, -halfHeight, s2 * bottomRadius, r, g, b, a);
            drawLineLocal(consumer, poseStack, c1 * topRadius, halfHeight, s1 * topRadius, c2 * topRadius, halfHeight, s2 * topRadius, r, g, b, a);
        }
        drawLineLocal(consumer, poseStack, bottomRadius, -halfHeight, 0, topRadius, halfHeight, 0, r, g, b, a);
        drawLineLocal(consumer, poseStack, -bottomRadius, -halfHeight, 0, -topRadius, halfHeight, 0, r, g, b, a);
        drawLineLocal(consumer, poseStack, 0, -halfHeight, bottomRadius, 0, halfHeight, topRadius, r, g, b, a);
        drawLineLocal(consumer, poseStack, 0, -halfHeight, -bottomRadius, 0, halfHeight, -topRadius, r, g, b, a);

        for (int i = 0; i < segments / 2; i++) {
            float a1 = (float) (i * Math.PI / (segments / 2)), a2 = (float) ((i + 1) * Math.PI / (segments / 2));
            float s1_b = (float) Math.sin(a1 + Math.PI) * bottomRadius, c1_b = (float) Math.cos(a1 + Math.PI) * bottomRadius;
            float s2_b = (float) Math.sin(a2 + Math.PI) * bottomRadius, c2_b = (float) Math.cos(a2 + Math.PI) * bottomRadius;
            float s1_t = (float) Math.sin(a1) * topRadius, c1_t = (float) Math.cos(a1) * topRadius;
            float s2_t = (float) Math.sin(a2) * topRadius, c2_t = (float) Math.cos(a2) * topRadius;

            // Bottom cap arcs
            drawLineLocal(consumer, poseStack, s1_b, c1_b - halfHeight, 0, s2_b, c2_b - halfHeight, 0, r, g, b, a);
            drawLineLocal(consumer, poseStack, 0, c1_b - halfHeight, s1_b, 0, c2_b - halfHeight, s2_b, r, g, b, a);
            // Top cap arcs
            drawLineLocal(consumer, poseStack, s1_t, c1_t + halfHeight, 0, s2_t, c2_t + halfHeight, 0, r, g, b, a);
            drawLineLocal(consumer, poseStack, 0, c1_t + halfHeight, s1_t, 0, c2_t + halfHeight, s2_t, r, g, b, a);
        }
    }

    /**
     * Draws an Oriented Bounding Box (OBB) using absolute world coordinates relative to the camera.
     *
     * @param consumer  The vertex consumer.
     * @param poseStack The current pose stack.
     * @param obb       The OBB to draw.
     * @param cameraPos The absolute world position of the camera.
     * @param r,g,b,a   The color components.
     */
    public static void drawOBB(@NotNull VertexConsumer consumer, @NotNull PoseStack poseStack, @NotNull VxOBB obb, @NotNull Vec3 cameraPos, float r, float g, float b, float a) {
        Vec3[] corners = obb.getCorners();

        // Bottom face
        drawLine(consumer, poseStack, corners[0], corners[1], cameraPos, r, g, b, a);
        drawLine(consumer, poseStack, corners[1], corners[5], cameraPos, r, g, b, a);
        drawLine(consumer, poseStack, corners[5], corners[4], cameraPos, r, g, b, a);
        drawLine(consumer, poseStack, corners[4], corners[0], cameraPos, r, g, b, a);

        // Top face
        drawLine(consumer, poseStack, corners[3], corners[2], cameraPos, r, g, b, a);
        drawLine(consumer, poseStack, corners[2], corners[6], cameraPos, r, g, b, a);
        drawLine(consumer, poseStack, corners[6], corners[7], cameraPos, r, g, b, a);
        drawLine(consumer, poseStack, corners[7], corners[3], cameraPos, r, g, b, a);

        // Connecting pillars
        drawLine(consumer, poseStack, corners[0], corners[3], cameraPos, r, g, b, a);
        drawLine(consumer, poseStack, corners[1], corners[2], cameraPos, r, g, b, a);
        drawLine(consumer, poseStack, corners[4], corners[7], cameraPos, r, g, b, a);
        drawLine(consumer, poseStack, corners[5], corners[6], cameraPos, r, g, b, a);
    }

    /**
     * Draws a line between two points in the local coordinate system of the current PoseStack.
     */
    public static void drawLineLocal(@NotNull VertexConsumer consumer, @NotNull PoseStack poseStack, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        PoseStack.Pose last = poseStack.last();
        consumer.addVertex(last.pose(), x1, y1, z1).setColor(r, g, b, a).setNormal(last, 0, 1, 0);
        consumer.addVertex(last.pose(), x2, y2, z2).setColor(r, g, b, a).setNormal(last, 0, 1, 0);
    }

    /**
     * Draws a line between two absolute world points by subtracting the camera position.
     * This uses double precision for the subtraction to prevent jittering.
     */
    public static void drawLine(@NotNull VertexConsumer consumer, @NotNull PoseStack poseStack, @NotNull Vec3 p1, @NotNull Vec3 p2, @NotNull Vec3 cameraPos, float r, float g, float b, float a) {
        PoseStack.Pose last = poseStack.last();
        float x1 = (float) (p1.x - cameraPos.x);
        float y1 = (float) (p1.y - cameraPos.y);
        float z1 = (float) (p1.z - cameraPos.z);
        float x2 = (float) (p2.x - cameraPos.x);
        float y2 = (float) (p2.y - cameraPos.y);
        float z2 = (float) (p2.z - cameraPos.z);
        consumer.addVertex(last.pose(), x1, y1, z1).setColor(r, g, b, a).setNormal(last, 0, 1, 0);
        consumer.addVertex(last.pose(), x2, y2, z2).setColor(r, g, b, a).setNormal(last, 0, 1, 0);
    }
}