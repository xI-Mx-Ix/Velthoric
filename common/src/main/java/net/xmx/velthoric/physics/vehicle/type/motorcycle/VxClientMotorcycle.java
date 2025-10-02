/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.type.motorcycle;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.WheelSettingsWv;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.vehicle.VxClientVehicle;
import net.xmx.velthoric.physics.vehicle.wheel.VxWheelRenderState;
import org.joml.Quaternionf;

import java.util.List;
import java.util.UUID;

/**
 * Client-side representation of a motorcycle. Handles rendering of the chassis and wheels.
 *
 * @author xI-Mx-Ix
 */
public class VxClientMotorcycle extends VxClientVehicle {

    private static final BlockState CHASSIS_STATE = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState WHEEL_STATE = Blocks.BLACK_CONCRETE.defaultBlockState();

    public VxClientMotorcycle(UUID id, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType) {
        super(id, manager, dataStoreIndex, objectType);
    }

    @Override
    protected void defineSyncData() {
        super.defineSyncData();
        this.synchronizedData.define(VxMotorcycle.DATA_CHASSIS_HALF_EXTENTS, new Vec3());
    }

    public Vec3 getChassisHalfExtents() {
        return this.getSyncData(VxMotorcycle.DATA_CHASSIS_HALF_EXTENTS);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, int packedLight, VxRenderState renderState) {
        poseStack.pushPose();

        RVec3 renderPosition = renderState.transform.getTranslation();
        Quat renderRotation = renderState.transform.getRotation();
        poseStack.translate(renderPosition.x(), renderPosition.y(), renderPosition.z());
        poseStack.mulPose(new Quaternionf(renderRotation.getX(), renderRotation.getY(), renderRotation.getZ(), renderRotation.getW()));

        // Chassis rendering
        Vec3 halfExtents = this.getChassisHalfExtents();
        poseStack.pushPose();
        poseStack.translate(-halfExtents.getX(), -halfExtents.getY(), -halfExtents.getZ());
        poseStack.scale(halfExtents.getX() * 2f, halfExtents.getY() * 2f, halfExtents.getZ() * 2f);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(CHASSIS_STATE, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        // Wheel rendering
        List<WheelSettingsWv> wheelSettingsList = this.getWheelSettings();
        List<VxWheelRenderState> wheelRenderStates = this.getInterpolatedWheelStates();

        if (wheelRenderStates == null || wheelSettingsList.size() != wheelRenderStates.size()) {
            poseStack.popPose();
            return;
        }

        for (int i = 0; i < wheelSettingsList.size(); i++) {
            WheelSettingsWv wheelSettings = wheelSettingsList.get(i);
            VxWheelRenderState wheelState = wheelRenderStates.get(i);

            if (wheelState == null) continue;

            poseStack.pushPose();

            Vec3 attachmentPos = wheelSettings.getPosition();
            poseStack.translate(attachmentPos.getX(), attachmentPos.getY(), attachmentPos.getZ());

            Vec3 suspensionDir = wheelSettings.getSuspensionDirection();
            poseStack.translate(
                    suspensionDir.getX() * wheelState.suspensionLength(),
                    suspensionDir.getY() * wheelState.suspensionLength(),
                    suspensionDir.getZ() * wheelState.suspensionLength()
            );

            Vec3 steerAxis = wheelSettings.getSteeringAxis();
            poseStack.mulPose(Axis.of(new org.joml.Vector3f(steerAxis.getX(), steerAxis.getY(), steerAxis.getZ())).rotation(wheelState.steerAngle()));
            poseStack.mulPose(Axis.XP.rotation(wheelState.rotationAngle()));

            float radius = wheelSettings.getRadius();
            float width = wheelSettings.getWidth();
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(90));
            poseStack.translate(-radius, -width / 2f, -radius);
            poseStack.scale(radius * 2f, width, radius * 2f);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(WHEEL_STATE, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();

            poseStack.popPose();
        }

        poseStack.popPose();
    }
}