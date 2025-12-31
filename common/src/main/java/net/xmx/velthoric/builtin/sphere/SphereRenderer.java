/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.sphere;

import com.github.stephengold.joltjni.Quat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.xmx.velthoric.physics.body.client.VxRenderState;
import net.xmx.velthoric.physics.body.client.body.renderer.VxRigidBodyRenderer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renderer for the {@link SphereRigidBody}.
 *
 * @author xI-Mx-Ix
 */
public class SphereRenderer extends VxRigidBodyRenderer<SphereRigidBody> {

    private static final int STACKS = 16;
    private static final int SECTORS = 32;

    @Override
    public void render(SphereRigidBody body, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight, VxRenderState renderState) {
        poseStack.pushPose();

        Quat renderRotation = renderState.transform.getRotation();
        poseStack.mulPose(new Quaternionf(renderRotation.getX(), renderRotation.getY(), renderRotation.getZ(), renderRotation.getW()));

        PoseStack.Pose lastPose = poseStack.last();
        Matrix4f poseMatrix = lastPose.pose();
        Matrix3f normalMatrix = lastPose.normal();

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.solid());
        float radius = body.get(SphereRigidBody.DATA_RADIUS);
        int r = 200, g = 50, b = 50, a = 255;

        for (int i = 0; i < STACKS; ++i) {
            float phi1 = (float) (i * Math.PI / STACKS);
            float phi2 = (float) ((i + 1) * Math.PI / STACKS);
            for (int j = 0; j < SECTORS; ++j) {
                float theta1 = (float) (j * 2 * Math.PI / SECTORS);
                float theta2 = (float) ((j + 1) * 2 * Math.PI / SECTORS);

                Vector3f v1 = getSphereVertex(radius, phi1, theta1);
                Vector3f v2 = getSphereVertex(radius, phi1, theta2);
                Vector3f v3 = getSphereVertex(radius, phi2, theta2);
                Vector3f v4 = getSphereVertex(radius, phi2, theta1);

                addVertex(consumer, poseMatrix, normalMatrix, v1, r, g, b, a, packedLight);
                addVertex(consumer, poseMatrix, normalMatrix, v2, r, g, b, a, packedLight);
                addVertex(consumer, poseMatrix, normalMatrix, v4, r, g, b, a, packedLight);

                addVertex(consumer, poseMatrix, normalMatrix, v2, r, g, b, a, packedLight);
                addVertex(consumer, poseMatrix, normalMatrix, v3, r, g, b, a, packedLight);
                addVertex(consumer, poseMatrix, normalMatrix, v4, r, g, b, a, packedLight);
            }
        }
        poseStack.popPose();
    }

    private Vector3f getSphereVertex(float radius, float phi, float theta) {
        float x = (float) (radius * Math.sin(phi) * Math.cos(theta));
        float y = (float) (radius * Math.cos(phi));
        float z = (float) (radius * Math.sin(phi) * Math.sin(theta));
        return new Vector3f(x, y, z);
    }

    private void addVertex(VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix, Vector3f pos, int r, int g, int b, int a, int packedLight) {
        Vector3f normal = new Vector3f(pos).normalize();
        normal.mul(normalMatrix);

        consumer.addVertex(poseMatrix, pos.x, pos.y, pos.z)
                .setColor(r, g, b, a)
                .setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(normal.x, normal.y, normal.z);
    }
}