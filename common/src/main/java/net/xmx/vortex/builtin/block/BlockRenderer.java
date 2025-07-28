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
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.object.physicsobject.type.rigid.RigidPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.type.rigid.client.ClientRigidPhysicsObjectData;

@Environment(EnvType.CLIENT)
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
                VxMainClass.LOGGER.error("Client: Failed to parse blockState ID for object {}", data.getId(), e);
            }
        }

        poseStack.pushPose();
        poseStack.translate(-0.5, -0.5, -0.5);

        try {
            RenderShape shape = blockStateToRender.getRenderShape();
            if (shape != RenderShape.INVISIBLE) {
                BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
                switch (shape) {
                    case MODEL -> {
                        var model = dispatcher.getBlockModel(blockStateToRender);
                        BlockColors colors = Minecraft.getInstance().getBlockColors();
                        int i = colors.getColor(blockStateToRender, (BlockAndTintGetter) null, (BlockPos) null, 0);
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
                    case ENTITYBLOCK_ANIMATED -> Minecraft.getInstance().getItemRenderer().renderStatic(
                            new ItemStack(blockStateToRender.getBlock()),
                            ItemDisplayContext.NONE,
                            packedLight,
                            OverlayTexture.NO_OVERLAY,
                            poseStack,
                            bufferSource,
                            null,
                            0
                    );
                }
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Error rendering BlockPhysicsObject with ID {} and BlockState {}", data.getId(), blockStateToRender, e);
        }

        poseStack.popPose();
    }
}
