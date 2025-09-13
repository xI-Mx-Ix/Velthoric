/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.rope;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.operator.Op;
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
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.object.type.VxSoftBody;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class RopeSoftBodyRenderer implements VxSoftBody.Renderer {

    private static final ResourceLocation YELLOW_WOOL_BLOCK_TEXTURE = new ResourceLocation("minecraft:block/yellow_wool");
    private static final int SIDES = 12;
    private static final Vec3 JOLT_UNIT_X = new Vec3(1, 0, 0);
    private static final Vec3 JOLT_UNIT_Y = new Vec3(0, 1, 0);

    @Override
    public void render(UUID id, VxRenderState renderState, @Nullable ByteBuffer customData, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight) {
        float[] renderVertexData = renderState.vertexData;
        if (renderVertexData == null || renderVertexData.length < 6) {
            return;
        }

        float ropeRadius = 0.1f;
        if (customData != null && customData.remaining() >= 20) {
            customData.rewind();
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(customData));
            try {
                buf.readFloat(); // ropeLength
                buf.readInt();   // segments
                ropeRadius = buf.readFloat();
            } catch (Exception ignored) {
            }
        }

        if (ropeRadius <= 0) {
            ropeRadius = 0.1f;
        }

        int numNodes = renderVertexData.length / 3;
        renderSmoothRope(renderVertexData, numNodes, ropeRadius, poseStack, bufferSource, packedLight);
    }

    private void renderSmoothRope(float[] vertexData, int numNodes, float ropeRadius, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.cutoutMipped());
        TextureAtlasSprite yellowWoolSprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(YELLOW_WOOL_BLOCK_TEXTURE);

        float uMin = yellowWoolSprite.getU0();
        float uMax = yellowWoolSprite.getU1();
        float vMin = yellowWoolSprite.getV0();
        float vMax = yellowWoolSprite.getV1();

        Vec3[][] ringVertices = new Vec3[numNodes][SIDES];
        Vec3[] nodePositions = new Vec3[numNodes];
        Vec3[] directions = new Vec3[numNodes];

        for (int i = 0; i < numNodes; i++) {
            nodePositions[i] = new Vec3(vertexData[i * 3], vertexData[i * 3 + 1], vertexData[i * 3 + 2]);
        }

        for (int i = 0; i < numNodes; i++) {
            Vec3 direction;
            if (i == 0) {
                direction = Op.minus(nodePositions[1], nodePositions[0]);
            } else if (i == numNodes - 1) {
                direction = Op.minus(nodePositions[i], nodePositions[i - 1]);
            } else {
                Vec3 dirToNext = Op.minus(nodePositions[i + 1], nodePositions[i]);
                Vec3 dirFromPrev = Op.minus(nodePositions[i], nodePositions[i - 1]);
                direction = Op.plus(dirToNext, dirFromPrev);
            }
            if (direction.lengthSq() < 1e-12f) {
                direction.set(0, -1, 0);
            }
            directions[i] = direction.normalized();
        }

        Vec3 lastUp;
        Vec3 firstDir = directions[0];
        if (Math.abs(firstDir.dot(JOLT_UNIT_Y)) > 0.99f) {
            lastUp = firstDir.cross(JOLT_UNIT_X).normalized();
        } else {
            lastUp = Op.star(firstDir.cross(JOLT_UNIT_Y).normalized(), -1f);
        }

        for (int nodeIdx = 0; nodeIdx < numNodes; nodeIdx++) {
            Vec3 position = nodePositions[nodeIdx];
            Vec3 direction = directions[nodeIdx];
            if (nodeIdx > 0) {
                Vec3 prevDirection = directions[nodeIdx - 1];
                Quat rotation = Quat.sFromTo(prevDirection, direction);
                lastUp = Op.star(rotation, lastUp);
            }
            Vec3 right = direction.cross(lastUp).normalized();
            float angleStep = (float) (2.0 * Math.PI / SIDES);
            for (int sideIdx = 0; sideIdx < SIDES; sideIdx++) {
                float angle = sideIdx * angleStep;
                float cosA = (float) Math.cos(angle);
                float sinA = (float) Math.sin(angle);
                Vec3 offsetRight = Op.star(right, cosA * ropeRadius);
                Vec3 offsetUp = Op.star(lastUp, sinA * ropeRadius);
                ringVertices[nodeIdx][sideIdx] = Op.plus(position, Op.plus(offsetRight, offsetUp));
            }
        }

        for (int nodeIdx = 0; nodeIdx < numNodes - 1; nodeIdx++) {
            for (int sideIdx = 0; sideIdx < SIDES; sideIdx++) {
                int nextSideIdx = (sideIdx + 1) % SIDES;
                Vec3 v00 = ringVertices[nodeIdx][sideIdx];
                Vec3 v01 = ringVertices[nodeIdx][nextSideIdx];
                Vec3 v10 = ringVertices[nodeIdx + 1][sideIdx];
                Vec3 v11 = ringVertices[nodeIdx + 1][nextSideIdx];
                Vec3 edge1 = Op.minus(v01, v00);
                Vec3 edge2 = Op.minus(v10, v00);
                Vec3 normal = edge1.cross(edge2).normalized();
                if (normal.lengthSq() < 1e-9f) {
                    normal.set(0, 1, 0);
                }
                float texV0 = (float) sideIdx / SIDES;
                float texV1 = (float) nextSideIdx / SIDES;
                float u0 = uMin + (vMax-vMin) * ((float)nodeIdx / numNodes);
                float u1 = uMin + (vMax-vMin) * ((float)(nodeIdx+1) / numNodes);
                addQuad(buffer, poseStack, v00, v10, v11, v01, normal, u0, texV0, u1, texV0, u1, texV1, u0, texV1, packedLight, yellowWoolSprite);
            }
        }
    }

    private void addQuad(VertexConsumer buffer, PoseStack poseStack, Vec3 v1, Vec3 v2, Vec3 v3, Vec3 v4, Vec3 normal,
                         float u1, float v1Coord, float u2, float v2Coord, float u3, float v3Coord, float u4, float v4Coord,
                         int packedLight, TextureAtlasSprite sprite) {
        PoseStack.Pose last = poseStack.last();
        buffer.vertex(last.pose(), v1.getX(), v1.getY(), v1.getZ()).color(255, 255, 255, 255).uv(sprite.getU(u1*16), sprite.getV(v1Coord*16)).uv2(packedLight).normal(last.normal(), normal.getX(), normal.getY(), normal.getZ()).endVertex();
        buffer.vertex(last.pose(), v2.getX(), v2.getY(), v2.getZ()).color(255, 255, 255, 255).uv(sprite.getU(u2*16), sprite.getV(v2Coord*16)).uv2(packedLight).normal(last.normal(), normal.getX(), normal.getY(), normal.getZ()).endVertex();
        buffer.vertex(last.pose(), v3.getX(), v3.getY(), v3.getZ()).color(255, 255, 255, 255).uv(sprite.getU(u3*16), sprite.getV(v3Coord*16)).uv2(packedLight).normal(last.normal(), normal.getX(), normal.getY(), normal.getZ()).endVertex();
        buffer.vertex(last.pose(), v4.getX(), v4.getY(), v4.getZ()).color(255, 255, 255, 255).uv(sprite.getU(u4*16), sprite.getV(v4Coord*16)).uv2(packedLight).normal(last.normal(), normal.getX(), normal.getY(), normal.getZ()).endVertex();
    }
}
