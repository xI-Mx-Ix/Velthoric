package net.xmx.velthoric.ship.packet;

import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.mixin.util.ship.IVxClientPacketListener;
import net.xmx.velthoric.ship.plot.VxClientPlotManager;

import java.util.UUID;
import java.util.function.Supplier;

public class UpdateShipPlotDataPacket {

    private final UUID shipId;
    private final UUID plotId;
    private final ChunkPos plotCenter;
    private final int plotRadius;

    public UpdateShipPlotDataPacket(UUID shipId, UUID plotId, ChunkPos plotCenter, int plotRadius) {
        this.shipId = shipId;
        this.plotId = plotId;
        this.plotCenter = plotCenter;
        this.plotRadius = plotRadius;
    }

    public UpdateShipPlotDataPacket(FriendlyByteBuf buf) {
        this.shipId = buf.readUUID();
        this.plotId = buf.readUUID();
        this.plotCenter = buf.readChunkPos();
        this.plotRadius = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(shipId);
        buf.writeUUID(plotId);
        buf.writeChunkPos(plotCenter);
        buf.writeVarInt(plotRadius);
    }

    public static void handle(UpdateShipPlotDataPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            VxMainClass.LOGGER.info(
                    "[PACKET] Received ship plot data: ShipID={}, PlotCenter={}, Radius={}",
                    msg.shipId, msg.plotCenter, msg.plotRadius
            );
            VxClientPlotManager.getInstance().addShipPlot(msg.shipId, msg.plotCenter, msg.plotRadius);

            ClientPacketListener listener = Minecraft.getInstance().getConnection();
            if (listener != null) {
                ((IVxClientPacketListener) listener).velthoric$getSeamlessManager().onShipDataReceived(msg.shipId);
            }
        });
    }
}