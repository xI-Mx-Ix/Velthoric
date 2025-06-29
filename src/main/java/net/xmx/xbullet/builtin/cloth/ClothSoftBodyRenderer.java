package net.xmx.xbullet.builtin.cloth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.xmx.xbullet.physics.object.softphysicsobject.SoftPhysicsObject;
import net.xmx.xbullet.physics.object.softphysicsobject.client.ClientSoftPhysicsObjectData;
import org.joml.Vector3f;

import java.util.function.BiFunction;

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

        BiFunction<Integer, Integer, Vector3f> getVertexLocalPos = (x, y) -> {
            int index = (y * numVerticesX + x) * 3;
            if (index + 2 >= renderVertexData.length) return new Vector3f();
            return new Vector3f(renderVertexData[index], renderVertexData[index + 1], renderVertexData[index + 2]);
        };

        for (int y = 0; y < heightSegments; ++y) {
            for (int x = 0; x < widthSegments; ++x) {

                Vector3f v1 = getVertexLocalPos.apply(x, y);
                Vector3f v2 = getVertexLocalPos.apply(x + 1, y);
                Vector3f v3 = getVertexLocalPos.apply(x + 1, y + 1);
                Vector3f v4 = getVertexLocalPos.apply(x, y + 1);

                float u1 = sprite.getU((float) x / widthSegments * 16f);
                float u2 = sprite.getU((float) (x + 1) / widthSegments * 16f);
                float v1Coord = sprite.getV((float) y / heightSegments * 16f);
                float v2Coord = sprite.getV((float) (y + 1) / heightSegments * 16f);

                Vector3f normal1 = calculateNormal(v1, v2, v3);
                addVertex(buffer, poseStack, v1, u1, v1Coord, normal1, packedLight);
                addVertex(buffer, poseStack, v2, u2, v1Coord, normal1, packedLight);
                addVertex(buffer, poseStack, v3, u2, v2Coord, normal1, packedLight);

                Vector3f normal2 = calculateNormal(v1, v3, v4);
                addVertex(buffer, poseStack, v1, u1, v1Coord, normal2, packedLight);
                addVertex(buffer, poseStack, v3, u2, v2Coord, normal2, packedLight);
                addVertex(buffer, poseStack, v4, u1, v2Coord, normal2, packedLight);
            }
        }
    }

    private Vector3f calculateNormal(Vector3f v1, Vector3f v2, Vector3f v3) {
        Vector3f edge1 = new Vector3f(v2).sub(v1);
        Vector3f edge2 = new Vector3f(v3).sub(v1);
        Vector3f normal = edge1.cross(edge2).normalize();
        if (Float.isNaN(normal.x()) || normal.length() == 0) {
            normal.set(0, 1, 0);
        }
        return normal;
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