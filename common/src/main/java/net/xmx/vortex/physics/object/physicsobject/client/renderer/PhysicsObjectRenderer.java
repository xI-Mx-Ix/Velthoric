package net.xmx.vortex.physics.object.physicsobject.client.renderer;

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
import net.xmx.vortex.physics.object.physicsobject.client.interpolation.RenderData;
import net.xmx.vortex.physics.object.physicsobject.type.rigid.RigidPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.type.soft.SoftPhysicsObject;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.nio.ByteBuffer;
import java.util.UUID;

public class PhysicsObjectRenderer {

    private static final Quaternionf REUSABLE_QUATERNION = new Quaternionf();
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
                        lastPos.xx() - CULLING_BOUNDS_INFLATION, lastPos.yy() - CULLING_BOUNDS_INFLATION, lastPos.zz() - CULLING_BOUNDS_INFLATION,
                        lastPos.xx() + CULLING_BOUNDS_INFLATION, lastPos.yy() + CULLING_BOUNDS_INFLATION, lastPos.zz() + CULLING_BOUNDS_INFLATION
                );

                if (!frustum.isVisible(objectAABB)) {
                    continue;
                }

                RenderData renderData = dataManager.getRenderData(id, partialTicks);
                if (renderData == null) continue;

                EObjectType objectType = dataManager.getObjectType(id);
                if (objectType == EObjectType.RIGID_BODY) {
                    renderRigidBody(mc, poseStack, bufferSource, partialTicks, id, renderData, dataManager.getRigidRenderer(id), dataManager.getCustomData(id));
                } else if (objectType == EObjectType.SOFT_BODY) {
                    renderSoftBody(mc, poseStack, bufferSource, partialTicks, id, renderData, dataManager.getSoftRenderer(id), dataManager.getCustomData(id));
                }
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Error rendering physics object {}", id, e);
            }
        }

        poseStack.popPose();
        bufferSource.endBatch();
    }

    private static void renderRigidBody(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, UUID id, RenderData renderData, RigidPhysicsObject.Renderer renderer, ByteBuffer customData) {
        if (renderer == null) return;

        poseStack.pushPose();
        RVec3 pos = renderData.transform.getTranslation();
        var rot = renderData.transform.getRotation();

        poseStack.translate(pos.xx(), pos.yy(), pos.zz());
        REUSABLE_QUATERNION.set(rot.getX(), rot.getY(), rot.getZ(), rot.getW());
        poseStack.mulPose(REUSABLE_QUATERNION);

        int packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(pos.xx(), pos.yy(), pos.zz()));
        renderer.render(id, renderData, customData, poseStack, bufferSource, partialTicks, packedLight);

        poseStack.popPose();
    }

    private static void renderSoftBody(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, UUID id, RenderData renderData, SoftPhysicsObject.Renderer renderer, ByteBuffer customData) {
        if (renderer == null) return;

        poseStack.pushPose();

        assert mc.level != null;
        int packedLight;
        if (renderData.vertexData != null && renderData.vertexData.length >= 3) {
            packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(renderData.vertexData[0], renderData.vertexData[1], renderData.vertexData[2]));
        } else {
            RVec3 pos = renderData.transform.getTranslation();
            packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(pos.xx(), pos.yy(), pos.zz()));
        }

        renderer.render(id, renderData, customData, poseStack, bufferSource, partialTicks, packedLight);

        poseStack.popPose();
    }
}