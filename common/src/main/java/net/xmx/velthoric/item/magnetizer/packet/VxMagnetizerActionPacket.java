/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.magnetizer.packet;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.item.magnetizer.VxMagnetizerManager;

import java.util.function.Supplier;

/**
 * A packet for magnetizer actions.
 *
 * @author xI-Mx-Ix
 */
public class VxMagnetizerActionPacket {

    private final ActionType actionType;

    public enum ActionType {
        START_ATTRACT,
        START_REPEL,
        STOP_ACTION
    }

    public VxMagnetizerActionPacket(ActionType actionType) {
        this.actionType = actionType;
    }

    public static void encode(VxMagnetizerActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.actionType);
    }

    public static VxMagnetizerActionPacket decode(FriendlyByteBuf buf) {
        return new VxMagnetizerActionPacket(buf.readEnum(ActionType.class));
    }

    public static void handle(VxMagnetizerActionPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player == null) return;

            var manager = VxMagnetizerManager.getInstance();
            switch (msg.actionType) {
                case START_ATTRACT -> manager.startAttract(player);
                case START_REPEL -> manager.startRepel(player);
                case STOP_ACTION -> manager.stop(player);
            }
        });
    }
}
