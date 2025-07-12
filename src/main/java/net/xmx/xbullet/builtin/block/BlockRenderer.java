package net.xmx.xbullet.builtin.block;

import com.mojang.blaze3d.vertex.PoseStack;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.object.physicsobject.type.rigid.RigidPhysicsObject;
import net.xmx.xbullet.physics.object.physicsobject.type.rigid.client.ClientRigidPhysicsObjectData;

@OnlyIn(Dist.CLIENT)
public class BlockRenderer extends RigidPhysicsObject.Renderer {

    @Override
    public void render(ClientRigidPhysicsObjectData data, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight) {
        BlockState blockStateToRender = Blocks.STONE.defaultBlockState();
        byte[] customData = data.getCustomData();

        if (customData != null && customData.length > 0) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(customData));
            try {
                int blockStateId = buf.readVarInt();
                BlockState parsedState = Block.stateById(blockStateId);
                if (!parsedState.isAir()) {
                    blockStateToRender = parsedState;
                }
            } catch (Exception e) {
                XBullet.LOGGER.error("Client: Failed to parse blockState ID for object {}", data.getId(), e);
            }
        }

        poseStack.pushPose();
        poseStack.translate(-0.5, -0.5, -0.5);

        try {
            if (blockStateToRender.getRenderShape() != RenderShape.INVISIBLE) {
                BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
                RenderType renderType = ItemBlockRenderTypes.getRenderType(blockStateToRender, false);
                blockRenderer.renderSingleBlock(blockStateToRender, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, renderType);
            }
        } catch (Exception e) {
            XBullet.LOGGER.error("Error rendering BlockPhysicsObject with ID {} and BlockState {}", data.getId(), blockStateToRender, e);
        }
        poseStack.popPose();
    }
}