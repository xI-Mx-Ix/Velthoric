/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.network;

import dev.architectury.networking.NetworkManager;

/**
 * Interface that all Velthoric network packets must implement.
 * <p>
 * This provides a unified structure for encoding and handling packets, allowing
 * them to be managed by the {@link VxNetworking} central registry.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public interface IVxNetPacket {

    /**
     * Encodes the packet data into the provided buffer.
     *
     * @param buf The extended buffer to write data to.
     */
    void encode(VxByteBuf buf);

    /**
     * Handles the packet logic when received.
     *
     * @param context The network context (player, environment, etc.).
     */
    void handle(NetworkManager.PacketContext context);
}