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
     * Constructs a packet by decoding it from a network buffer.
     *
     * @param buf The buffer to read from.
     */
    public S2CRemoveBodyBatchPacket(FriendlyByteBuf buf) {
        int uncompressedSize = buf.readVarInt();
        byte[] compressedData = buf.readByteArray();
        try {
            byte[] decompressedData = VxPacketUtils.decompress(compressedData, uncompressedSize);
            FriendlyByteBuf decompressedBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(decompressedData));
            int size = decompressedBuf.readVarInt();
            this.ids = new ObjectArrayList<>(size);
            for (int i = 0; i < size; i++) {
                this.ids.add(decompressedBuf.readUUID());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to decompress remove body batch packet", e);
        }
    }

    /**
     * Encodes the packet's data into a network buffer for sending.
     *
     * @param buf The buffer to write to.
     */
    public void encode(FriendlyByteBuf buf) {
        FriendlyByteBuf tempBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            tempBuf.writeVarInt(ids.size());
            for (UUID id : ids) {
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