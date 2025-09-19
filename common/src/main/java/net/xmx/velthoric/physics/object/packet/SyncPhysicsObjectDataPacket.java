/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.packet;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxBody;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * A network packet for synchronizing an object's custom data, which is not part of the
 * regular high-frequency state updates. This is used for data that changes infrequently.
 *
 * @author xI-Mx-Ix
 */
public class SyncPhysicsObjectDataPacket {

    // The UUID of the object whose data is being synchronized.
    private final UUID id;
    // The raw byte array of the custom data.
    private final byte[] data;

    /**
     * Constructs a packet from a server-side physics object, serializing its custom data.
     *
     * @param obj The object to sync.
     */
    public SyncPhysicsObjectDataPacket(VxBody obj) {
        this.id = obj.getPhysicsId();
        VxByteBuf buf = new VxByteBuf(Unpooled.buffer());
        try {
            obj.writeCreationData(buf); // The same method is used for both creation and sync.
            this.data = new byte[buf.readableBytes()];
            buf.readBytes(this.data);
        } finally {
            buf.release();
        }
    }

    /**
     * Constructs a packet by decoding it from a network buffer.
     *
     * @param buf The buffer to read from.
     */
    public SyncPhysicsObjectDataPacket(FriendlyByteBuf buf) {
        this.id = buf.readUUID();
        this.data = buf.readByteArray();
    }

    /**
     * Encodes the packet's data into a network buffer for sending.
     *
     * @param buf The buffer to write to.
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeByteArray(data);
    }

    /**
     * Handles the packet on the client side.
     *
     * @param msg            The received packet message.
     * @param contextSupplier A supplier for the network context.
     */
    public static void handle(SyncPhysicsObjectDataPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            // Executed on the client thread.
            VxClientObjectManager manager = VxClientObjectManager.getInstance();
            manager.updateCustomObjectData(msg.id, Unpooled.wrappedBuffer(msg.data));
        });
    }
}