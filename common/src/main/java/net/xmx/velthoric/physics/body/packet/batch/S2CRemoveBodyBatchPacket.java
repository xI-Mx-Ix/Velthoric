/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.packet.batch;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.network.VxPacketUtils;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A network packet that contains a batch of UUIDs for physics bodies to be removed
 * from the client. Batching and compression reduce network overhead compared to sending one packet per removal.
 *
 * @author xI-Mx-Ix
 */
public class S2CRemoveBodyBatchPacket {

    private final List<UUID> ids;

    /**
     * Constructs a new packet with a list of UUIDs to send.
     *
     * @param ids The list of UUIDs.
     */
    public S2CRemoveBodyBatchPacket(List<UUID> ids) {
        this.ids = ids;
    }

    /**
     * Encodes the packet's data into a network buffer for sending.
     *
     * @param msg The packet instance to encode.
     * @param buf The buffer to write to.
     */
    public static void encode(S2CRemoveBodyBatchPacket msg, FriendlyByteBuf buf) {
        FriendlyByteBuf tempBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            tempBuf.writeVarInt(msg.ids.size());
            for (UUID id : msg.ids) {
                tempBuf.writeUUID(id);
            }
            byte[] uncompressedData = new byte[tempBuf.readableBytes()];
            tempBuf.readBytes(uncompressedData);

            byte[] compressedData = VxPacketUtils.compress(uncompressedData);
            buf.writeVarInt(uncompressedData.length); // Write uncompressed size for the client
            buf.writeByteArray(compressedData);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to compress remove body batch packet", e);
        } finally {
            tempBuf.release();
        }
    }

    /**
     * Decodes the packet from a network buffer.
     *
     * @param buf The buffer to read from.
     * @return A new instance of the packet.
     */
    public static S2CRemoveBodyBatchPacket decode(FriendlyByteBuf buf) {
        int uncompressedSize = buf.readVarInt();
        byte[] compressedData = buf.readByteArray();
        try {
            byte[] decompressedData = VxPacketUtils.decompress(compressedData, uncompressedSize);
            FriendlyByteBuf decompressedBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(decompressedData));
            int size = decompressedBuf.readVarInt();
            List<UUID> ids = new ObjectArrayList<>(size);
            for (int i = 0; i < size; i++) {
                ids.add(decompressedBuf.readUUID());
            }
            return new S2CRemoveBodyBatchPacket(ids);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to decompress remove body batch packet", e);
        }
    }

    /**
     * Handles the packet on the client side.
     *
     * @param msg            The received packet message.
     * @param contextSupplier A supplier for the network context.
     */
    public static void handle(S2CRemoveBodyBatchPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            // Executed on the client thread.
            VxClientBodyManager manager = VxClientBodyManager.getInstance();
            for (UUID id : msg.ids) {
                manager.removeBody(id);
            }
        });
    }
}