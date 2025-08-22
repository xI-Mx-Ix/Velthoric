package net.xmx.velthoric.builtin.sphere;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.netty.buffer.Unpooled;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.physics.object.client.interpolation.RenderState;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.util.UUID;

public class SphereRenderer implements VxRigidBody.Renderer {

    private static final int STACKS = 16;
    private static final int SECTORS = 32;

    @Override
    public void render(UUID id, RenderState renderState, @Nullable ByteBuffer customData, PoseStack poseStack, MultiBufferSource bufferSource, float partialTicks, int packedLight) {
        float radius = 0.5f;

        if (customData != null && customData.remaining() >= 4) {
            customData.rewind();
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(customData));
            try {
                radius = buf.readFloat();
            } catch (Exception ignored) {
            }
        }

        PoseStack.Pose lastPose = poseStack.last();
        Matrix4f poseMatrix = lastPose.pose();
        Matrix3f normalMatrix = lastPose.normal();

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.solid());

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
    }

    private Vector3f getSphereVertex(float radius, float phi, float theta) {
        float x = (float) (radius * Math.sin(phi) * Math.cos(theta));
        float y = (float) (radius * Math.cos(phi));
        float z = (float) (radius * Math.sin(phi) * Math.sin(theta));
        return new Vector3f(x, y, z);
    }

    private void addVertex(VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix, Vector3f pos, int r, int g, int b, int a, int packedLight) {
        Vector3f normal = new Vector3f(pos).normalize();
        consumer.vertex(poseMatrix, pos.x, pos.y, pos.z)
                .color(r, g, b, a)
                .uv(0, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(normalMatrix, normal.x, normal.y, normal.z)
                .endVertex();
    }
}