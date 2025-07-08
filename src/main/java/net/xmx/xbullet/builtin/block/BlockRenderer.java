package net.xmx.xbullet.builtin.block;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
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
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();

        BlockState blockStateToRender = Blocks.STONE.defaultBlockState();
        CompoundTag syncedNbt = data.getSyncedNbtData();

        if (syncedNbt.contains("blockState", CompoundTag.TAG_COMPOUND)) {
            try {
                BlockState parsedState = NbtUtils.readBlockState(mc.level.holderLookup(Registries.BLOCK), syncedNbt.getCompound("blockState"));
                if (!parsedState.isAir()) {
                    blockStateToRender = parsedState;
                }
            } catch (Exception e) {
                XBullet.LOGGER.error("Client: Failed to parse blockState for object {}", data.getId(), e);
            }
        }

        poseStack.pushPose();
        poseStack.translate(-0.5, -0.5, -0.5);

        try {
            if (blockStateToRender.getRenderShape() != RenderShape.INVISIBLE) {
                RenderType renderType = ItemBlockRenderTypes.getRenderType(blockStateToRender, false);
                blockRenderer.renderSingleBlock(blockStateToRender, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, renderType);
            }
        } catch (Exception e) {
            XBullet.LOGGER.error("Error rendering BlockPhysicsObject with ID {} and BlockState {}", data.getId(), blockStateToRender, e);
        }
        poseStack.popPose();
    }
}