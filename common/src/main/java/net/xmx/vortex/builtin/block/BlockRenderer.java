package net.xmx.vortex.builtin.block;

import com.mojang.blaze3d.vertex.PoseStack;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.object.physicsobject.client.interpolation.RenderState;
import net.xmx.vortex.physics.object.physicsobject.type.rigid.VxRigidBody;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class BlockRenderer implements VxRigidBody.Renderer {

    @Override
    public void render(UUID id, RenderState renderState, @Nullable ByteBuffer customData, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight) {
        BlockState blockStateToRender = Blocks.STONE.defaultBlockState();

        if (customData != null && customData.hasRemaining()) {
            customData.rewind();
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(customData));
            try {
                int blockStateId = buf.readVarInt();
                BlockState parsedState = Block.stateById(blockStateId);
                if (!parsedState.isAir()) {
                    blockStateToRender = parsedState;
                }
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Client: Failed to parse blockState ID for object {}", id, e);
            }
        }

        poseStack.pushPose();
        poseStack.translate(-0.5, -0.5, -0.5);

        try {
            RenderShape shape = blockStateToRender.getRenderShape();
            if (shape != RenderShape.INVISIBLE) {
                BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
                if (shape == RenderShape.MODEL) {
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

                } else if (shape == RenderShape.ENTITYBLOCK_ANIMATED) {
                    Minecraft.getInstance().getItemRenderer().renderStatic(
                            new ItemStack(blockStateToRender.getBlock()),
                            ItemDisplayContext.NONE,
                            packedLight,
                            OverlayTexture.NO_OVERLAY,
                            poseStack,
                            bufferSource,
                            Minecraft.getInstance().level,
                            0
                    );
                }
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Error rendering BlockPhysicsObject with ID {} and BlockState {}", id, blockStateToRender, e);
        }

        poseStack.popPose();
    }
}