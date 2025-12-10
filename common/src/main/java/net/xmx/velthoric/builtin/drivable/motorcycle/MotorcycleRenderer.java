/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.drivable.motorcycle;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.xmx.velthoric.physics.body.client.VxRenderState;
import net.xmx.velthoric.physics.body.client.body.renderer.VxRigidBodyRenderer;
import net.xmx.velthoric.physics.vehicle.part.VxPart;
import org.joml.Quaternionf;

/**
 * Renderer for the {@link MotorcycleImpl} implementation.
 *
 * @author xI-Mx-Ix
 */
public class MotorcycleRenderer extends VxRigidBodyRenderer<MotorcycleImpl> {

    private static final BlockState CHASSIS_STATE = Blocks.RED_CONCRETE.defaultBlockState();

    // Hardcoded dimensions matching the implementation class
    private static final Vec3 CHASSIS_HALF_EXTENTS = new Vec3(0.2f, 0.3f, 0.4f);

    @Override
    public void render(MotorcycleImpl body, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, int packedLight, VxRenderState renderState) {
        poseStack.pushPose();

        // Apply Main Body Transform
        RVec3 renderPosition = renderState.transform.getTranslation();
        Quat renderRotation = renderState.transform.getRotation();
        poseStack.translate(renderPosition.x(), renderPosition.y(), renderPosition.z());
        poseStack.mulPose(new Quaternionf(renderRotation.getX(), renderRotation.getY(), renderRotation.getZ(), renderRotation.getW()));

        // 1. Render Chassis
        Vec3 halfExtents = CHASSIS_HALF_EXTENTS;
        poseStack.pushPose();
        poseStack.translate(-halfExtents.getX(), -halfExtents.getY(), -halfExtents.getZ());
        poseStack.scale(halfExtents.getX() * 2f, halfExtents.getY() * 2f, halfExtents.getZ() * 2f);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(CHASSIS_STATE, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        // 2. Render Parts (Wheels, Seats, etc.) using their own assigned renderers
        for (VxPart part : body.getParts()) {
            part.render(poseStack, bufferSource, partialTicks, packedLight);
        }

        poseStack.popPose();
    }
}