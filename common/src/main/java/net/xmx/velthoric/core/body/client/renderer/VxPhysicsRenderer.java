/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.client.renderer;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.event.api.VxRenderEvent;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.mixin.impl.culling.LevelRendererAccessor;
import net.xmx.velthoric.mixin.impl.debug.EntityRenderDispatcherAccessor;
import net.xmx.velthoric.core.body.client.VxClientBodyManager;
import net.xmx.velthoric.core.body.client.VxRenderState;
import net.xmx.velthoric.core.body.client.body.renderer.VxBodyRenderer;
import net.xmx.velthoric.core.body.registry.VxBodyRegistry;
import net.xmx.velthoric.core.body.type.VxBody;
import net.xmx.velthoric.core.body.type.VxSoftBody;
import net.xmx.velthoric.core.physics.world.VxClientPhysicsWorld;

/**
 * The main client-side renderer/dispatcher for physics bodies.
 * <p>
 * This class mimics the behavior of {@link net.minecraft.client.renderer.entity.EntityRenderDispatcher}.
 * It handles the orchestration of rendering all active physics bodies, performing frustum culling,
 * lighting calculations, and crash report handling for rendering errors.
 *
 * @author xI-Mx-Ix
 */
public class VxPhysicsRenderer {

    /**
     * Inflation value to prevent objects from disappearing when their center is off-screen but edges are visible.
     */
    private static final float CULLING_BOUNDS_INFLATION = 2.0f;

    // Reusable containers to reduce garbage collection pressure during the render loop.
    private static final VxRenderState sharedRenderState = new VxRenderState();
    private static final RVec3 interpolatedPosition = new RVec3();
    private static final Quat interpolatedRotation = new Quat();

    // Dedicated debug renderer instance.
    private static final VxDebugRenderer debugRenderer = new VxDebugRenderer();

    /**
     * Registers the render event listeners.
     */
    public static void registerEvents() {
        VxRenderEvent.ClientRenderLevelStageEvent.EVENT.register(VxPhysicsRenderer::onRenderLevelStage);
    }

    /**
     * Main event hook called by the rendering pipeline.
     * Prepares the render context and iterates over all physics bodies.
     *
     * @param event The render level stage event.
     */
    private static void onRenderLevelStage(VxRenderEvent.ClientRenderLevelStageEvent event) {
        if (event.getStage() != VxRenderEvent.ClientRenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || mc.getCameraEntity() == null) return;

        VxClientBodyManager manager = VxClientPhysicsWorld.getInstance().getBodyManager();
        if (manager.getAllBodies().isEmpty()) return;

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        float partialTicks = event.getPartialTick();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        Frustum frustum = ((LevelRendererAccessor) mc.levelRenderer).velthoric_getCullingFrustum();

        poseStack.pushPose();

        for (VxBody body : manager.getAllBodies()) {
            if (shouldRender(body, frustum)) {
                render(body, level, poseStack, bufferSource, cameraPos, partialTicks);
            }
        }

        // Render debug hitboxes if the vanilla "F3+B" debug option is enabled.
        if (((EntityRenderDispatcherAccessor) mc.getEntityRenderDispatcher()).getRenderHitBoxes()) {
            debugRenderer.render(poseStack, bufferSource, manager, partialTicks, cameraPos);
        }

        poseStack.popPose();
        bufferSource.endBatch();
    }

    /**
     * Checks if the body is visible and initialized.
     *
     * @param body    The physics body to check.
     * @param frustum The current camera frustum.
     * @return True if the body should be rendered.
     */
    private static boolean shouldRender(VxBody body, Frustum frustum) {
        if (!body.isInitialized()) {
            return false;
        }
        AABB cullingBox = body.getCullingAABB(CULLING_BOUNDS_INFLATION);
        return frustum.isVisible(cullingBox);
    }

    /**
     * Renders a single physics body.
     * <p>
     * This method handles state interpolation, light calculation, and camera-relative translation.
     * It wraps the actual rendering call in a try-catch block to generate a crash report
     * if a specific renderer fails, rather than crashing the entire game loop silently.
     *
     * @param body         The body to render.
     * @param level        The current client level.
     * @param poseStack    The pose stack.
     * @param bufferSource The buffer source.
     * @param cameraPos    The absolute camera position.
     * @param partialTicks The partial tick time for interpolation.
     */
    private static void render(VxBody body, Level level, PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, float partialTicks) {
        try {
            // Interpolate physics state
            body.calculateRenderState(partialTicks, sharedRenderState, interpolatedPosition, interpolatedRotation);
            RVec3 absPos = sharedRenderState.transform.getTranslation();

            // Calculate rendering offset (Camera Relative)
            double relX = absPos.x() - cameraPos.x;
            double relY = absPos.y() - cameraPos.y;
            double relZ = absPos.z() - cameraPos.z;

            // Calculate lighting
            int packedLight = getPackedLight(body, level, absPos, sharedRenderState);

            poseStack.pushPose();
            poseStack.translate(relX, relY, relZ);

            renderBodyInternal(body, poseStack, bufferSource, partialTicks, packedLight, sharedRenderState);

            poseStack.popPose();

        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.forThrowable(throwable, "Rendering Velthoric Physics Body");
            CrashReportCategory category = crashReport.addCategory("Physics Body Details");
            category.setDetail("Body ID", body.getPhysicsId().toString());
            category.setDetail("Body Type", body.getTypeId().toString());
            category.setDetail("Initialized", body.isInitialized());
            throw new ReportedException(crashReport);
        }
    }

    /**
     * Internal method to look up the registry and dispatch to the specific {@link VxBodyRenderer}.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderBodyInternal(VxBody body, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight, VxRenderState state) {
        ResourceLocation typeId = body.getTypeId();
        VxBodyRenderer renderer = VxBodyRegistry.getInstance().getClientRenderer(typeId);

        if (renderer != null) {
            renderer.render(body, poseStack, bufferSource, partialTicks, packedLight, state);
        } else {
            VxMainClass.LOGGER.warn("No renderer registered for physics body type: {}", typeId);
        }
    }

    /**
     * Calculates the packed light coordinates for the body.
     * Handles special cases for soft bodies where vertex data might be more relevant than center position.
     */
    private static int getPackedLight(VxBody body, Level level, RVec3 absPos, VxRenderState state) {
        BlockPos lightPos;
        if (body instanceof VxSoftBody && state.vertexData != null && state.vertexData.length >= 3) {
            lightPos = BlockPos.containing(state.vertexData[0], state.vertexData[1], state.vertexData[2]);
        } else {
            lightPos = BlockPos.containing(absPos.xx(), absPos.yy(), absPos.zz());
        }
        return LevelRenderer.getLightColor(level, lightPos);
    }
}