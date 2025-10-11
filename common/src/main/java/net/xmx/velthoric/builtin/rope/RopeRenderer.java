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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.physics.object.client.VxRenderState;
import net.xmx.velthoric.physics.object.client.body.renderer.VxSoftBodyRenderer;

/**
 * Renderer for the {@link RopeSoftBody}.
 *
 * @author xI-Mx-Ix
 */
public class RopeRenderer extends VxSoftBodyRenderer<RopeSoftBody> {

    private static final ResourceLocation YELLOW_WOOL_BLOCK_TEXTURE = new ResourceLocation("minecraft:block/yellow_wool");
    private static final int SIDES = 12;
    private static final Vec3 JOLT_UNIT_X = new Vec3(1, 0, 0);
    private static final Vec3 JOLT_UNIT_Y = new Vec3(0, 1, 0);

    @Override
    public void render(RopeSoftBody body, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTicks, int packedLight, VxRenderState renderState) {
        float[] renderVertexData = renderState.vertexData;
        if (renderVertexData == null || renderVertexData.length < 6) {
            return;
        }

        float ropeRadius = body.getSyncData(RopeSoftBody.DATA_ROPE_RADIUS);
        if (ropeRadius <= 0) {
            return;
        }

        int numNodes = renderVertexData.length / 3;
        renderSmoothRope(renderVertexData, numNodes, ropeRadius, poseStack, bufferSource, packedLight);
    }

    private void renderSmoothRope(float[] vertexData, int numNodes, float ropeRadius, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.cutoutMipped());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(YELLOW_WOOL_BLOCK_TEXTURE);

        Vec3[][] ringVertices = new Vec3[numNodes][SIDES];
        Vec3[] nodePositions = new Vec3[numNodes];
        Vec3[] directions = new Vec3[numNodes];

        for (int i = 0; i < numNodes; i++) {
            nodePositions[i] = new Vec3(vertexData[i * 3], vertexData[i * 3 + 1], vertexData[i * 3 + 2]);
        }

        for (int i = 0; i < numNodes; i++) {
            Vec3 direction;
            if (i == 0) direction = Op.minus(nodePositions[1], nodePositions[0]);
            else if (i == numNodes - 1) direction = Op.minus(nodePositions[i], nodePositions[i - 1]);
            else direction = Op.plus(Op.minus(nodePositions[i + 1], nodePositions[i]), Op.minus(nodePositions[i], nodePositions[i - 1]));
            if (direction.lengthSq() < 1e-12f) direction.set(0, -1, 0);
            directions[i] = direction.normalized();
        }

        Vec3 lastUp = Math.abs(directions[0].dot(JOLT_UNIT_Y)) > 0.99f ? directions[0].cross(JOLT_UNIT_X).normalized() : Op.star(directions[0].cross(JOLT_UNIT_Y).normalized(), -1f);

        for (int nodeIdx = 0; nodeIdx < numNodes; nodeIdx++) {
            Vec3 direction = directions[nodeIdx];
            if (nodeIdx > 0) lastUp = Op.star(Quat.sFromTo(directions[nodeIdx - 1], direction), lastUp);
            Vec3 right = direction.cross(lastUp).normalized();
            for (int sideIdx = 0; sideIdx < SIDES; sideIdx++) {
                float angle = sideIdx * (float) (2.0 * Math.PI / SIDES);
                Vec3 offset = Op.plus(Op.star(right, (float) Math.cos(angle) * ropeRadius), Op.star(lastUp, (float) Math.sin(angle) * ropeRadius));
                ringVertices[nodeIdx][sideIdx] = Op.plus(nodePositions[nodeIdx], offset);
            }
        }

        for (int nodeIdx = 0; nodeIdx < numNodes - 1; nodeIdx++) {
            for (int sideIdx = 0; sideIdx < SIDES; sideIdx++) {
                int nextSideIdx = (sideIdx + 1) % SIDES;
                Vec3 v00 = ringVertices[nodeIdx][sideIdx];
                Vec3 v01 = ringVertices[nodeIdx][nextSideIdx];
                Vec3 v10 = ringVertices[nodeIdx + 1][sideIdx];
                Vec3 v11 = ringVertices[nodeIdx + 1][nextSideIdx];
                Vec3 normal = Op.minus(v01, v00).cross(Op.minus(v10, v00)).normalized();
                if (normal.lengthSq() < 1e-9f) normal.set(0, 1, 0);
                addQuad(buffer, poseStack, v00, v10, v11, v01, normal, packedLight, sprite);
            }
        }
    }

    private void addQuad(VertexConsumer buffer, PoseStack poseStack, Vec3 v1, Vec3 v2, Vec3 v3, Vec3 v4, Vec3 normal, int packedLight, TextureAtlasSprite sprite) {
        PoseStack.Pose last = poseStack.last();
        buffer.vertex(last.pose(), v1.getX(), v1.getY(), v1.getZ()).color(255, 255, 255, 255).uv(sprite.getU0(), sprite.getV0()).uv2(packedLight).normal(last.normal(), normal.getX(), normal.getY(), normal.getZ()).endVertex();
        buffer.vertex(last.pose(), v2.getX(), v2.getY(), v2.getZ()).color(255, 255, 255, 255).uv(sprite.getU1(), sprite.getV0()).uv2(packedLight).normal(last.normal(), normal.getX(), normal.getY(), normal.getZ()).endVertex();
        buffer.vertex(last.pose(), v3.getX(), v3.getY(), v3.getZ()).color(255, 255, 255, 255).uv(sprite.getU1(), sprite.getV1()).uv2(packedLight).normal(last.normal(), normal.getX(), normal.getY(), normal.getZ()).endVertex();
        buffer.vertex(last.pose(), v4.getX(), v4.getY(), v4.getZ()).color(255, 255, 255, 255).uv(sprite.getU0(), sprite.getV1()).uv2(packedLight).normal(last.normal(), normal.getX(), normal.getY(), normal.getZ()).endVertex();
    }
}