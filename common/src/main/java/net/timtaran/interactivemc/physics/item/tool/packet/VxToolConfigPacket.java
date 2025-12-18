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
import net.timtaran.interactivemc.physics.item.tool.config.VxToolConfig;
import net.timtaran.interactivemc.physics.item.tool.config.VxToolProperty;
import net.timtaran.interactivemc.physics.item.tool.registry.VxToolRegistry;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Packet sent from Client UI to Server to update tool configuration.
 *
 * @author xI-Mx-Ix
 */
public class VxToolConfigPacket {

    private final int itemId;
    private final Map<String, String> stringifiedValues;

    public VxToolConfigPacket(int itemId, Map<String, String> values) {
        this.itemId = itemId;
        this.stringifiedValues = values;
    }

    public static void encode(VxToolConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.itemId);
        buf.writeMap(msg.stringifiedValues, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeUtf);
    }

    public static VxToolConfigPacket decode(FriendlyByteBuf buf) {
        return new VxToolConfigPacket(
                buf.readInt(),
                buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readUtf)
        );
    }

    public static void handle(VxToolConfigPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player == null) return;

            Item item = Item.byId(msg.itemId);
            VxToolMode mode = VxToolRegistry.get(item);

            if (mode != null) {
                VxToolConfig config = mode.getConfig(player.getUUID());
                
                // Parse values back to their original types
                for (Map.Entry<String, String> entry : msg.stringifiedValues.entrySet()) {
                    String key = entry.getKey();
                    String valStr = entry.getValue();
                    
                    if (config.getProperties().containsKey(key)) {
                        VxToolProperty<?> prop = config.getProperties().get(key);
                        try {
                            if (prop.getType() == Integer.class) {
                                config.setValue(key, Integer.parseInt(valStr));
                            } else if (prop.getType() == Float.class) {
                                config.setValue(key, Float.parseFloat(valStr));
                            } else if (prop.getType() == Boolean.class) {
                                config.setValue(key, Boolean.parseBoolean(valStr));
                            }
                        } catch (NumberFormatException ignored) {
                            // Invalid input from client, ignore
                        }
                    }
                }
            }
        });
    }
}