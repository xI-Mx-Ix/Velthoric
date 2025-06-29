package net.xmx.xbullet.debug.drawer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DebugShapeRenderer {

    private static final List<DebugLine> linesToRender = new CopyOnWriteArrayList<>();

    public static void setLinesToRender(List<DebugLine> lines) {
        linesToRender.clear();
        if (lines != null) {
            linesToRender.addAll(lines);
        }
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource) {
        if (linesToRender.isEmpty()) {
            return;
        }

        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
        Matrix4f poseMatrix = poseStack.last().pose();

        for (DebugLine line : linesToRender) {
            float r = ((line.color >> 16) & 0xFF) / 255.0f;
            float g = ((line.color >> 8) & 0xFF) / 255.0f;
            float b = (line.color & 0xFF) / 255.0f;
            float a = ((line.color >> 24) & 0xFF) / 255.0f;
            if (a == 0.0f) a = 1.0f;

            vertexConsumer.vertex(poseMatrix, (float) line.fromX, (float) line.fromY, (float) line.fromZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
            vertexConsumer.vertex(poseMatrix, (float) line.toX, (float) line.toY, (float) line.toZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        }
    }
}