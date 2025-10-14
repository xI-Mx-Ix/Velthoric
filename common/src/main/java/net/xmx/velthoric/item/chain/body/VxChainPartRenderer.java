/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.chain.body;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.xmx.velthoric.physics.body.client.VxRenderState;
import net.xmx.velthoric.physics.body.client.body.renderer.VxRigidBodyRenderer;
import org.joml.Quaternionf;

/**
 * Renders a {@link VxChainPartRigidBody} using Minecraft's chain block model,
 * scaled to the body's specific dimensions.
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

        poseStack.translate(pos.x(), pos.y(), pos.z());
        poseStack.mulPose(new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW()));

        float length = body.getLength();
        float radius = body.getRadius();
        float halfLength = length / 2.0f;

        poseStack.translate(-radius, -halfLength, -radius);
        poseStack.scale(radius * 2, length, radius * 2);

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