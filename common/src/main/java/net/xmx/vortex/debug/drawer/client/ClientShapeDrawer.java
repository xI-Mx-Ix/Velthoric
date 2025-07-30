package net.xmx.vortex.debug.drawer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import net.xmx.vortex.debug.drawer.packet.DebugShapesUpdatePacket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientShapeDrawer {

    private static final ClientShapeDrawer INSTANCE = new ClientShapeDrawer();
    public static boolean ENABLED = true;

    private final Map<Integer, float[]> lastKnownVertices = new ConcurrentHashMap<>();

    private ClientShapeDrawer() {}

    public static ClientShapeDrawer getInstance() {
        return INSTANCE;
    }

    public void onPacketReceived(DebugShapesUpdatePacket packet) {
        if (!ENABLED) {
            lastKnownVertices.clear();
            return;
        }

        if (packet.isFirstBatch()) {
            lastKnownVertices.clear();
        }

        packet.getDrawData().forEach((bodyId, data) -> {
            lastKnownVertices.put(bodyId, data.vertices());
        });
    }

    public void render(PoseStack poseStack, VertexConsumer vertexConsumer, Vec3 cameraPos) {
        if (!ENABLED || lastKnownVertices.isEmpty()) {
            return;
        }

        poseStack.pushPose();

        for (float[] vertices : lastKnownVertices.values()) {
            drawVertices(poseStack, vertexConsumer, vertices, 0xFFFFFFFF, cameraPos);
        }

        poseStack.popPose();
    }

    private void drawVertices(PoseStack poseStack, VertexConsumer vertexConsumer, float[] vertices, int color, Vec3 cameraPos) {
        if (vertices == null) return;
        for (int i = 0; i < vertices.length; i += 9) {
            drawWireTriangle(
                    poseStack, vertexConsumer,
                    vertices[i], vertices[i+1], vertices[i+2],
                    vertices[i+3], vertices[i+4], vertices[i+5],
                    vertices[i+6], vertices[i+7], vertices[i+8],
                    color,
                    cameraPos
            );
        }
    }

    private void drawWireTriangle(PoseStack poseStack, VertexConsumer vertexConsumer,
                                  float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  float x3, float y3, float z3, int color, Vec3 cameraPos) {
        drawLine(poseStack, vertexConsumer, x1, y1, z1, x2, y2, z2, color, cameraPos);
        drawLine(poseStack, vertexConsumer, x2, y2, z2, x3, y3, z3, color, cameraPos);
        drawLine(poseStack, vertexConsumer, x3, y3, z3, x1, y1, z1, color, cameraPos);
    }

    private void drawLine(PoseStack poseStack, VertexConsumer vertexConsumer,
                          float x1, float y1, float z1,
                          float x2, float y2, float z2,
                          int color, Vec3 cameraPos) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = ((color >> 24) & 0xFF) / 255.0f;

        float camX = (float) cameraPos.x();
        float camY = (float) cameraPos.y();
        float camZ = (float) cameraPos.z();

        var matrix = poseStack.last().pose();
        var normal = poseStack.last().normal();

        vertexConsumer.vertex(matrix, x1 - camX, y1 - camY, z1 - camZ).color(r, g, b, a).normal(normal, 0, 1, 0).endVertex();
        vertexConsumer.vertex(matrix, x2 - camX, y2 - camY, z2 - camZ).color(r, g, b, a).normal(normal, 0, 1, 0).endVertex();
    }
}