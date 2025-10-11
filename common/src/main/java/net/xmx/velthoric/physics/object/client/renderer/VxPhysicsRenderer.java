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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.event.api.VxRenderEvent;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.mixin.impl.debug.EntityRenderDispatcherAccessor;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.object.client.body.renderer.VxBodyRenderer;
import net.xmx.velthoric.physics.object.registry.VxObjectRegistry;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;
import org.joml.Matrix4f;

/**
 * The main client-side renderer for all physics objects.
 * This class hooks into Minecraft's rendering pipeline. It performs frustum culling,
 * delegates state calculation to the {@link VxBody}, and then delegates the
 * final drawing call to a registered {@link VxBodyRenderer}.
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

        VxClientObjectManager manager = VxClientObjectManager.getInstance();
        if (manager.getAllObjects().isEmpty()) return;

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

        for (VxBody body : manager.getAllObjects()) {
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
                VxMainClass.LOGGER.error("Error rendering physics object {}", body.getPhysicsId(), e);
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
        VxBodyRenderer renderer = VxObjectRegistry.getInstance().getClientRenderer(typeId);

        if (renderer != null) {
            // This unchecked call is necessary due to the generic nature of the registry.
            renderer.render(body, poseStack, bufferSource, partialTicks, packedLight, renderState);
        } else {
            VxMainClass.LOGGER.warn("No renderer registered for physics object type: {}", typeId);
        }
    }
}