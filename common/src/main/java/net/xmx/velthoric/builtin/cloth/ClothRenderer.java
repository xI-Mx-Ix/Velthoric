/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.cloth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.core.body.client.VxRenderState;
import net.xmx.velthoric.core.body.client.renderer.VxSoftBodyRenderer;
import org.joml.Vector3f;

import java.util.function.BiFunction;

/**
 * Renderer for the {@link ClothSoftBody}.
 *
 * @author xI-Mx-Ix
 */
public class ClothRenderer extends VxSoftBodyRenderer<ClothSoftBody> {

    private static final ResourceLocation BLUE_WOOL_TEXTURE = ResourceLocation.tryParse("minecraft:block/blue_wool");

    @Override
    public void render(ClothSoftBody body, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight, VxRenderState renderState) {
        float[] renderVertexData = renderState.vertexData;
        int widthSegments = body.get(ClothSoftBody.DATA_WIDTH_SEGMENTS);
        int heightSegments = body.get(ClothSoftBody.DATA_HEIGHT_SEGMENTS);

        if (renderVertexData == null || renderVertexData.length < 12 || widthSegments <= 0 || heightSegments <= 0) {
            return;
        }

        double comX = renderState.transform.getTranslation().x();
        double comY = renderState.transform.getTranslation().y();
        double comZ = renderState.transform.getTranslation().z();

        int numVerticesX = widthSegments + 1;
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.translucent());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(BLUE_WOOL_TEXTURE);

        float minU = sprite.getU0();
        float maxU = sprite.getU1();
        float minV = sprite.getV0();
        float maxV = sprite.getV1();
        float deltaU = maxU - minU;
        float deltaV = maxV - minV;

        // Function to retrieve a vertex position converted to local space
        BiFunction<Integer, Integer, Vector3f> getVertexLocalPos = (x, y) -> {
            int index = (y * numVerticesX + x) * 3;
            if (index + 2 >= renderVertexData.length) return new Vector3f();
            return new Vector3f(
                    (float)(renderVertexData[index] - comX),
                    (float)(renderVertexData[index + 1] - comY),
                    (float)(renderVertexData[index + 2] - comZ)
            );
        };

        for (int y = 0; y < heightSegments; ++y) {
            for (int x = 0; x < widthSegments; ++x) {
                Vector3f v1 = getVertexLocalPos.apply(x, y);
                Vector3f v2 = getVertexLocalPos.apply(x + 1, y);
                Vector3f v3 = getVertexLocalPos.apply(x + 1, y + 1);
                Vector3f v4 = getVertexLocalPos.apply(x, y + 1);

                float u1 = minU + (deltaU * ((float) x / widthSegments));
                float u2 = minU + (deltaU * ((float) (x + 1) / widthSegments));
                float v1Coord = minV + (deltaV * ((float) y / heightSegments));
                float v2Coord = minV + (deltaV * ((float) (y + 1) / heightSegments));

                Vector3f edge1 = new Vector3f(v2).sub(v1);
                Vector3f edge2 = new Vector3f(v4).sub(v1);
                Vector3f normal = edge1.cross(edge2).normalize();
                if (Float.isNaN(normal.x())) normal.set(0, 1, 0);

                // Front face
                addVertex(buffer, poseStack, v1, u1, v1Coord, normal, packedLight);
                addVertex(buffer, poseStack, v2, u2, v1Coord, normal, packedLight);
                addVertex(buffer, poseStack, v3, u2, v2Coord, normal, packedLight);
                addVertex(buffer, poseStack, v4, u1, v2Coord, normal, packedLight);

                // Back face (inverted normal)
                Vector3f backNormal = new Vector3f(normal).mul(-1.0f);
                addVertex(buffer, poseStack, v1, u1, v1Coord, backNormal, packedLight);
                addVertex(buffer, poseStack, v4, u1, v2Coord, backNormal, packedLight);
                addVertex(buffer, poseStack, v3, u2, v2Coord, backNormal, packedLight);
                addVertex(buffer, poseStack, v2, u2, v1Coord, backNormal, packedLight);
            }
        }
    }

    private void addVertex(VertexConsumer buffer, PoseStack poseStack, Vector3f pos, float u, float v, Vector3f normal, int packedLight) {
        buffer.addVertex(poseStack.last(), pos.x(), pos.y(), pos.z())
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setLight(packedLight)
                .setNormal(poseStack.last(), normal.x(), normal.y(), normal.z());
    }
}