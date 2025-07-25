package net.xmx.vortex.debug.drawer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.xmx.vortex.debug.drawer.packet.DebugShapesUpdatePacket;
import net.xmx.vortex.physics.object.physicsobject.client.time.VxClientClock;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ClientShapeDrawer {

    private static final ClientShapeDrawer INSTANCE = new ClientShapeDrawer();
    public static boolean ENABLED = true;

    private static final long INTERPOLATION_DELAY_NANOS = 150_000_000L;
    private static final int MAX_BUFFER_SIZE = 20;

    private static class DebugStateSnapshot {
        long serverTimestampNanos;
        float[] vertices;

        private static final ConcurrentLinkedQueue<DebugStateSnapshot> POOL = new ConcurrentLinkedQueue<>();

        public static DebugStateSnapshot acquire(long timestamp, float[] vertices) {
            DebugStateSnapshot s = POOL.poll();
            if (s == null) s = new DebugStateSnapshot();
            s.serverTimestampNanos = timestamp;
            s.vertices = vertices;
            return s;
        }

        public static void release(DebugStateSnapshot s) {
            if (s != null) {
                s.vertices = null;
                POOL.offer(s);
            }
        }
    }

    private final Map<Integer, Deque<DebugStateSnapshot>> stateBuffers = new ConcurrentHashMap<>();
    private final Map<Integer, float[]> lastKnownVertices = new ConcurrentHashMap<>();

    private long clockOffsetNanos = 0L;
    private boolean isClockOffsetInitialized = false;

    private ClientShapeDrawer() {}

    public static ClientShapeDrawer getInstance() {
        return INSTANCE;
    }

    public void onPacketReceived(DebugShapesUpdatePacket packet) {
        if (!ENABLED) {
            stateBuffers.clear();
            lastKnownVertices.clear();
            return;
        }

        long serverTimestamp = packet.getTimestampNanos();
        long clientReceiptTime = VxClientClock.getInstance().getGameTimeNanos();

        if (!isClockOffsetInitialized) {
            this.clockOffsetNanos = serverTimestamp - clientReceiptTime;
            this.isClockOffsetInitialized = true;
        } else {
            long newOffset = serverTimestamp - clientReceiptTime;

            this.clockOffsetNanos = (long) (this.clockOffsetNanos * 0.95 + newOffset * 0.05);
        }

        Set<Integer> receivedBodyIds = new HashSet<>(packet.getDrawData().keySet());

        packet.getDrawData().forEach((bodyId, data) -> {
            Deque<DebugStateSnapshot> buffer = stateBuffers.computeIfAbsent(bodyId, k -> new ArrayDeque<>());

            if (buffer.isEmpty() || serverTimestamp > buffer.peekLast().serverTimestampNanos) {
                buffer.addLast(DebugStateSnapshot.acquire(serverTimestamp, data.vertices()));
                lastKnownVertices.put(bodyId, data.vertices());
            }

            while (buffer.size() > MAX_BUFFER_SIZE) {
                DebugStateSnapshot.release(buffer.removeFirst());
            }
        });

        stateBuffers.keySet().removeIf(id -> {
            if (!receivedBodyIds.contains(id)) {

                stateBuffers.get(id).forEach(DebugStateSnapshot::release);
                lastKnownVertices.remove(id);
                return true;
            }
            return false;
        });
    }

    public void render(PoseStack poseStack, VertexConsumer vertexConsumer, Vec3 cameraPos) {
        if (!ENABLED || stateBuffers.isEmpty()) {
            return;
        }

        poseStack.pushPose();

        long estimatedServerTime = VxClientClock.getInstance().getGameTimeNanos() + this.clockOffsetNanos;
        long renderTimestamp = estimatedServerTime - INTERPOLATION_DELAY_NANOS;

        for (Map.Entry<Integer, Deque<DebugStateSnapshot>> entry : stateBuffers.entrySet()) {
            Integer bodyId = entry.getKey();
            Deque<DebugStateSnapshot> buffer = entry.getValue();

            if (buffer.size() < 2) {

                float[] vertices = lastKnownVertices.get(bodyId);
                if (vertices != null) {
                    drawVertices(poseStack, vertexConsumer, vertices, 0xFFFFFFFF, cameraPos);
                }
                continue;
            }

            DebugStateSnapshot from = null;
            DebugStateSnapshot to = null;
            for (DebugStateSnapshot s : buffer) {
                if (s.serverTimestampNanos <= renderTimestamp) {
                    from = s;
                } else {
                    to = s;
                    break;
                }
            }

            if (from == null || to == null) {
                float[] vertices = lastKnownVertices.get(bodyId);
                if (vertices != null) {
                    drawVertices(poseStack, vertexConsumer, vertices, 0xFFFFFFFF, cameraPos);
                }
                continue;
            }

            long timeDiff = to.serverTimestampNanos - from.serverTimestampNanos;
            if (timeDiff <= 0) {
                drawVertices(poseStack, vertexConsumer, from.vertices, 0xFFFFFFFF, cameraPos);
                continue;
            }

            float alpha = (float) (renderTimestamp - from.serverTimestampNanos) / (float) timeDiff;
            alpha = Mth.clamp(alpha, 0.0f, 1.0f);

            float[] interpolatedVertices = lerpVertices(from.vertices, to.vertices, alpha);
            drawVertices(poseStack, vertexConsumer, interpolatedVertices, 0xFFFFFFFF, cameraPos);
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

    private float[] lerpVertices(float[] prev, float[] curr, float alpha) {
        if (prev == null || curr == null || prev.length != curr.length) {
            return curr != null ? curr : prev;
        }
        float[] result = new float[curr.length];
        for (int i = 0; i < curr.length; i++) {
            result[i] = Mth.lerp(alpha, prev[i], curr[i]);
        }
        return result;
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