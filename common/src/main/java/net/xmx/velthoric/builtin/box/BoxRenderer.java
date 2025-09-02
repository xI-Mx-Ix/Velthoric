package net.xmx.velthoric.builtin.box;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.blaze3d.vertex.PoseStack;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;
import net.xmx.velthoric.physics.object.client.RenderState;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.nio.ByteBuffer;
import java.util.UUID;

public class BoxRenderer implements VxRigidBody.Renderer {

    @Override
    public void render(UUID id, RenderState renderState, @Nullable ByteBuffer customData, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight) {
        if (customData == null || customData.remaining() < 12) {
            return;
        }

        customData.rewind();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(customData));
        float hx = buf.readFloat();
        float hy = buf.readFloat();
        float hz = buf.readFloat();

        float fullWidth = hx * 2.0f;
        float fullHeight = hy * 2.0f;
        float fullDepth = hz * 2.0f;

        poseStack.pushPose();

        RVec3 renderPosition = renderState.transform.getTranslation();
        Quat renderRotation = renderState.transform.getRotation();
        poseStack.translate(renderPosition.x(), renderPosition.y(), renderPosition.z());
        poseStack.mulPose(new Quaternionf(renderRotation.getX(), renderRotation.getY(), renderRotation.getZ(), renderRotation.getW()));

        poseStack.translate(-hx, -hy, -hz);
        poseStack.scale(fullWidth, fullHeight, fullDepth);

        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                Blocks.RED_CONCRETE.defaultBlockState(),
                poseStack,
                bufferSource,
                packedLight,
                OverlayTexture.NO_OVERLAY
        );

        poseStack.popPose();
    }
}