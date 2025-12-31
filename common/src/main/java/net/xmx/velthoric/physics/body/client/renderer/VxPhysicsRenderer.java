/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.client.renderer;

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
import net.xmx.velthoric.mixin.impl.culling.LevelRendererAccessor;
import net.xmx.velthoric.mixin.impl.debug.EntityRenderDispatcherAccessor;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.client.VxRenderState;
import net.xmx.velthoric.physics.body.client.body.renderer.VxBodyRenderer;
import net.xmx.velthoric.physics.body.registry.VxBodyRegistry;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.body.type.VxSoftBody;
import net.xmx.velthoric.physics.world.VxClientPhysicsWorld;

/**
 * The main client-side renderer for all physics bodies.
 * This class hooks into Minecraft's rendering pipeline and orchestrates the drawing of all active physics objects.
 * <p>
 * It handles:
 * <ul>
 *     <li>Frustum culling using the vanilla Minecraft frustum.</li>
 *     <li>Interpolation of physics states for smooth rendering.</li>
 *     <li>High-precision camera-relative positioning to prevent floating-point jitter at large coordinates.</li>
 *     <li>Delegation to specific {@link VxBodyRenderer} implementations.</li>
 * </ul>
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
     * The main render event handler, called every frame during the level rendering process.
     * <p>
     * This method iterates through all active physics bodies, performs frustum culling,
     * interpolates their physics state, and renders them relative to the camera to ensure
     * visual stability. It also triggers the debug renderer if the hitbox debug flag is active.
     *
     * @param event The render event containing context like the pose stack and partial ticks.
     */
    private static void onRenderLevelStage(VxRenderEvent.ClientRenderLevelStageEvent event) {
        // We render after entities to ensure correct transparency and depth testing.
        if (event.getStage() != VxRenderEvent.ClientRenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getCameraEntity() == null) return;

        VxClientBodyManager manager = VxClientPhysicsWorld.getInstance().getBodyManager();
        if (manager.getAllBodies().isEmpty()) return;

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        float partialTicks = event.getPartialTick();

        // Retrieve the absolute position of the camera for relative rendering calculations.
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        // Retrieve the vanilla Frustum via Mixin accessor to match entity culling logic.
        Frustum frustum = ((LevelRendererAccessor) mc.levelRenderer).velthoric_getCullingFrustum();

        poseStack.pushPose();

        for (VxBody body : manager.getAllBodies()) {
            try {
                // Skip if the body hasn't been fully initialized.
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

                RVec3 absRenderPosition = finalRenderState.transform.getTranslation();

                // Calculate the light level at the body's position.
                int packedLight;
                if (body instanceof VxSoftBody && finalRenderState.vertexData != null && finalRenderState.vertexData.length >= 3) {
                    packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(finalRenderState.vertexData[0], finalRenderState.vertexData[1], finalRenderState.vertexData[2]));
                } else {
                    packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(absRenderPosition.xx(), absRenderPosition.yy(), absRenderPosition.zz()));
                }

                // Perform Camera-Relative Rendering.
                // We calculate the difference between the absolute body position and the camera position
                // using Double precision on the CPU. The resulting vector is small enough to be
                // safely cast to Float for the GPU matrix without precision loss (jitter).
                double relativeX = absRenderPosition.x() - cameraPos.x;
                double relativeY = absRenderPosition.y() - cameraPos.y;
                double relativeZ = absRenderPosition.z() - cameraPos.z;

                poseStack.pushPose();
                poseStack.translate(relativeX, relativeY, relativeZ);

                // Look up the renderer and delegate the rendering call.
                // The renderer receives the PoseStack already centered at the body's position.
                renderBody(body, poseStack, bufferSource, partialTicks, packedLight, finalRenderState);

                poseStack.popPose();

            } catch (Exception e) {
                VxMainClass.LOGGER.error("Error rendering physics body {}", body.getPhysicsId(), e);
            }
        }

        // Render debug information if enabled (F3+B).
        // We pass the camera position to allow the debug renderer to perform its own
        // camera-relative coordinate transformations for absolute world-space shapes.
        if (((EntityRenderDispatcherAccessor) mc.getEntityRenderDispatcher()).getRenderHitBoxes()) {
            debugRenderer.render(poseStack, bufferSource, manager, partialTicks, cameraPos);
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
            // This unchecked call is necessary due to the generic nature of the registry.
            renderer.render(body, poseStack, bufferSource, partialTicks, packedLight, renderState);
        } else {
            VxMainClass.LOGGER.warn("No renderer registered for physics body type: {}", typeId);
        }
    }
}