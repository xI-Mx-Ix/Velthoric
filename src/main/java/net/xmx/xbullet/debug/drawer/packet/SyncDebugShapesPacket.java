package net.xmx.xbullet.debug.drawer.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.xmx.xbullet.debug.drawer.DebugLine;
import net.xmx.xbullet.debug.drawer.DebugShapeRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncDebugShapesPacket {

    private final List<DebugLine> lines;

    public SyncDebugShapesPacket(List<DebugLine> lines) {
        this.lines = lines;
    }

    public SyncDebugShapesPacket(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.lines = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            lines.add(new DebugLine(
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readInt()
            ));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(lines.size());
        for (DebugLine line : lines) {
            buf.writeDouble(line.fromX);
            buf.writeDouble(line.fromY);
            buf.writeDouble(line.fromZ);
            buf.writeDouble(line.toX);
            buf.writeDouble(line.toY);
            buf.writeDouble(line.toZ);
            buf.writeInt(line.color);
        }
    }

    public static void handle(SyncDebugShapesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DebugShapeRenderer.setLinesToRender(msg.lines));
        ctx.get().setPacketHandled(true);
    }
}