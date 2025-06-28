package net.xmx.xbullet.physics.object.global.physicsobject.client.renderer;

import com.github.stephengold.joltjni.RVec3;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.global.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.global.physicsobject.client.ClientPhysicsObjectData;
import net.xmx.xbullet.physics.object.global.physicsobject.client.ClientPhysicsObjectManager;
import net.xmx.xbullet.physics.object.rigidphysicsobject.client.ClientRigidPhysicsObjectData;
import net.xmx.xbullet.physics.object.softphysicsobject.client.ClientSoftPhysicsObjectData;
import org.joml.Quaternionf;

import java.util.ArrayList;

public class PhysicsObjectRenderer {

    private static final Quaternionf REUSABLE_QUATERNION = new Quaternionf();

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getCameraEntity() == null) return;

        ClientPhysicsObjectManager clientManager = ClientPhysicsObjectManager.getInstance();
        if (clientManager == null) return;

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        float partialTicks = event.getPartialTick();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        for (ClientPhysicsObjectData data : new ArrayList<>(clientManager.getAllObjectData())) {
            try {
                if (data.getObjectType() == EObjectType.RIGID_BODY) {
                    renderRigidBody(mc, poseStack, bufferSource, partialTicks, data);
                } else if (data.getObjectType() == EObjectType.SOFT_BODY) {
                    renderSoftBody(mc, poseStack, bufferSource, partialTicks, data);
                }
            } catch (Exception e) {
                XBullet.LOGGER.error("Error rendering physics object {}", data.getId(), e);
            }
        }

        poseStack.popPose();
        bufferSource.endBatch();
    }

    private static void renderRigidBody(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, ClientPhysicsObjectData data) {
        ClientRigidPhysicsObjectData rigidData = data.getRigidData();
        if (rigidData == null || rigidData.getRenderer() == null) return;

        PhysicsTransform renderTransform = rigidData.getRenderTransform(partialTicks);
        if (renderTransform == null) return;

        poseStack.pushPose();
        RVec3 pos = renderTransform.getTranslation();
        var rot = renderTransform.getRotation();

        poseStack.translate(pos.xx(), pos.yy(), pos.zz());
        REUSABLE_QUATERNION.set(rot.getX(), rot.getY(), rot.getZ(), rot.getW());
        poseStack.mulPose(REUSABLE_QUATERNION);

        int packedLight = LevelRenderer.getLightColor(mc.level, BlockPos.containing(pos.xx(), pos.yy(), pos.zz()));
        rigidData.getRenderer().render(rigidData, poseStack, bufferSource, partialTicks, packedLight);

        poseStack.popPose();
    }

    private static void renderSoftBody(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, ClientPhysicsObjectData data) {
        ClientSoftPhysicsObjectData softData = data.getSoftData();
        if (softData == null || softData.getRenderer() == null) return;

        float[] renderData = softData.getRenderVertexData(partialTicks);
        if (renderData == null || renderData.length < 3) return;

        BlockPos lightPos = BlockPos.containing(renderData[0], renderData[1], renderData[2]);
        int packedLight = LevelRenderer.getLightColor(mc.level, lightPos);

        poseStack.pushPose();
        softData.getRenderer().render(softData, poseStack, bufferSource, partialTicks, packedLight);
        poseStack.popPose();
    }
}