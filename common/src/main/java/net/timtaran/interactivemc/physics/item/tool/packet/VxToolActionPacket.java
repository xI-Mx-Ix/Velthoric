/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.item.tool.packet;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.timtaran.interactivemc.physics.item.tool.VxToolMode;
import net.timtaran.interactivemc.physics.item.tool.registry.VxToolRegistry;

import java.util.function.Supplier;

/**
 * Packet sent when the player uses a tool (Left/Right click).
 *
 * @author xI-Mx-Ix
 */
public class VxToolActionPacket {

    private final VxToolMode.ActionState action;

    public VxToolActionPacket(VxToolMode.ActionState action) {
        this.action = action;
    }

    public static void encode(VxToolActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
    }

    public static VxToolActionPacket decode(FriendlyByteBuf buf) {
        return new VxToolActionPacket(buf.readEnum(VxToolMode.ActionState.class));
    }

    public static void handle(VxToolActionPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player == null) return;

            Item heldItem = player.getMainHandItem().getItem();
            VxToolMode mode = VxToolRegistry.get(heldItem);

            if (mode != null) {
                mode.setState(player, msg.action);
            }
        });
    }
}