package net.xmx.vortex.debug.drawer.packet;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.debug.drawer.client.ClientShapeDrawer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class DebugShapesUpdatePacket {

    private final Map<Integer, BodyDrawData> drawData;
    private final boolean isFirstBatch;

    public record BodyDrawData(int color, float[] vertices) {
        public int estimateSize() {
            return 4 + 4 + 5 + (vertices.length * 4);
        }
    }

    public DebugShapesUpdatePacket(Map<Integer, BodyDrawData> data, boolean isFirstBatch) {
        this.drawData = data;
        this.isFirstBatch = isFirstBatch;
    }

    public DebugShapesUpdatePacket(FriendlyByteBuf buf) {
        this.isFirstBatch = buf.readBoolean();
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
        buf.writeBoolean(isFirstBatch);
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

    public boolean isFirstBatch() {
        return isFirstBatch;
    }
}