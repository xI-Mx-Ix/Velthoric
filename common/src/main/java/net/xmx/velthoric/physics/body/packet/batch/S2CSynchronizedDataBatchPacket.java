/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.packet.batch;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.network.VxPacketUtils;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;

import java.io.IOException;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A network packet that sends a compressed batch of custom data updates for various physics bodies.
 * This is used to synchronize non-physics state that is specific to a body's implementation.
 *
 * @author xI-Mx-Ix
 */
public class S2CSynchronizedDataBatchPacket {

    private final Map<Integer, byte[]> dataUpdates;

    /**
     * Constructs a new packet with a map of object network IDs to their custom data.
     *
     * @param dataUpdates A map where the key is the body's network ID and the value is the serialized custom data.
     */
    public S2CSynchronizedDataBatchPacket(Map<Integer, byte[]> dataUpdates) {
        this.dataUpdates = dataUpdates;
    }

    /**
     * Encodes the packet's data into a network buffer.
     *
     * @param msg The packet instance to encode.
     * @param buf The buffer to write to.
     */
    public static void encode(S2CSynchronizedDataBatchPacket msg, FriendlyByteBuf buf) {
        FriendlyByteBuf tempBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            tempBuf.writeVarInt(msg.dataUpdates.size());
            for (Map.Entry<Integer, byte[]> entry : msg.dataUpdates.entrySet()) {
                tempBuf.writeVarInt(entry.getKey());
                tempBuf.writeByteArray(entry.getValue());
            }

            byte[] uncompressedData = new byte[tempBuf.readableBytes()];
            tempBuf.readBytes(uncompressedData);

            byte[] compressedData = VxPacketUtils.compress(uncompressedData);
            buf.writeVarInt(uncompressedData.length);
            buf.writeByteArray(compressedData);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to compress sync data batch packet", e);
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
    public static S2CSynchronizedDataBatchPacket decode(FriendlyByteBuf buf) {
        try {
            int uncompressedSize = buf.readVarInt();
            byte[] compressedData = buf.readByteArray();
            byte[] decompressedData = VxPacketUtils.decompress(compressedData, uncompressedSize);
            FriendlyByteBuf decompressedBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(decompressedData));

            int size = decompressedBuf.readVarInt();
            Map<Integer, byte[]> dataUpdates = new Int2ObjectArrayMap<>(size);
            for (int i = 0; i < size; i++) {
                int id = decompressedBuf.readVarInt();
                byte[] data = decompressedBuf.readByteArray();
                dataUpdates.put(id, data);
            }
            return new S2CSynchronizedDataBatchPacket(dataUpdates);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to decompress sync data batch packet", e);
        }
    }

    /**
     * Handles the packet on the client side.
     *
     * @param msg             The received packet.
     * @param contextSupplier A supplier for the network packet context.
     */
    public static void handle(S2CSynchronizedDataBatchPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            VxClientBodyManager manager = VxClientBodyManager.getInstance();
            // Apply each custom data update to the corresponding client-side body.
            for (Map.Entry<Integer, byte[]> entry : msg.dataUpdates.entrySet()) {
                manager.updateSynchronizedData(entry.getKey(), Unpooled.wrappedBuffer(entry.getValue()));
            }
        });
    }
}