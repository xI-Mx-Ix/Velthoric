/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.drivable.motorcycle;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.WheelSettingsWv;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.xmx.velthoric.physics.body.client.VxRenderState;
import net.xmx.velthoric.physics.body.client.body.renderer.VxRigidBodyRenderer;
import net.xmx.velthoric.physics.vehicle.component.VxVehicleWheel;
import org.joml.Quaternionf;

import java.util.List;

/**
 * Renderer for the {@link MotorcycleImpl} implementation.
 *
 * @author xI-Mx-Ix
 */
public class MotorcycleRenderer extends VxRigidBodyRenderer<MotorcycleImpl> {

    private static final BlockState CHASSIS_STATE = Blocks.RED_CONCRETE.defaultBlockState();
    private static final BlockState WHEEL_STATE = Blocks.BLACK_CONCRETE.defaultBlockState();
    
    // Hardcoded dimensions matching the implementation class
    private static final Vec3 CHASSIS_HALF_EXTENTS = new Vec3(0.2f, 0.3f, 0.4f);

    @Override
    public void render(MotorcycleImpl body, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, int packedLight, VxRenderState renderState) {
        poseStack.pushPose();

        // 1. Apply Main Body Transform
        RVec3 renderPosition = renderState.transform.getTranslation();
        Quat renderRotation = renderState.transform.getRotation();
        poseStack.translate(renderPosition.x(), renderPosition.y(), renderPosition.z());
        poseStack.mulPose(new Quaternionf(renderRotation.getX(), renderRotation.getY(), renderRotation.getZ(), renderRotation.getW()));

        // 2. Chassis Rendering
        Vec3 halfExtents = CHASSIS_HALF_EXTENTS;
        poseStack.pushPose();
        poseStack.translate(-halfExtents.getX(), -halfExtents.getY(), -halfExtents.getZ());
        poseStack.scale(halfExtents.getX() * 2f, halfExtents.getY() * 2f, halfExtents.getZ() * 2f);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(CHASSIS_STATE, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        // 3. Wheel Rendering
        List<VxVehicleWheel> wheels = body.getWheels();

        for (VxVehicleWheel wheel : wheels) {
            WheelSettingsWv settings = wheel.getSettings();

            poseStack.pushPose();

            Vec3 attachmentPos = settings.getPosition();
            poseStack.translate(attachmentPos.getX(), attachmentPos.getY(), attachmentPos.getZ());

            float suspLength = wheel.getRenderSuspension(partialTicks);
            Vec3 suspensionDir = settings.getSuspensionDirection();
            poseStack.translate(
                    suspensionDir.getX() * suspLength,
                    suspensionDir.getY() * suspLength,
                    suspensionDir.getZ() * suspLength
            );

            float steerAngle = wheel.getRenderSteer(partialTicks);
            float rotationAngle = wheel.getRenderRotation(partialTicks);

            Vec3 steerAxis = settings.getSteeringAxis();
            poseStack.mulPose(Axis.of(new org.joml.Vector3f(steerAxis.getX(), steerAxis.getY(), steerAxis.getZ())).rotation(steerAngle));
            poseStack.mulPose(Axis.XP.rotation(rotationAngle));

            float radius = settings.getRadius();
            float width = settings.getWidth();
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