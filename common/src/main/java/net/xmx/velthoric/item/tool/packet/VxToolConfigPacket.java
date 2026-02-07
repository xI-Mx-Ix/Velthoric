/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.tool.packet;

import dev.architectury.networking.NetworkManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.xmx.velthoric.item.tool.VxToolMode;
import net.xmx.velthoric.item.tool.config.VxToolConfig;
import net.xmx.velthoric.item.tool.config.VxToolProperty;
import net.xmx.velthoric.item.tool.registry.VxToolRegistry;
import net.xmx.velthoric.network.IVxNetPacket;
import net.xmx.velthoric.network.VxByteBuf;

import java.util.Map;

/**
 * Packet sent from Client UI to Server to update tool configuration values.
 * <p>
 * This packet contains the item ID of the tool and a map of property keys to their
 * new stringified values. The server parses these values back into their original
 * types (Integer, Float, Boolean).
 * </p>
 *
 * @author xI-Mx-Ix
 */
public class VxToolConfigPacket implements IVxNetPacket {

    private final int itemId;
    private final Map<String, String> stringifiedValues;

    /**
     * Constructs a new tool configuration update packet.
     *
     * @param itemId            The numeric ID of the item.
     * @param stringifiedValues A map of property keys to their values as strings.
     */
    public VxToolConfigPacket(int itemId, Map<String, String> stringifiedValues) {
        this.itemId = itemId;
        this.stringifiedValues = stringifiedValues;
    }

    /**
     * Decodes the packet from the network buffer.
     *
     * @param buf The buffer to read from.
     * @return A new instance of the packet.
     */
    public static VxToolConfigPacket decode(VxByteBuf buf) {
        return new VxToolConfigPacket(
                buf.readInt(),
                // Use explicit lambdas to resolve ambiguity with writeUtf/readUtf overloads
                buf.readMap(b -> b.readUtf(), b -> b.readUtf())
        );
    }

    @Override
    public void encode(VxByteBuf buf) {
        buf.writeInt(this.itemId);
        // Use explicit lambdas to ensure the correct writeUtf overload is used
        buf.writeMap(this.stringifiedValues, (b, s) -> b.writeUtf(s), (b, s) -> b.writeUtf(s));
    }

    @Override
    public void handle(NetworkManager.PacketContext context) {
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player == null) return;

            Item item = Item.byId(this.itemId);
            VxToolMode mode = VxToolRegistry.get(item);

            if (mode != null) {
                VxToolConfig config = mode.getConfig(player.getUUID());

                // Parse values back to their original types based on the property definition
                for (Map.Entry<String, String> entry : this.stringifiedValues.entrySet()) {
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
                            // Invalid input from client, ignore the change
                        }
                    }
                }
            }
        });
    }
}