/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.block;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.object.client.body.VxClientRigidBody;
import net.xmx.velthoric.physics.object.sync.VxDataAccessor;
import net.xmx.velthoric.physics.object.sync.VxDataSerializers;
import org.joml.Quaternionf;

import java.util.UUID;

/**
 * @author xI-Mx-Ix
 */
public class BlockClientRigidBody extends VxClientRigidBody {

    private static final VxDataAccessor<Integer> DATA_BLOCK_STATE_ID = VxDataAccessor.create(BlockClientRigidBody.class, VxDataSerializers.INTEGER);

    public BlockClientRigidBody(UUID id, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType) {
        super(id, manager, dataStoreIndex, objectType);
    }

    @Override
    protected void defineSyncData() {
        this.synchronizedData.define(DATA_BLOCK_STATE_ID, Block.getId(Blocks.STONE.defaultBlockState()));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, int packedLight, VxRenderState renderState) {
        int blockStateId = this.getSyncData(DATA_BLOCK_STATE_ID);
        BlockState blockStateToRender = Block.stateById(blockStateId);

        if (blockStateToRender.isAir() || blockStateToRender.getRenderShape() == RenderShape.INVISIBLE) {
            return;
        }

        poseStack.pushPose();

        RVec3 renderPosition = renderState.transform.getTranslation();
        Quat renderRotation = renderState.transform.getRotation();
        poseStack.translate(renderPosition.x(), renderPosition.y(), renderPosition.z());
        poseStack.mulPose(new Quaternionf(renderRotation.getX(), renderRotation.getY(), renderRotation.getZ(), renderRotation.getW()));

        RenderShape shape = blockStateToRender.getRenderShape();
        if (shape == RenderShape.MODEL || shape == RenderShape.ENTITYBLOCK_ANIMATED) {
            BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();

            BlockColors blockColors = Minecraft.getInstance().getBlockColors();
            Level level = Minecraft.getInstance().level;
            BlockPos currentPos = BlockPos.containing(renderPosition.x(), renderPosition.y(), renderPosition.z());

            int color = blockColors.getColor(blockStateToRender, level, currentPos, 0);

            float r = (color >> 16 & 255) / 255.0f;
            float g = (color >> 8 & 255) / 255.0f;
            float b = (color & 255) / 255.0f;

            poseStack.pushPose();
            poseStack.translate(-0.5, -0.5, -0.5);

            dispatcher.getModelRenderer().renderModel(
                    poseStack.last(),
                    bufferSource.getBuffer(ItemBlockRenderTypes.getRenderType(blockStateToRender, false)),
                    blockStateToRender,
                    dispatcher.getBlockModel(blockStateToRender),
                    r, g, b,
                    packedLight,
                    OverlayTexture.NO_OVERLAY
            );
            poseStack.popPose();
        }

        if (blockStateToRender.getBlock() instanceof EntityBlock entityBlock) {
            Minecraft mc = Minecraft.getInstance();
            BlockEntityRenderDispatcher beDispatcher = mc.getBlockEntityRenderDispatcher();
            var blockEntity = entityBlock.newBlockEntity(BlockPos.ZERO, blockStateToRender);

            if (blockEntity != null) {

                blockEntity.setLevel(mc.level);
                var renderer = beDispatcher.getRenderer(blockEntity);
                if (renderer != null) {
                    poseStack.pushPose();
                    poseStack.translate(-0.5, -0.5, -0.5);
                    renderer.render(blockEntity, partialTicks, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
                    poseStack.popPose();
                }
            }
        }

        poseStack.popPose();
    }
}