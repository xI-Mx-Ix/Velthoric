package net.xmx.vortex.physics.object.physicsobject.client.renderer;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.xmx.vortex.event.api.VxRenderEvent;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.math.VxOperations;
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

    private static final Quaternionf REUSABLE_QUATERNION = new Quaternionf();
    private static final float CULLING_BOUNDS_INFLATION = 2.0f;
    private static final RenderData FINAL_RENDER_DATA = new RenderData();
    private static final RVec3 RENDER_POSITION = new RVec3();
    private static final Quat RENDER_ROTATION = new Quat();
    private static float[] RENDER_VERTICES = null;

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

        poseStack.pushPose();

        RVec3 prevPos = renderState.previous.transform.getTranslation();
        RVec3 currPos = renderState.current.transform.getTranslation();
        RENDER_POSITION.set(
                (float)Mth.lerp(partialTicks, prevPos.x(), currPos.x()),
                (float)Mth.lerp(partialTicks, prevPos.y(), currPos.y()),
                (float)Mth.lerp(partialTicks, prevPos.z(), currPos.z())
        );

        var prevRot = renderState.previous.transform.getRotation();
        var currRot = renderState.current.transform.getRotation();
        VxOperations.slerp(prevRot, currRot, partialTicks, RENDER_ROTATION);

        poseStack.translate(RENDER_POSITION.x(), RENDER_POSITION.y(), RENDER_POSITION.z());
        REUSABLE_QUATERNION.set(RENDER_ROTATION.getX(), RENDER_ROTATION.getY(), RENDER_ROTATION.getZ(), RENDER_ROTATION.getW());
        poseStack.mulPose(REUSABLE_QUATERNION);

        FINAL_RENDER_DATA.transform.set(RENDER_POSITION, RENDER_ROTATION);
        FINAL_RENDER_DATA.vertexData = null;

        int packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(RENDER_POSITION.x(), RENDER_POSITION.y(), RENDER_POSITION.z()));
        renderer.render(id, FINAL_RENDER_DATA, customData, poseStack, bufferSource, partialTicks, packedLight);

        poseStack.popPose();
    }

    private static void renderSoftBody(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, UUID id, InterpolatedRenderState renderState, VxSoftBody.Renderer renderer, ByteBuffer customData) {
        if (renderer == null) return;

        poseStack.pushPose();

        float[] prevVerts = renderState.previous.vertexData;
        float[] currVerts = renderState.current.vertexData;

        if (prevVerts != null && currVerts != null && prevVerts.length == currVerts.length) {
            if (RENDER_VERTICES == null || RENDER_VERTICES.length != currVerts.length) {
                RENDER_VERTICES = new float[currVerts.length];
            }
            for (int i = 0; i < currVerts.length; i++) {
                RENDER_VERTICES[i] = Mth.lerp(partialTicks, prevVerts[i], currVerts[i]);
            }
            FINAL_RENDER_DATA.vertexData = RENDER_VERTICES;
        } else if (currVerts != null) {
            FINAL_RENDER_DATA.vertexData = currVerts;
        } else {
            FINAL_RENDER_DATA.vertexData = prevVerts;
        }

        if (FINAL_RENDER_DATA.vertexData == null) {
            poseStack.popPose();
            return;
        }

        int packedLight;
        if (FINAL_RENDER_DATA.vertexData.length >= 3) {
            packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(FINAL_RENDER_DATA.vertexData[0], FINAL_RENDER_DATA.vertexData[1], FINAL_RENDER_DATA.vertexData[2]));
        } else {
            RVec3 pos = renderState.current.transform.getTranslation();
            packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(pos.x(), pos.y(), pos.z()));
        }

        renderer.render(id, FINAL_RENDER_DATA, customData, poseStack, bufferSource, partialTicks, packedLight);

        poseStack.popPose();
    }
}