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
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.object.client.body.VxClientRigidBody;
import org.joml.Quaternionf;

import java.util.UUID;

public class BlockClientRigidBody extends VxClientRigidBody {

    private BlockState representedBlockState = Blocks.AIR.defaultBlockState();

    public BlockClientRigidBody(UUID id, VxClientObjectManager manager, int dataStoreIndex, EBodyType objectType) {
        super(id, manager, dataStoreIndex, objectType);
    }

    @Override
    public void readSyncData(VxByteBuf buf) {
        try {
            int blockStateId = buf.readVarInt();
            BlockState parsedState = Block.stateById(blockStateId);
            this.representedBlockState = parsedState;
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Client: Failed to parse blockState ID for object {}", this.id, e);
            this.representedBlockState = Blocks.STONE.defaultBlockState();
        }
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, int packedLight, VxRenderState renderState) {
        BlockState blockStateToRender = this.representedBlockState;
        if (blockStateToRender.isAir()) {
            return;
        }

        poseStack.pushPose();

        RVec3 renderPosition = renderState.transform.getTranslation();
        Quat renderRotation = renderState.transform.getRotation();
        poseStack.translate(renderPosition.x(), renderPosition.y(), renderPosition.z());
        poseStack.mulPose(new Quaternionf(renderRotation.getX(), renderRotation.getY(), renderRotation.getZ(), renderRotation.getW()));

        poseStack.translate(-0.5, -0.5, -0.5);

        try {
            RenderShape shape = blockStateToRender.getRenderShape();
            if (shape == RenderShape.MODEL) {
                BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
                var model = dispatcher.getBlockModel(blockStateToRender);
                BlockColors colors = Minecraft.getInstance().getBlockColors();
                int i = colors.getColor(blockStateToRender, null, null, 0);
                float r = (i >> 16 & 255) / 255.0f;
                float g = (i >> 8 & 255) / 255.0f;
                float b = (i & 255) / 255.0f;

                dispatcher.getModelRenderer().renderModel(
                        poseStack.last(),
                        bufferSource.getBuffer(ItemBlockRenderTypes.getRenderType(blockStateToRender, false)),
                        blockStateToRender,
                        model,
                        r, g, b,
                        packedLight,
                        OverlayTexture.NO_OVERLAY
                );
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Error rendering BlockPhysicsObject with ID {} and BlockState {}", id, blockStateToRender, e);
        }

        poseStack.popPose();
    }
}