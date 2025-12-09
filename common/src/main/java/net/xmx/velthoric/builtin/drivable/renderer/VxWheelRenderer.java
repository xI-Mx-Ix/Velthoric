/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.drivable.renderer;

import com.github.stephengold.joltjni.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.xmx.velthoric.physics.vehicle.part.VxPartRenderer;
import net.xmx.velthoric.physics.vehicle.part.impl.VxVehicleWheel;
import org.joml.Vector3f;

/**
 * Handles the rendering of vehicle wheels, including suspension compression,
 * steering rotation, and rolling rotation.
 *
 * @author xI-Mx-Ix
 */
@Environment(EnvType.CLIENT)
public class VxWheelRenderer extends VxPartRenderer<VxVehicleWheel> {

    // Placeholder block state for debug rendering if no model is present
    private static final BlockState DEBUG_WHEEL_STATE = Blocks.BLACK_CONCRETE.defaultBlockState();

    @Override
    public void render(VxVehicleWheel part, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, int packedLight) {
        poseStack.pushPose();

        // 1. Move to the static attachment point (defined in local part position)
        Vector3f localPos = part.getLocalPosition();
        poseStack.translate(localPos.x, localPos.y, localPos.z);

        // 2. Apply Suspension Interpolation
        float currentSusp = Mth.lerp(partialTicks, part.getPrevSuspension(), part.getTargetSuspension());
        Vec3 suspensionDir = part.getSettings().getSuspensionDirection();
        poseStack.translate(
                suspensionDir.getX() * currentSusp,
                suspensionDir.getY() * currentSusp,
                suspensionDir.getZ() * currentSusp
        );

        // 3. Apply Steering Interpolation
        float currentSteer = Mth.lerp(partialTicks, part.getPrevSteer(), part.getTargetSteer());
        Vec3 steerAxis = part.getSettings().getSteeringAxis();
        poseStack.mulPose(Axis.of(new Vector3f(steerAxis.getX(), steerAxis.getY(), steerAxis.getZ())).rotation(currentSteer));

        // 4. Apply Rolling Rotation Interpolation
        float currentRot = Mth.lerp(partialTicks, part.getPrevRotation(), part.getTargetRotation());
        poseStack.mulPose(Axis.XP.rotation(currentRot));

        // 5. Render the Wheel Model
        // This example uses a generic block render. Real implementations would render a GLTF/Item model here.
        float radius = part.getSettings().getRadius();
        float width = part.getSettings().getWidth();

        poseStack.pushPose();
        // Rotate block to align with wheel axis
        poseStack.mulPose(Axis.ZP.rotationDegrees(90));
        poseStack.translate(-radius, -width / 2f, -radius);
        poseStack.scale(radius * 2f, width, radius * 2f);

        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(DEBUG_WHEEL_STATE, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        poseStack.popPose();
    }
}