package net.xmx.velthoric.builtin.box;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.nio.ByteBuffer;
import java.util.UUID;

public class BoxRenderer implements VxRigidBody.Renderer {

    private final RandomSource random = RandomSource.create();

    @Override
    public void render(UUID id, VxRenderState renderState, @Nullable ByteBuffer customData, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight) {
        if (customData == null || customData.remaining() < 16) {
            return;
        }

        customData.rewind();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(customData));
        float hx = buf.readFloat();
        float hy = buf.readFloat();
        float hz = buf.readFloat();
        int color = buf.readInt();

        float red = ((color >> 16) & 0xFF) / 255.0f;
        float green = ((color >> 8) & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;

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

        BlockState blockState = Blocks.WHITE_CONCRETE.defaultBlockState();
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(blockState);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.solid());
        PoseStack.Pose pose = poseStack.last();

        long seed = 42L;
        random.setSeed(seed);

        for (Direction direction : Direction.values()) {
            for (var quad : model.getQuads(blockState, direction, random)) {
                consumer.putBulkData(pose, quad, red, green, blue, packedLight, OverlayTexture.NO_OVERLAY);
            }
        }

        random.setSeed(seed);
        for (var quad : model.getQuads(blockState, null, random)) {
            consumer.putBulkData(pose, quad, red, green, blue, packedLight, OverlayTexture.NO_OVERLAY);
        }

        poseStack.popPose();
    }
}