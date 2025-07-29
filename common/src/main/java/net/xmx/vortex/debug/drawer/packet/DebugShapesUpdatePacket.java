package net.xmx.vortex.debug.drawer.packet;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.debug.drawer.client.ClientShapeDrawer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class DebugShapesUpdatePacket {

    private final Map<Integer, BodyDrawData> drawData;

    public record BodyDrawData(int color, float[] vertices) {}

    public DebugShapesUpdatePacket(Map<Integer, BodyDrawData> data) {
        this.drawData = data;
    }

    public DebugShapesUpdatePacket(FriendlyByteBuf buf) {
        int mapSize = buf.readVarInt();
        this.drawData = new HashMap<>(mapSize);
        for (int i = 0; i < mapSize; i++) {
            int bodyId = buf.readVarInt();
            int color = buf.readInt();
            int length = buf.readVarInt();
            float[] vertices = new float[length];
            for (int j = 0; j < length; j++) {
                vertices[j] = buf.readFloat();
            }
            this.drawData.put(bodyId, new BodyDrawData(color, vertices));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(drawData.size());
        for (Map.Entry<Integer, BodyDrawData> entry : drawData.entrySet()) {
            buf.writeVarInt(entry.getKey());
            buf.writeInt(entry.getValue().color());
            float[] vertices = entry.getValue().vertices();
            buf.writeVarInt(vertices.length);
            for (float v : vertices) {
                buf.writeFloat(v);
            }
        }
    }

    public static void handle(DebugShapesUpdatePacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            ClientShapeDrawer.getInstance().onPacketReceived(msg);
        });
    }

    public Map<Integer, BodyDrawData> getDrawData() {
        return drawData;
    }
}