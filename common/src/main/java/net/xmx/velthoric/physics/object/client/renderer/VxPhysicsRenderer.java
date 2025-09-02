package net.xmx.velthoric.physics.object.client.renderer;

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
import net.xmx.velthoric.physics.object.client.RenderState;
import net.xmx.velthoric.physics.object.client.VxClientObjectInterpolator;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.VxClientObjectStore;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.UUID;

public class VxPhysicsRenderer {

    private static final float CULLING_BOUNDS_INFLATION = 2.0f;
    private static final RenderState finalRenderState = new RenderState();
    private static final RVec3 interpolatedPosition = new RVec3();
    private static final Quat interpolatedRotation = new Quat();

    public static void registerEvents() {
        VxRenderEvent.ClientRenderLevelStageEvent.EVENT.register(VxPhysicsRenderer::onRenderLevelStage);
    }

    private static void onRenderLevelStage(VxRenderEvent.ClientRenderLevelStageEvent event) {
        if (event.getStage() != VxRenderEvent.ClientRenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getCameraEntity() == null) return;

        VxClientObjectManager manager = VxClientObjectManager.getInstance();
        VxClientObjectStore store = manager.getStore();
        VxClientObjectInterpolator interpolator = manager.getInterpolator();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        float partialTicks = event.getPartialTick();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        Matrix4f projectionMatrix = event.getProjectionMatrix();

        Frustum frustum = new Frustum(poseStack.last().pose(), projectionMatrix);
        frustum.prepare(cameraPos.x, cameraPos.y, cameraPos.z);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        for (UUID id : store.getAllObjectIds()) {
            Integer index = store.getIndexForId(id);
            if (index == null) continue;

            try {
                RVec3 lastPos = store.lastKnownPosition[index];
                if (lastPos.lengthSq() < 1e-6) continue;

                AABB objectAABB = new AABB(
                        lastPos.xx() - CULLING_BOUNDS_INFLATION, lastPos.yy() - CULLING_BOUNDS_INFLATION, lastPos.zz() - CULLING_BOUNDS_INFLATION,
                        lastPos.xx() + CULLING_BOUNDS_INFLATION, lastPos.yy() + CULLING_BOUNDS_INFLATION, lastPos.zz() + CULLING_BOUNDS_INFLATION
                );

                if (!frustum.isVisible(objectAABB) || !store.render_isInitialized[index]) {
                    continue;
                }

                interpolator.interpolateFrame(store, index, partialTicks, interpolatedPosition, interpolatedRotation);
                finalRenderState.transform.getTranslation().set(interpolatedPosition);
                finalRenderState.transform.getRotation().set(interpolatedRotation);

                EBodyType objectType = store.objectType[index];
                if (objectType == EBodyType.RigidBody) {
                    renderRigidBody(mc, poseStack, bufferSource, partialTicks, id, index, store);
                } else if (objectType == EBodyType.SoftBody) {
                    finalRenderState.vertexData = interpolator.getInterpolatedVertexData(store, index, partialTicks);
                    renderSoftBody(mc, poseStack, bufferSource, partialTicks, id, index, store);
                }
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Error rendering physics object {}", id, e);
            }
        }

        poseStack.popPose();
        bufferSource.endBatch();
    }

    private static void renderRigidBody(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, UUID id, int index, VxClientObjectStore store) {
        VxRigidBody.Renderer renderer = (VxRigidBody.Renderer) store.renderer[index];
        if (renderer == null) return;

        RVec3 renderPosition = finalRenderState.transform.getTranslation();
        int packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(renderPosition.xx(), renderPosition.yy(), renderPosition.zz()));
        ByteBuffer customData = store.customData[index];

        renderer.render(id, finalRenderState, customData, poseStack, bufferSource, partialTicks, packedLight);
    }

    private static void renderSoftBody(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, UUID id, int index, VxClientObjectStore store) {
        VxSoftBody.Renderer renderer = (VxSoftBody.Renderer) store.renderer[index];
        if (renderer == null) return;

        if (finalRenderState.vertexData == null || finalRenderState.vertexData.length < 3) return;

        int packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(finalRenderState.vertexData[0], finalRenderState.vertexData[1], finalRenderState.vertexData[2]));
        ByteBuffer customData = store.customData[index];

        renderer.render(id, finalRenderState, customData, poseStack, bufferSource, partialTicks, packedLight);
    }
}