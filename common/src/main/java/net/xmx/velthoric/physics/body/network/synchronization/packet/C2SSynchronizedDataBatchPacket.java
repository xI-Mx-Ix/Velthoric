/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.network.synchronization.packet;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.network.VxPacketUtils;
import net.xmx.velthoric.physics.body.network.synchronization.manager.VxServerSyncManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.io.IOException;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A network packet (Client -> Server) that sends a ZSTD-compressed batch of custom data updates.
 * This handles updates where the client has authority.
 * It strictly validates that the client is allowed to modify the data.
 *
 * @author xI-Mx-Ix
 */
public class C2SSynchronizedDataBatchPacket {

    private final Map<Integer, byte[]> dataUpdates;

    /**
     * Constructs a new C2S batch packet.
     *
     * @param dataUpdates A map of Network ID -> Serialized Data Buffer.
     */
    public C2SSynchronizedDataBatchPacket(Map<Integer, byte[]> dataUpdates) {
        this.dataUpdates = dataUpdates;
    }

    /**
     * Encodes the packet's data into a network buffer.
     * Uses ZSTD compression to minimize bandwidth usage.
     *
     * @param msg The packet instance to encode.
     * @param buf The buffer to write to.
     */
    public static void encode(C2SSynchronizedDataBatchPacket msg, FriendlyByteBuf buf) {
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
            throw new IllegalStateException("Failed to compress C2S sync data batch packet", e);
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
    public static C2SSynchronizedDataBatchPacket decode(FriendlyByteBuf buf) {
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
            return new C2SSynchronizedDataBatchPacket(dataUpdates);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to decompress C2S sync data batch packet", e);
        }
    }

    /**
     * Handles the packet on the server side.
     * Validates and applies updates using the anti-cheat logic in VxSynchronizedData.
     *
     * @param msg             The received packet.
     * @param contextSupplier A supplier for the network packet context.
     */
    public static void handle(C2SSynchronizedDataBatchPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            VxPhysicsWorld world = VxPhysicsWorld.get(player.serverLevel().dimension());

            if (world == null) return;

            VxServerSyncManager syncManager = world.getBodyManager().getServerSyncManager();

            for (Map.Entry<Integer, byte[]> entry : msg.dataUpdates.entrySet()) {
                // Delegate validation and application to the server sync manager
                syncManager.processClientUpdate(entry.getKey(), entry.getValue(), player);
            }
        });
    }
}