package net.xmx.vortex.builtin.cloth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.xmx.vortex.physics.object.physicsobject.client.interpolation.RenderState;
import net.xmx.vortex.physics.object.physicsobject.type.soft.VxSoftBody;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.function.BiFunction;

@Environment(EnvType.CLIENT)
public class ClothSoftBodyRenderer implements VxSoftBody.Renderer {

    private static final ResourceLocation BLUE_WOOL_TEXTURE = new ResourceLocation("minecraft:block/blue_wool");

    @Override
    public void render(UUID id, RenderState renderState, @Nullable ByteBuffer customData, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight) {
        float[] renderVertexData = renderState.vertexData;
        if (renderVertexData == null || renderVertexData.length < 12) {
            return;
        }

        int widthSegments = 15;
        int heightSegments = 15;

        if (customData != null && customData.remaining() >= 8) {
            customData.rewind();
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(customData));
            try {
                widthSegments = buf.readInt();
                heightSegments = buf.readInt();
            } catch (Exception ignored) {
            }
        }

        if (widthSegments <= 0 || heightSegments <= 0) {
            return;
        }

        int numVerticesX = widthSegments + 1;
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.translucent());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(BLUE_WOOL_TEXTURE);

        BiFunction<Integer, Integer, Vector3f> getVertexWorldPos = (x, y) -> {
            int index = (y * numVerticesX + x) * 3;
            if (index + 2 >= renderVertexData.length) {
                return new Vector3f();
            }
            return new Vector3f(renderVertexData[index], renderVertexData[index + 1], renderVertexData[index + 2]);
        };

        for (int y = 0; y < heightSegments; ++y) {
            for (int x = 0; x < widthSegments; ++x) {
                Vector3f v1 = getVertexWorldPos.apply(x, y);
                Vector3f v2 = getVertexWorldPos.apply(x + 1, y);
                Vector3f v3 = getVertexWorldPos.apply(x + 1, y + 1);
                Vector3f v4 = getVertexWorldPos.apply(x, y + 1);

                float u1 = sprite.getU((float) x / widthSegments * 16f);
                float u2 = sprite.getU((float) (x + 1) / widthSegments * 16f);
                float v1Coord = sprite.getV((float) y / heightSegments * 16f);
                float v2Coord = sprite.getV((float) (y + 1) / heightSegments * 16f);

                Vector3f edge1 = new Vector3f(v2).sub(v1);
                Vector3f edge2 = new Vector3f(v4).sub(v1);
                Vector3f normal = edge1.cross(edge2).normalize();
                if (Float.isNaN(normal.x())) {
                    normal.set(0, 1, 0);
                }

                addVertex(buffer, poseStack, v1, u1, v1Coord, normal, packedLight);
                addVertex(buffer, poseStack, v2, u2, v1Coord, normal, packedLight);
                addVertex(buffer, poseStack, v3, u2, v2Coord, normal, packedLight);
                addVertex(buffer, poseStack, v4, u1, v2Coord, normal, packedLight);

                Vector3f backNormal = new Vector3f(normal).mul(-1.0f);
                addVertex(buffer, poseStack, v1, u1, v1Coord, backNormal, packedLight);
                addVertex(buffer, poseStack, v4, u1, v2Coord, backNormal, packedLight);
                addVertex(buffer, poseStack, v3, u2, v2Coord, backNormal, packedLight);
                addVertex(buffer, poseStack, v2, u2, v1Coord, backNormal, packedLight);
            }
        }
    }

    private void addVertex(VertexConsumer buffer, PoseStack poseStack, Vector3f pos, float u, float v, Vector3f normal, int packedLight) {
        PoseStack.Pose last = poseStack.last();
        buffer.vertex(last.pose(), pos.x(), pos.y(), pos.z())
                .color(255, 255, 255, 255)
                .uv(u, v)
                .uv2(packedLight)
                .normal(last.normal(), normal.x(), normal.y(), normal.z())
                .endVertex();
    }
}