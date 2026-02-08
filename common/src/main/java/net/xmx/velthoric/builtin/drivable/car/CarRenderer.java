/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.drivable.car;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.xmx.velthoric.core.body.client.VxRenderState;
import net.xmx.velthoric.core.body.client.body.renderer.VxRigidBodyRenderer;
import net.xmx.velthoric.core.vehicle.part.VxPart;
import org.joml.Quaternionf;

/**
 * Renderer for the {@link CarImpl} class.
 *
 * @author xI-Mx-Ix
 */
public class CarRenderer extends VxRigidBodyRenderer<CarImpl> {

    private static final BlockState CHASSIS_STATE = Blocks.BLUE_CONCRETE.defaultBlockState();

    // Hardcoded half-extents matching the implementation class
    private static final Vec3 CHASSIS_HALF_EXTENTS = new Vec3(1.1f, 0.5f, 2.4f);

    @Override
    public void render(CarImpl body, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight, VxRenderState renderState) {
        poseStack.pushPose();

        // Apply Main Body Transform
        Quat renderRotation = renderState.transform.getRotation();
        poseStack.mulPose(new Quaternionf(renderRotation.getX(), renderRotation.getY(), renderRotation.getZ(), renderRotation.getW()));

        // 1. Render Chassis
        Vec3 halfExtents = CHASSIS_HALF_EXTENTS;
        poseStack.pushPose();
        poseStack.translate(-halfExtents.getX(), -halfExtents.getY(), -halfExtents.getZ());
        poseStack.scale(halfExtents.getX() * 2f, halfExtents.getY() * 2f, halfExtents.getZ() * 2f);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(CHASSIS_STATE, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        // 2. Render Parts (Wheels, Seats, etc.) using their own assigned renderers
        // We pass the PoseStack which is currently at the vehicle's origin.
        // The parts will translate themselves using their local position.
        for (VxPart part : body.getParts()) {
            part.render(poseStack, bufferSource, partialTicks, packedLight);
        }

        poseStack.popPose();
    }
}