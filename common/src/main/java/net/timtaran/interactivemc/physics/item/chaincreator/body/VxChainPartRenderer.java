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
    // The vanilla chain texture is roughly 6 pixels wide within a 16 pixel block.
    // We scale up the width by this factor to ensure the visible chain fills the physics radius.
    private static final float VISUAL_WIDTH_MULTIPLIER = 16.0f / 5.5f;

    @Override
    public void render(VxChainPartRigidBody body, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, int packedLight, VxRenderState renderState) {
        poseStack.pushPose();

        RVec3 pos = renderState.transform.getTranslation();
        Quat rot = renderState.transform.getRotation();

        // Apply physics transform
        poseStack.translate(pos.x(), pos.y(), pos.z());
        poseStack.mulPose(new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW()));

        float length = body.getLength();
        float radius = body.getRadius();

        // Calculate visual dimensions
        // The Y-axis (length) maps 1:1, but X/Z need multiplication because the block model is mostly air.
        float scaleX = radius * 2.0f * VISUAL_WIDTH_MULTIPLIER;
        float scaleZ = radius * 2.0f * VISUAL_WIDTH_MULTIPLIER;
        float scaleY = length;

        // Center the rendering logic (Minecraft renders blocks from 0,0,0 to 1,1,1)
        poseStack.translate(-scaleX / 2.0f, -scaleY / 2.0f, -scaleZ / 2.0f);
        poseStack.scale(scaleX, scaleY, scaleZ);

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