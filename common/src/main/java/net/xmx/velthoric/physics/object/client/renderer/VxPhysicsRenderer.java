/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.client.renderer;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.event.api.VxRenderEvent;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.body.VxClientBody;
import net.xmx.velthoric.physics.object.client.body.VxClientSoftBody;
import org.joml.Matrix4f;

/**
 * The main client-side renderer for all physics objects.
 * This class hooks into Minecraft's rendering pipeline to draw physics objects in the world.
 * It performs frustum culling and delegates state calculation and rendering to each
 * individual {@link VxClientBody} instance.
 *
 * @author xI-Mx-Ix
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

        poseStack.popPose();
        // End the batch to draw everything.
        bufferSource.endBatch();
    }
}