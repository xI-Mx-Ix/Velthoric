/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.item.chaincreator.body;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.timtaran.interactivemc.physics.physics.body.client.VxRenderState;
import net.timtaran.interactivemc.physics.physics.body.client.body.renderer.VxRigidBodyRenderer;
import org.joml.Quaternionf;

/**
 * Renders a {@link VxChainPartRigidBody} using Minecraft's chain block model.
 * <p>
 * This renderer scales the model so that the visible texture matches the
 * physical dimensions of the rigid body, compensating for the empty space
 * in the vanilla chain block model.
 *
 * @author xI-Mx-Ix
 */
public class VxChainPartRenderer extends VxRigidBodyRenderer<VxChainPartRigidBody> {

    private static final BlockState CHAIN_BLOCK_STATE = Blocks.CHAIN.defaultBlockState();

    @Override
    public void render(VxChainPartRigidBody body, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, int packedLight, VxRenderState renderState) {
        poseStack.pushPose();

        RVec3 pos = renderState.transform.getTranslation();
        Quat rot = renderState.transform.getRotation();

        // Apply the physics transformation (Position & Rotation)
        poseStack.translate(pos.x(), pos.y(), pos.z());
        poseStack.mulPose(new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW()));

        float length = body.getLength();
        float radius = body.getRadius();

        // Based on the provided JSON model ("from": 6.5 to "to": 9.5), the chain texture is exactly 3 pixels wide.
        // We want this 3-pixel wide visual plane to match the physics diameter (2 * radius).
        // The block model coordinate system is 0 to 16.
        final float modelWidthPixels = 3.0f;
        final float blockPixels = 16.0f;

        // Calculate the scaling factor for X and Z axes.
        // Formula: (VisualWidth / 16) * Scale = PhysicsDiameter
        // Scale = PhysicsDiameter / (VisualWidth / 16)
        // Scale = (radius * 2.0f) * (16.0f / 3.0f)
        float scaleXZ = (radius * 2.0f) * (blockPixels / modelWidthPixels);

        // The Y-axis (length) maps 1:1 from block units (0-1) to meters.
        float scaleY = length;

        // Center the block model.
        // Minecraft models are defined from 0,0,0 to 1,1,1.
        // The physics body pivot is at the center of mass.
        // We translate by negative half of the scaled dimensions to center the model at the pivot.
        poseStack.translate(-scaleXZ / 2.0f, -scaleY / 2.0f, -scaleXZ / 2.0f);

        // Apply the calculated scales.
        poseStack.scale(scaleXZ, scaleY, scaleXZ);

        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                CHAIN_BLOCK_STATE,
                poseStack,
                bufferSource,
                packedLight,
                OverlayTexture.NO_OVERLAY
        );

        poseStack.popPose();
    }
}