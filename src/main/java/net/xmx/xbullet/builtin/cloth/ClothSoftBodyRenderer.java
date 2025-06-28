package net.xmx.xbullet.builtin.cloth;

import com.github.stephengold.joltjni.operator.Op;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.github.stephengold.joltjni.Vec3;
import net.xmx.xbullet.physics.object.softphysicsobject.SoftPhysicsObject;
import net.xmx.xbullet.physics.object.softphysicsobject.client.ClientSoftPhysicsObjectData;

@OnlyIn(Dist.CLIENT)
public class ClothSoftBodyRenderer extends SoftPhysicsObject.Renderer {

    private static final ResourceLocation BLUE_WOOL_TEXTURE = ResourceLocation.parse("minecraft:block/blue_wool");

    @Override
    public void render(ClientSoftPhysicsObjectData data, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight) {
        float[] renderVertexData = data.getRenderVertexData(partialTicks);
        if (renderVertexData == null || renderVertexData.length < 12) { 
            return;
        }

        int widthSegments = data.getSyncedNbtData().getInt("widthSegments");
        int heightSegments = data.getSyncedNbtData().getInt("heightSegments");
        if (widthSegments <= 0 || heightSegments <= 0) return;

        int numVerticesX = widthSegments + 1;

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.solid()); 
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(BLUE_WOOL_TEXTURE);

        java.util.function.BiFunction<Integer, Integer, Vec3> getVertex = (x, y) -> {
            int index = (y * numVerticesX + x) * 3;
            if (index + 2 >= renderVertexData.length) return new Vec3(); 
            return new Vec3(renderVertexData[index], renderVertexData[index + 1], renderVertexData[index + 2]);
        };

        poseStack.pushPose();
        for (int y = 0; y < heightSegments; ++y) {
            for (int x = 0; x < widthSegments; ++x) {
                Vec3 v1 = getVertex.apply(x, y);         
                Vec3 v2 = getVertex.apply(x + 1, y);     
                Vec3 v3 = getVertex.apply(x + 1, y + 1); 
                Vec3 v4 = getVertex.apply(x, y + 1);     

                float u1 = sprite.getU((float) x / widthSegments * 16f);
                float u2 = sprite.getU((float) (x + 1) / widthSegments * 16f);
                float v1Coord = sprite.getV((float) y / heightSegments * 16f);
                float v2Coord = sprite.getV((float) (y + 1) / heightSegments * 16f);

                Vec3 edge1 = Op.minus(v2, v1);
                Vec3 edge2 = Op.minus(v4, v1);
                Vec3 normal = edge1.cross(edge2).normalized();

                addVertex(buffer, poseStack, v1, u1, v1Coord, normal, packedLight);
                addVertex(buffer, poseStack, v2, u2, v1Coord, normal, packedLight);
                addVertex(buffer, poseStack, v3, u2, v2Coord, normal, packedLight);
                addVertex(buffer, poseStack, v4, u1, v2Coord, normal, packedLight);
            }
        }
        poseStack.popPose();
    }

    private void addVertex(VertexConsumer buffer, PoseStack poseStack, Vec3 pos, float u, float v, Vec3 normal, int packedLight) {
        PoseStack.Pose last = poseStack.last();
        buffer.vertex(last.pose(), pos.getX(), pos.getY(), pos.getZ())
                .color(255, 255, 255, 255)
                .uv(u, v)
                .uv2(packedLight)
                .normal(last.normal(), normal.getX(), normal.getY(), normal.getZ())
                .endVertex();
    }
}