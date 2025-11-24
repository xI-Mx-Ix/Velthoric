/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.boxthrower.packet;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.item.boxthrower.VxBoxThrowerManager;

import java.util.function.Supplier;

/**
 * A packet for box thrower actions.
 *
 * @author xI-Mx-Ix
 */
public class VxBoxThrowerActionPacket {

    private final ActionType actionType;

    public enum ActionType {
        START_SHOOTING,
        STOP_SHOOTING
    }

    public VxBoxThrowerActionPacket(ActionType actionType) {
        this.actionType = actionType;
    }

    public static void encode(VxBoxThrowerActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.actionType);
    }

    public static VxBoxThrowerActionPacket decode(FriendlyByteBuf buf) {
        return new VxBoxThrowerActionPacket(buf.readEnum(ActionType.class));
    }

    public static void handle(VxBoxThrowerActionPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player == null) return;

            var manager = VxBoxThrowerManager.getInstance();
            switch (msg.actionType) {
                case START_SHOOTING -> manager.startShooting(player);
                case STOP_SHOOTING -> manager.stopShooting(player);
            }
        });
    }
}
