package net.xmx.velthoric.physics.object.physicsobject.client.renderer;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EBodyType;
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
import net.xmx.velthoric.physics.object.physicsobject.client.ClientObjectDataManager;
import net.xmx.velthoric.physics.object.physicsobject.client.interpolation.InterpolationFrame;
import net.xmx.velthoric.physics.object.physicsobject.client.interpolation.RenderState;
import net.xmx.velthoric.physics.object.physicsobject.type.rigid.VxRigidBody;
import net.xmx.velthoric.physics.object.physicsobject.type.soft.VxSoftBody;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.nio.ByteBuffer;
import java.util.UUID;

public class PhysicsObjectRenderer {

    private static final float CULLING_BOUNDS_INFLATION = 2.0f;
    private static final RenderState finalRenderState = new RenderState();

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

                InterpolationFrame frame = dataManager.getInterpolationFrame(id);
                if (frame == null || !frame.isInitialized) continue;

                EBodyType objectType = dataManager.getObjectType(id);
                if (objectType == EBodyType.RigidBody) {
                    renderRigidBody(mc, poseStack, bufferSource, partialTicks, id, frame, dataManager.getRigidRenderer(id), dataManager.getCustomData(id));
                } else if (objectType == EBodyType.SoftBody) {
                    renderSoftBody(mc, poseStack, bufferSource, partialTicks, id, frame, dataManager.getSoftRenderer(id), dataManager.getCustomData(id));
                }
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Error rendering physics object {}", id, e);
            }
        }

        poseStack.popPose();
        bufferSource.endBatch();
    }

    private static void renderRigidBody(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, UUID id, InterpolationFrame frame, VxRigidBody.Renderer renderer, ByteBuffer customData) {
        if (renderer == null) return;

        frame.interpolate(finalRenderState, partialTicks);

        poseStack.pushPose();

        RVec3 renderPosition = finalRenderState.transform.getTranslation();
        Quat renderRotation = finalRenderState.transform.getRotation();

        poseStack.translate(renderPosition.x(), renderPosition.y(), renderPosition.z());

        poseStack.mulPose(new Quaternionf(renderRotation.getX(), renderRotation.getY(), renderRotation.getZ(), renderRotation.getW()));

        int packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(renderPosition.x(), renderPosition.y(), renderPosition.z()));

        renderer.render(id, finalRenderState, customData, poseStack, bufferSource, partialTicks, packedLight);

        poseStack.popPose();
    }

    private static void renderSoftBody(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, UUID id, InterpolationFrame frame, VxSoftBody.Renderer renderer, ByteBuffer customData) {
        if (renderer == null) return;

        frame.interpolate(finalRenderState, partialTicks);

        if (finalRenderState.vertexData == null || finalRenderState.vertexData.length < 3) {
            return;
        }

        poseStack.pushPose();

        int packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(finalRenderState.vertexData[0], finalRenderState.vertexData[1], finalRenderState.vertexData[2]));

        renderer.render(id, finalRenderState, customData, poseStack, bufferSource, partialTicks, packedLight);

        poseStack.popPose();
    }
}