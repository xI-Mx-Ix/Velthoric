/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.client.renderer;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.event.api.VxRenderEvent;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxOBB;
import net.xmx.velthoric.mixin.impl.debug.EntityRenderDispatcherAccessor;
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.body.VxClientBody;
import net.xmx.velthoric.physics.object.client.body.VxClientSoftBody;
import net.xmx.velthoric.physics.riding.manager.VxClientRidingManager;
import net.xmx.velthoric.physics.riding.seat.VxSeat;
import org.joml.Matrix4f;

/**
 * The main client-side renderer for all physics objects.
 * This class hooks into Minecraft's rendering pipeline to draw physics objects in the world.
 * It performs frustum culling and delegates state calculation and rendering to each
 * individual {@link VxClientBody} instance.
 */
public class VxPhysicsRenderer {

    /** An inflation value for the culling bounding box to prevent objects from disappearing at the edge of the screen. */
    private static final float CULLING_BOUNDS_INFLATION = 2.0f;

    // Reusable objects to avoid allocations in the render loop.
    private static final VxRenderState finalRenderState = new VxRenderState();
    private static final RVec3 interpolatedPosition = new RVec3();
    private static final Quat interpolatedRotation = new Quat();

    /**
     * Registers the necessary render event listeners.
     */
    public static void registerEvents() {
        VxRenderEvent.ClientRenderLevelStageEvent.EVENT.register(VxPhysicsRenderer::onRenderLevelStage);
    }

    /**
     * The main render event handler, called every frame.
     *
     * @param event The render event containing context like the pose stack and partial ticks.
     */
    private static void onRenderLevelStage(VxRenderEvent.ClientRenderLevelStageEvent event) {
        // We render after entities to ensure correct transparency and depth testing.
        if (event.getStage() != VxRenderEvent.ClientRenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getCameraEntity() == null) return;

        VxClientObjectManager manager = VxClientObjectManager.getInstance();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        float partialTicks = event.getPartialTick();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        Matrix4f projectionMatrix = event.getProjectionMatrix();

        // Set up the frustum for culling objects outside the camera's view.
        Frustum frustum = new Frustum(poseStack.last().pose(), projectionMatrix);
        frustum.prepare(cameraPos.x, cameraPos.y, cameraPos.z);

        poseStack.pushPose();
        // Translate the world so rendering is relative to the camera position.
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        for (VxClientBody body : manager.getAllObjects()) {
            try {
                // Cull the object if it's not visible or hasn't been fully initialized.
                if (!body.isInitialized()) {
                    continue;
                }
                AABB objectAABB = body.getCullingAABB(CULLING_BOUNDS_INFLATION);
                if (!frustum.isVisible(objectAABB)) {
                    continue;
                }

                // Delegate to the body to calculate its final interpolated state for this frame.
                body.calculateRenderState(partialTicks, finalRenderState, interpolatedPosition, interpolatedRotation);

                // Determine the light level at the object's position.
                int packedLight;
                if (body instanceof VxClientSoftBody && finalRenderState.vertexData != null && finalRenderState.vertexData.length >= 3) {
                    // For soft bodies, use the position of the first vertex to sample light.
                    packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(finalRenderState.vertexData[0], finalRenderState.vertexData[1], finalRenderState.vertexData[2]));
                } else {
                    // For rigid bodies, use the main transform's position.
                    RVec3 renderPosition = finalRenderState.transform.getTranslation();
                    packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(renderPosition.xx(), renderPosition.yy(), renderPosition.zz()));
                }

                // Delegate the actual rendering call to the body.
                body.render(poseStack, bufferSource, partialTicks, packedLight, finalRenderState);

            } catch (Exception e) {
                VxMainClass.LOGGER.error("Error rendering physics object {}", body.getId(), e);
            }
        }

        // Render debug hitboxes if enabled (F3+B).
        if (((EntityRenderDispatcherAccessor) mc.getEntityRenderDispatcher()).getRenderHitBoxes()) {
            renderDebugHitboxes(poseStack, bufferSource, manager, partialTicks);
        }

        poseStack.popPose();
        // End the batch to draw everything.
        bufferSource.endBatch();
    }

    /**
     * Renders debug hitboxes for all rideable seats.
     * This is called when Minecraft's native "Show Hitboxes" (F3+B) is enabled.
     *
     * @param poseStack    The current pose stack, translated to camera-relative space.
     * @param bufferSource The buffer source for drawing lines.
     * @param manager      The client object manager.
     * @param partialTicks The current partial tick.
     */
    private static void renderDebugHitboxes(PoseStack poseStack, MultiBufferSource bufferSource, VxClientObjectManager manager, float partialTicks) {
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
        VxClientRidingManager ridingManager = VxClientRidingManager.getInstance();

        for (VxClientBody body : manager.getAllObjects()) {
            if (!body.isInitialized()) continue;

            // Re-calculate the render state for this object to ensure we have the correct transform.
            body.calculateRenderState(partialTicks, finalRenderState, interpolatedPosition, interpolatedRotation);

            for (VxSeat seat : ridingManager.getSeats(body.getId())) {
                // Get the precise Oriented Bounding Box for the seat.
                VxOBB obb = seat.getGlobalOBB(finalRenderState.transform);
                // Draw the OBB using its 8 world-space corners.
                drawOBB(vertexConsumer, poseStack, obb, 0.0f, 1.0f, 1.0f, 1.0f);
            }
        }
    }

    /**
     * Draws the wireframe of an Oriented Bounding Box (OBB).
     *
     * @param consumer  The vertex consumer for drawing lines.
     * @param poseStack The current pose stack.
     * @param obb       The OBB to draw.
     * @param r         Red color component (0-1).
     * @param g         Green color component (0-1).
     * @param b         Blue color component (0-1).
     * @param a         Alpha component (0-1).
     */
    private static void drawOBB(VertexConsumer consumer, PoseStack poseStack, VxOBB obb, float r, float g, float b, float a) {
        Vec3[] corners = obb.getCorners();

        // Bottom face
        drawLine(consumer, poseStack, corners[0], corners[1], r, g, b, a);
        drawLine(consumer, poseStack, corners[1], corners[5], r, g, b, a);
        drawLine(consumer, poseStack, corners[5], corners[4], r, g, b, a);
        drawLine(consumer, poseStack, corners[4], corners[0], r, g, b, a);

        // Top face
        drawLine(consumer, poseStack, corners[3], corners[2], r, g, b, a);
        drawLine(consumer, poseStack, corners[2], corners[6], r, g, b, a);
        drawLine(consumer, poseStack, corners[6], corners[7], r, g, b, a);
        drawLine(consumer, poseStack, corners[7], corners[3], r, g, b, a);

        // Connecting vertical lines
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