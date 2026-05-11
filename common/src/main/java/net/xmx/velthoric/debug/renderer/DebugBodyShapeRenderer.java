/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.debug.renderer;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.body.client.VxRenderState;
import net.xmx.velthoric.core.body.shape.*;
import net.xmx.velthoric.math.VxOBB;
import org.jetbrains.annotations.NotNull;

/**
 * A specialized renderer for visualizing {@link VxCollisionShape} hierarchies of physics bodies.
 * <p>
 * This class handles the high-level orchestration of shape rendering, including:
 * <ul>
 *     <li>Calculating interpolated world-space transforms for physics bodies.</li>
 *     <li>Managing {@link PoseStack} transformations for local coordinate spaces.</li>
 *     <li>Recursively traversing complex shape hierarchies (compounds, scaled shapes, etc.).</li>
 *     <li>Delegating primitive drawing to {@link DebugRenderUtils}.</li>
 * </ul>
 *
 * @author xI-Mx-Ix
 */
public class DebugBodyShapeRenderer {

    // Reusable state objects to minimize allocations during the render loop.
    private final VxRenderState renderState = new VxRenderState();
    private final RVec3 interpolatedPosition = new RVec3();
    private final Quat interpolatedRotation = new Quat();

    /**
     * Renders the complete collision shape of a physics body in the world.
     *
     * @param body         The physics body to render.
     * @param poseStack    The current pose stack.
     * @param bufferSource The source for line vertex buffers.
     * @param partialTicks Interpolation factor for smooth movement.
     * @param cameraPos    Absolute world position of the camera (for jitter prevention).
     * @param r,g,b,a      The color of the wireframe.
     */
    public void renderBodyShape(@NotNull VxBody body, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, float partialTicks, @NotNull Vec3 cameraPos, float r, float g, float b, float a) {
        VxCollisionShape shape = body.getShape();
        if (shape == null) return;

        // 1. Calculate the interpolated transform for the current frame.
        body.calculateRenderState(partialTicks, this.renderState, this.interpolatedPosition, this.interpolatedRotation);

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        poseStack.pushPose();

        // 2. Move to camera-relative position in world space.
        // We use double precision for the initial subtraction to maintain accuracy far from origin.
        float x = (float) (this.renderState.transform.getTranslation().x() - cameraPos.x);
        float y = (float) (this.renderState.transform.getTranslation().y() - cameraPos.y);
        float z = (float) (this.renderState.transform.getTranslation().z() - cameraPos.z);
        poseStack.translate(x, y, z);

        // 3. Apply the body's rotation.
        Quat q = this.renderState.transform.getRotation();
        poseStack.mulPose(new org.joml.Quaternionf(q.getX(), q.getY(), q.getZ(), q.getW()));

        // 4. Recursively render the shape hierarchy.
        renderShapeRecursive(consumer, poseStack, shape, r, g, b, a);

        poseStack.popPose();
    }

    /**
     * Internal recursive method to traverse and draw collision shapes.
     * This method handles coordinate space transformations for child shapes.
     *
     * @param consumer  The vertex consumer.
     * @param poseStack The pose stack, already transformed to the current shape's local space.
     * @param shape     The shape to render.
     * @param r,g,b,a   Color components.
     */
    private void renderShapeRecursive(VertexConsumer consumer, PoseStack poseStack, VxCollisionShape shape, float r, float g, float b, float a) {
        // Primitive Shapes
        // These are the leaf nodes of the shape tree. They are drawn directly using utility methods.
        if (shape instanceof VxBoxShape box) {
            DebugRenderUtils.drawBox(consumer, poseStack, box.getHalfExtents(), r, g, b, a);
        } else if (shape instanceof VxSphereShape sphere) {
            DebugRenderUtils.drawSphere(consumer, poseStack, sphere.getRadius(), r, g, b, a);
        } else if (shape instanceof VxCapsuleShape capsule) {
            DebugRenderUtils.drawCapsule(consumer, poseStack, capsule.getHalfHeight(), capsule.getRadius(), r, g, b, a);
        } else if (shape instanceof VxCylinderShape cylinder) {
            DebugRenderUtils.drawCylinder(consumer, poseStack, cylinder.getHalfHeight(), cylinder.getRadius(), r, g, b, a);
        }

        // Compound Shapes (Requires Recursion & Transformation)
        // Compound shapes contain multiple child shapes, each with its own local offset and rotation.
        // We push a new pose for each child, transform it, and then recurse.
        else if (shape instanceof VxStaticCompoundShape compound) {
            for (VxStaticCompoundShape.ChildShape child : compound.getChildren()) {
                poseStack.pushPose();
                // 1. Shift to the child's local center.
                poseStack.translate(child.position().getX(), child.position().getY(), child.position().getZ());
                // 2. Rotate to the child's orientation.
                poseStack.mulPose(new org.joml.Quaternionf(child.rotation().getX(), child.rotation().getY(), child.rotation().getZ(), child.rotation().getW()));
                // 3. Render the child (which could be another compound).
                renderShapeRecursive(consumer, poseStack, child.shape(), r, g, b, a);
                poseStack.popPose();
            }
        } else if (shape instanceof VxMutableCompoundShape compound) {
            // Same logic as static compound, but children might change at runtime.
            for (VxMutableCompoundShape.ChildShape child : compound.getChildren()) {
                poseStack.pushPose();
                poseStack.translate(child.position().getX(), child.position().getY(), child.position().getZ());
                poseStack.mulPose(new org.joml.Quaternionf(child.rotation().getX(), child.rotation().getY(), child.rotation().getZ(), child.rotation().getW()));
                renderShapeRecursive(consumer, poseStack, child.shape(), r, g, b, a);
                poseStack.popPose();
            }
        }

        // Decorator / Transformation Shapes
        // These shapes wrap a single inner shape and apply a specific transformation to it.
        else if (shape instanceof VxRotatedTranslatedShape rt) {
            poseStack.pushPose();
            // Apply the fixed rotation and translation before rendering the inner shape.
            poseStack.translate(rt.getOffset().getX(), rt.getOffset().getY(), rt.getOffset().getZ());
            poseStack.mulPose(new org.joml.Quaternionf(rt.getRotation().getX(), rt.getRotation().getY(), rt.getRotation().getZ(), rt.getRotation().getW()));
            renderShapeRecursive(consumer, poseStack, rt.getInner(), r, g, b, a);
            poseStack.popPose();
        } else if (shape instanceof VxScaledShape scaled) {
            poseStack.pushPose();
            // Scaling affects the entire subtree.
            poseStack.scale(scaled.getScale().getX(), scaled.getScale().getY(), scaled.getScale().getZ());
            renderShapeRecursive(consumer, poseStack, scaled.getInner(), r, g, b, a);
            poseStack.popPose();
        } else if (shape instanceof VxOffsetCenterOfMassShape offset) {
            poseStack.pushPose();
            // Adjusts the visual center to match the physical offset center of mass.
            poseStack.translate(offset.getOffset().getX(), offset.getOffset().getY(), offset.getOffset().getZ());
            renderShapeRecursive(consumer, poseStack, offset.getInner(), r, g, b, a);
            poseStack.popPose();
        }

        // Specialized Mesh / Complex Shapes
        // These shapes have unique vertex data or custom drawing requirements.
        else if (shape instanceof VxTriangleShape triangle) {
            // Draw a simple wireframe triangle.
            DebugRenderUtils.drawLineLocal(consumer, poseStack, triangle.getV1().getX(), triangle.getV1().getY(), triangle.getV1().getZ(), triangle.getV2().getX(), triangle.getV2().getY(), triangle.getV2().getZ(), r, g, b, a);
            DebugRenderUtils.drawLineLocal(consumer, poseStack, triangle.getV2().getX(), triangle.getV2().getY(), triangle.getV2().getZ(), triangle.getV3().getX(), triangle.getV3().getY(), triangle.getV3().getZ(), r, g, b, a);
            DebugRenderUtils.drawLineLocal(consumer, poseStack, triangle.getV3().getX(), triangle.getV3().getY(), triangle.getV3().getZ(), triangle.getV1().getX(), triangle.getV1().getY(), triangle.getV1().getZ(), r, g, b, a);
        } else if (shape instanceof VxTaperedCapsuleShape taperedCapsule) {
            DebugRenderUtils.drawTaperedCapsule(consumer, poseStack, taperedCapsule.getHalfHeight(), taperedCapsule.getTopRadius(), taperedCapsule.getBottomRadius(), r, g, b, a);
        } else if (shape instanceof VxTaperedCylinderShape taperedCylinder) {
            DebugRenderUtils.drawTaperedCylinder(consumer, poseStack, taperedCylinder.getHalfHeight(), taperedCylinder.getTopRadius(), taperedCylinder.getBottomRadius(), r, g, b, a);
        } else if (shape instanceof VxConvexHullShape hull) {
            // Wireframing a full hull is computationally expensive; we draw crosshairs at the vertices for visibility.
            float[] pts = hull.getPoints();
            for (int i = 0; i < pts.length; i += 3) {
                float px = pts[i], py = pts[i + 1], pz = pts[i + 2];
                float s = 0.05f; 
                DebugRenderUtils.drawLineLocal(consumer, poseStack, px - s, py, pz, px + s, py, pz, r, g, b, a);
                DebugRenderUtils.drawLineLocal(consumer, poseStack, px, py - s, pz, px, py + s, pz, r, g, b, a);
                DebugRenderUtils.drawLineLocal(consumer, poseStack, px, py, pz - s, px, py, pz + s, r, g, b, a);
            }
        }
    }

    /**
     * Renders an Oriented Bounding Box (OBB).
     */
    public void renderOBB(@NotNull VxOBB obb, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, @NotNull Vec3 cameraPos, float r, float g, float b, float a) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        DebugRenderUtils.drawOBB(consumer, poseStack, obb, cameraPos, r, g, b, a);
    }

    /**
     * Returns the internal render state used for interpolation calculations.
     */
    public VxRenderState getRenderState() {
        return renderState;
    }
}