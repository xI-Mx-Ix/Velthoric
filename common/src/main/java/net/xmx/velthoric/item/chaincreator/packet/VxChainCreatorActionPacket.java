/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.chaincreator.packet;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.item.chaincreator.VxChainCreatorManager;

import java.util.function.Supplier;

/**
 * A network packet to communicate player actions for the Chain Creator item.
 *
 * @author xI-Mx-Ix
 */
public class VxChainCreatorActionPacket {

    private final ActionType actionType;

    /**
     * Defines the type of action the player is performing.
     */
    public enum ActionType {
        /** Sent when the player presses the primary use button. */
        START_CREATION,
        /** Sent when the player releases the primary use button. */
        FINISH_CREATION
    }

    public VxChainCreatorActionPacket(ActionType actionType) {
        this.actionType = actionType;
    }

    /**
     * Encodes the packet data into a buffer.
     * @param msg The packet message.
     * @param buf The buffer to write to.
     */
    public static void encode(VxChainCreatorActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.actionType);
    }

    /**
     * Decodes the packet data from a buffer.
     * @param buf The buffer to read from.
     * @return A new packet instance.
     */
    public static VxChainCreatorActionPacket decode(FriendlyByteBuf buf) {
        return new VxChainCreatorActionPacket(buf.readEnum(ActionType.class));
    }

    /**
     * Handles the packet on the server side by delegating to the VxChainCreatorManager.
     * @param msg The received packet.
     * @param contextSupplier The packet context.
     */
    public static void handle(VxChainCreatorActionPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player == null) return;

            var manager = VxChainCreatorManager.INSTANCE;
            switch (msg.actionType) {
                case START_CREATION -> manager.startChainCreation(player);
                case FINISH_CREATION -> manager.finishChainCreation(player);
            }
        });
    }
}