/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.network.synchronization.packet;

import com.github.luben.zstd.Zstd;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.network.IVxNetPacket;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.world.VxClientPhysicsWorld;

import java.util.Map;

/**
 * A network packet (Server -> Client) that sends a ZSTD-compressed batch of custom data updates.
 * <p>
 * This is used to synchronize non-physics state from the server to clients. It allows
 * for efficient updates of arbitrary data associated with physics bodies by bundling
 * multiple updates into a single compressed payload.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public class S2CSynchronizedDataBatchPacket implements IVxNetPacket {

    /**
     * A map storing the network IDs of the bodies and their corresponding serialized custom data.
     */
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
     * Decodes the packet from the network buffer.
     * <p>
     * This method reads the compressed data block, decompresses it using Zstd, and
     * reconstructs the update map by reading from the decompressed stream.
     * </p>
     *
     * @param buf The buffer to read the compressed packet data from.
     * @return A new instance of the packet.
     * @throws IllegalStateException If the decompression or reconstruction fails.
     */
    public static S2CSynchronizedDataBatchPacket decode(VxByteBuf buf) {
        try {
            int uncompressedSize = buf.readVarInt();
            byte[] compressedData = buf.readByteArray();

            // Decompress the received byte array using the uncompressed size hint
            byte[] decompressedData = Zstd.decompress(compressedData, uncompressedSize);

            FriendlyByteBuf decompressedBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(decompressedData));
            int size = decompressedBuf.readVarInt();
            Map<Integer, byte[]> dataUpdates = new Int2ObjectArrayMap<>(size);

            for (int i = 0; i < size; i++) {
                int id = decompressedBuf.readVarInt();
                byte[] data = decompressedBuf.readByteArray();
                dataUpdates.put(id, data);
            }
            return new S2CSynchronizedDataBatchPacket(dataUpdates);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decompress S2C sync data batch packet", e);
        }
    }

    /**
     * Encodes the packet's data into the provided network buffer.
     * <p>
     * To maximize bandwidth efficiency, the update map is first written to a temporary
     * buffer, which is then compressed using Zstd before being written to the final output buffer.
     * </p>
     *
     * @param buf The extended buffer to write the compressed packet data to.
     */
    @Override
    public void encode(VxByteBuf buf) {
        FriendlyByteBuf tempBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            tempBuf.writeVarInt(this.dataUpdates.size());
            for (Map.Entry<Integer, byte[]> entry : this.dataUpdates.entrySet()) {
                tempBuf.writeVarInt(entry.getKey());
                tempBuf.writeByteArray(entry.getValue());
            }

            byte[] uncompressedData = new byte[tempBuf.readableBytes()];
            tempBuf.readBytes(uncompressedData);

            // Compress the serialized map data
            byte[] compressedData = Zstd.compress(uncompressedData);
            buf.writeVarInt(uncompressedData.length);
            buf.writeByteArray(compressedData);
        } finally {
            tempBuf.release();
        }
    }

    /**
     * Handles the packet on the client side.
     * <p>
     * This method applies the synchronized data updates to the corresponding bodies
     * via the client-side body manager.
     * </p>
     *
     * @param context The network context.
     */
    @Override
    public void handle(NetworkManager.PacketContext context) {
        context.queue(() -> {
            VxClientBodyManager manager = VxClientPhysicsWorld.getInstance().getBodyManager();
            for (Map.Entry<Integer, byte[]> entry : this.dataUpdates.entrySet()) {
                // Apply the serialized data to the body's synchronized data store
                manager.updateSynchronizedData(entry.getKey(), Unpooled.wrappedBuffer(entry.getValue()));
            }
        });
    }
}