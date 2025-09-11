/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.packet.batch;

import dev.architectury.networking.NetworkManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A network packet that contains a batch of UUIDs for physics objects to be removed
 * from the client. Batching reduces network overhead compared to sending one packet per removal.
 *
 * @author xI-Mx-Ix
 */
public class RemovePhysicsObjectBatchPacket {

    /** The list of object UUIDs to be removed. */
    private final List<UUID> ids;

    /**
     * Constructs a new packet with a list of UUIDs to send.
     *
     * @param ids The list of UUIDs.
     */
    public RemovePhysicsObjectBatchPacket(List<UUID> ids) {
        this.ids = ids;
    }

    /**
     * Constructs a packet by decoding it from a network buffer.
     *
     * @param buf The buffer to read from.
     */
    public RemovePhysicsObjectBatchPacket(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.ids = new ObjectArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.ids.add(buf.readUUID());
        }
    }

    /**
     * Encodes the packet's data into a network buffer for sending.
     *
     * @param buf The buffer to write to.
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(ids.size());
        for (UUID id : ids) {
            buf.writeUUID(id);
        }
    }

    /**
     * Handles the packet on the client side.
     *
     * @param msg            The received packet message.
     * @param contextSupplier A supplier for the network context.
     */
    public static void handle(RemovePhysicsObjectBatchPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            // Executed on the client thread.
            VxClientObjectManager manager = VxClientObjectManager.getInstance();
            for (UUID id : msg.ids) {
                manager.removeObject(id);
            }
        });
    }
}