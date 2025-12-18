/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.physics.body.client.renderer;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.timtaran.interactivemc.physics.event.api.VxRenderEvent;
import net.timtaran.interactivemc.physics.init.VxMainClass;
import net.timtaran.interactivemc.physics.mixin.impl.culling.LevelRendererAccessor;
import net.timtaran.interactivemc.physics.mixin.impl.debug.EntityRenderDispatcherAccessor;
import net.timtaran.interactivemc.physics.physics.body.client.VxClientBodyManager;
import net.timtaran.interactivemc.physics.physics.body.client.VxRenderState;
import net.timtaran.interactivemc.physics.physics.body.client.body.renderer.VxBodyRenderer;
import net.timtaran.interactivemc.physics.physics.body.registry.VxBodyRegistry;
import net.timtaran.interactivemc.physics.physics.body.type.VxBody;
import net.timtaran.interactivemc.physics.physics.body.type.VxSoftBody;

/**
 * The main client-side renderer for all physics bodies.
 * This class hooks into Minecraft's rendering pipeline.
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

    // A dedicated renderer for debug visualizations.
    private static final VxDebugRenderer debugRenderer = new VxDebugRenderer();

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

        VxClientBodyManager manager = VxClientBodyManager.getInstance();
        if (manager.getAllBodies().isEmpty()) return;

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        float partialTicks = event.getPartialTick();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        // Retrieve the vanilla Frustum via our Mixin accessor.
        // This ensures we use the EXACT same culling logic as the rest of the game,
        // preventing issues where the frustum is inverted or desynchronized from the camera.
        Frustum frustum = ((LevelRendererAccessor) mc.levelRenderer).velthoric_getCullingFrustum();

        poseStack.pushPose();
        // Translate the world so rendering is relative to the camera position.
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        for (VxBody body : manager.getAllBodies()) {
            try {
                // Cull the body if it's not visible or hasn't been fully initialized.
                if (!body.isInitialized()) {
                    continue;
                }

                // Check visibility against the vanilla frustum.
                AABB objectAABB = body.getCullingAABB(CULLING_BOUNDS_INFLATION);
                if (!frustum.isVisible(objectAABB)) {
                    continue;
                }

                // Delegate to the body to calculate its final interpolated state for this frame.
                body.calculateRenderState(partialTicks, finalRenderState, interpolatedPosition, interpolatedRotation);

                // Determine the light level at the body's position.
                int packedLight;
                if (body instanceof VxSoftBody && finalRenderState.vertexData != null && finalRenderState.vertexData.length >= 3) {
                    // For soft bodies, use the position of the first vertex to sample light.
                    packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(finalRenderState.vertexData[0], finalRenderState.vertexData[1], finalRenderState.vertexData[2]));
                } else {
                    // For rigid bodies, use the main transform's position.
                    RVec3 renderPosition = finalRenderState.transform.getTranslation();
                    packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(renderPosition.xx(), renderPosition.yy(), renderPosition.zz()));
                }

                // Look up the renderer and delegate the rendering call.
                renderBody(body, poseStack, bufferSource, partialTicks, packedLight, finalRenderState);

            } catch (Exception e) {
                VxMainClass.LOGGER.error("Error rendering physics body {}", body.getPhysicsId(), e);
            }
        }

        // Render debug information if enabled (F3+B).
        if (((EntityRenderDispatcherAccessor) mc.getEntityRenderDispatcher()).getRenderHitBoxes()) {
            debugRenderer.render(poseStack, bufferSource, manager, partialTicks);
        }

        poseStack.popPose();
        // End the batch to draw everything.
        bufferSource.endBatch();
    }

    /**
     * Looks up the appropriate renderer for the given body and calls its render method.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderBody(VxBody body, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, int packedLight, VxRenderState renderState) {
        ResourceLocation typeId = body.getTypeId();
        VxBodyRenderer renderer = VxBodyRegistry.getInstance().getClientRenderer(typeId);

        if (renderer != null) {
            renderer.render(body, poseStack, bufferSource, partialTicks, packedLight, renderState);
        } else {
            VxMainClass.LOGGER.warn("No renderer registered for physics body type: {}", typeId);
        }
    }
}