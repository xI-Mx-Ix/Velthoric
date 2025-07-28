package net.xmx.vortex.item.magnetizer.packet;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.vortex.item.magnetizer.MagnetizerManager;

import java.util.function.Supplier;

public class MagnetizerActionPacket {

    private final ActionType actionType;

    public enum ActionType {
        START_ATTRACT,
        START_REPEL,
        STOP_ACTION
    }

    public MagnetizerActionPacket(ActionType actionType) {
        this.actionType = actionType;
    }

    public static void encode(MagnetizerActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.actionType);
    }

    public static MagnetizerActionPacket decode(FriendlyByteBuf buf) {
        return new MagnetizerActionPacket(buf.readEnum(ActionType.class));
    }

    public static void handle(MagnetizerActionPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player == null) return;

            var manager = MagnetizerManager.getInstance();
            switch (msg.actionType) {
                case START_ATTRACT -> manager.startAttract(player);
                case START_REPEL -> manager.startRepel(player);
                case STOP_ACTION -> manager.stop(player);
            }
        });
    }
}