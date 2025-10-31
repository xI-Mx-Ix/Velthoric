/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.packet.batch;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.network.VxPacketUtils;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.packet.VxSpawnData;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

/**
 * A network packet that contains a compressed batch of physics bodies to be spawned on the client.
 * This is more efficient than sending a separate packet for each individual body.
 *
 * @author xI-Mx-Ix
 */
public class S2CSpawnBodyBatchPacket {

    private final List<VxSpawnData> spawnDataList;

    /**
     * Constructs a new batch packet with a list of objects to spawn.
     *
     * @param spawnDataList The list of {@link VxSpawnData} for each body.
     */
    public S2CSpawnBodyBatchPacket(List<VxSpawnData> spawnDataList) {
        this.spawnDataList = spawnDataList;
    }

    /**
     * Encodes the packet's data into a network buffer.
     *
     * @param msg The packet instance to encode.
     * @param buf The buffer to write to.
     */
    public static void encode(S2CSpawnBodyBatchPacket msg, FriendlyByteBuf buf) {
        FriendlyByteBuf tempBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            tempBuf.writeVarInt(msg.spawnDataList.size());
            for (VxSpawnData data : msg.spawnDataList) {
                data.encode(tempBuf);
            }
            byte[] uncompressedData = new byte[tempBuf.readableBytes()];
            tempBuf.readBytes(uncompressedData);

            byte[] compressedData = VxPacketUtils.compress(uncompressedData);
            buf.writeVarInt(uncompressedData.length); // Write uncompressed size for client
            buf.writeByteArray(compressedData);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to compress spawn body batch packet", e);
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
    public static S2CSpawnBodyBatchPacket decode(FriendlyByteBuf buf) {
        int uncompressedSize = buf.readVarInt();
        byte[] compressedData = buf.readByteArray();
        try {
            byte[] decompressedData = VxPacketUtils.decompress(compressedData, uncompressedSize);
            FriendlyByteBuf decompressedBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(decompressedData));
            int size = decompressedBuf.readVarInt();
            List<VxSpawnData> spawnDataList = new ObjectArrayList<>(size);
            for (int i = 0; i < size; i++) {
                spawnDataList.add(new VxSpawnData(decompressedBuf));
            }
            return new S2CSpawnBodyBatchPacket(spawnDataList);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to decompress spawn body batch packet", e);
        }
    }

    /**
     * Handles the packet on the client side.
     *
     * @param msg             The received packet.
     * @param contextSupplier A supplier for the network packet context.
     */
    public static void handle(S2CSpawnBodyBatchPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            VxClientBodyManager manager = VxClientBodyManager.getInstance();
            // Iterate through each spawn data entry and spawn the corresponding body on the client.
            for (VxSpawnData data : msg.spawnDataList) {
                // Wrap the raw byte data into a buffer for the manager to read.
                VxByteBuf dataBuf = new VxByteBuf(Unpooled.wrappedBuffer(data.data));
                try {
                    manager.spawnBody(data.id, data.networkId, data.typeIdentifier, dataBuf, data.timestamp);
                } finally {
                    // Ensure the buffer is released to prevent memory leaks.
                    if (dataBuf.refCnt() > 0) {
                        dataBuf.release();
                    }
                }
            }
        });
    }
}