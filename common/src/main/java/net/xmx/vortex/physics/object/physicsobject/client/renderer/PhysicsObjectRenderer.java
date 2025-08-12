package net.xmx.vortex.physics.object.physicsobject.client.renderer;

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
import net.xmx.vortex.event.api.VxRenderEvent;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.object.physicsobject.EObjectType;
import net.xmx.vortex.physics.object.physicsobject.client.ClientObjectDataManager;
import net.xmx.vortex.physics.object.physicsobject.client.interpolation.InterpolatedRenderState;
import net.xmx.vortex.physics.object.physicsobject.client.interpolation.RenderData;
import net.xmx.vortex.physics.object.physicsobject.type.rigid.VxRigidBody;
import net.xmx.vortex.physics.object.physicsobject.type.soft.VxSoftBody;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.nio.ByteBuffer;
import java.util.UUID;

public class PhysicsObjectRenderer {

    private static final float CULLING_BOUNDS_INFLATION = 2.0f;

    public static void registerEvents() {
        VxRenderEvent.ClientRenderLevelStageEvent.EVENT.register(PhysicsObjectRenderer::onRenderLevelStage);
    }

    private static void onRenderLevelStage(VxRenderEvent.ClientRenderLevelStageEvent event) {
        if (event.getStage() != VxRenderEvent.ClientRenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getCameraEntity() == null) return;

        ClientObjectDataManager dataManager = ClientObjectDataManager.getInstance();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        float partialTicks = event.getPartialTick();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        Matrix4f projectionMatrix = event.getProjectionMatrix();
        Matrix4f modelViewMatrix = poseStack.last().pose();
        Frustum frustum = new Frustum(modelViewMatrix, projectionMatrix);
        frustum.prepare(cameraPos.x, cameraPos.y, cameraPos.z);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        for (UUID id : dataManager.getAllObjectIds()) {
            try {
                RVec3 lastPos = dataManager.getLatestPosition(id);
                if (lastPos == null) continue;

                AABB objectAABB = new AABB(
                        lastPos.x() - CULLING_BOUNDS_INFLATION, lastPos.y() - CULLING_BOUNDS_INFLATION, lastPos.z() - CULLING_BOUNDS_INFLATION,
                        lastPos.x() + CULLING_BOUNDS_INFLATION, lastPos.y() + CULLING_BOUNDS_INFLATION, lastPos.z() + CULLING_BOUNDS_INFLATION
                );

                if (!frustum.isVisible(objectAABB)) {
                    continue;
                }

                InterpolatedRenderState renderState = dataManager.getRenderState(id);
                if (renderState == null || !renderState.isInitialized) continue;

                EObjectType objectType = dataManager.getObjectType(id);
                if (objectType == EObjectType.RIGID_BODY) {
                    renderRigidBody(mc, poseStack, bufferSource, partialTicks, id, renderState, dataManager.getRigidRenderer(id), dataManager.getCustomData(id));
                } else if (objectType == EObjectType.SOFT_BODY) {
                    renderSoftBody(mc, poseStack, bufferSource, partialTicks, id, renderState, dataManager.getSoftRenderer(id), dataManager.getCustomData(id));
                }
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Error rendering physics object {}", id, e);
            }
        }

        poseStack.popPose();
        bufferSource.endBatch();
    }

    private static void renderRigidBody(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, UUID id, InterpolatedRenderState renderState, VxRigidBody.Renderer renderer, ByteBuffer customData) {
        if (renderer == null) return;

        RenderData finalRenderData = RenderData.interpolate(renderState, partialTicks, new RenderData());

        poseStack.pushPose();

        RVec3 renderPosition = finalRenderData.transform.getTranslation();
        Quat renderRotation = finalRenderData.transform.getRotation();

        poseStack.translate(renderPosition.x(), renderPosition.y(), renderPosition.z());

        poseStack.mulPose(new Quaternionf(renderRotation.getX(), renderRotation.getY(), renderRotation.getZ(), renderRotation.getW()));

        int packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(renderPosition.x(), renderPosition.y(), renderPosition.z()));

        renderer.render(id, finalRenderData, customData, poseStack, bufferSource, partialTicks, packedLight);

        poseStack.popPose();
    }

    private static void renderSoftBody(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, UUID id, InterpolatedRenderState renderState, VxSoftBody.Renderer renderer, ByteBuffer customData) {
        if (renderer == null) return;

        RenderData finalRenderData = RenderData.interpolate(renderState, partialTicks, new RenderData());

        if (finalRenderData.vertexData == null || finalRenderData.vertexData.length < 3) {
            return;
        }

        poseStack.pushPose();

        int packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(finalRenderData.vertexData[0], finalRenderData.vertexData[1], finalRenderData.vertexData[2]));

        renderer.render(id, finalRenderData, customData, poseStack, bufferSource, partialTicks, packedLight);

        poseStack.popPose();
    }
}