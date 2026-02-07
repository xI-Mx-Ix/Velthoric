/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.tool.packet;

import dev.architectury.networking.NetworkManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.xmx.velthoric.item.tool.VxToolMode;
import net.xmx.velthoric.item.tool.registry.VxToolRegistry;
import net.xmx.velthoric.network.IVxNetPacket;
import net.xmx.velthoric.network.VxByteBuf;

/**
 * Packet sent when the player uses a tool (Left/Right click events).
 *
 * @author xI-Mx-Ix
 */
public class VxToolActionPacket implements IVxNetPacket {

    private final VxToolMode.ActionState action;

    public VxToolActionPacket(VxToolMode.ActionState action) {
        this.action = action;
    }

    public static VxToolActionPacket decode(VxByteBuf buf) {
        return new VxToolActionPacket(buf.readEnum(VxToolMode.ActionState.class));
    }

    @Override
    public void encode(VxByteBuf buf) {
        buf.writeEnum(this.action);
    }

    @Override
    public void handle(NetworkManager.PacketContext context) {
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player == null) return;

            Item heldItem = player.getMainHandItem().getItem();
            VxToolMode mode = VxToolRegistry.get(heldItem);

            if (mode != null) {
                mode.setState(player, this.action);
            }
        });
    }
}