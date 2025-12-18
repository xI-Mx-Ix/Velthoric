/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.physics.body.sync.packet;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.network.FriendlyByteBuf;
import net.timtaran.interactivemc.physics.network.VxPacketUtils;
import net.timtaran.interactivemc.physics.physics.body.client.VxClientBodyManager;

import java.io.IOException;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A network packet (Server -> Client) that sends a ZSTD-compressed batch of custom data updates.
 * This is used to synchronize non-physics state from the server to clients.
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
     * Uses ZSTD compression to minimize bandwidth usage.
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
            throw new IllegalStateException("Failed to compress S2C sync data batch packet", e);
        } finally {
            tempBuf.release();
        }
    }

    /**
     * Decodes the packet from a network buffer.
     * Decompresses the data before reconstructing the map.
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
            throw new IllegalStateException("Failed to decompress S2C sync data batch packet", e);
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
            for (Map.Entry<Integer, byte[]> entry : msg.dataUpdates.entrySet()) {
                manager.updateSynchronizedData(entry.getKey(), Unpooled.wrappedBuffer(entry.getValue()));
            }
        });
    }
}