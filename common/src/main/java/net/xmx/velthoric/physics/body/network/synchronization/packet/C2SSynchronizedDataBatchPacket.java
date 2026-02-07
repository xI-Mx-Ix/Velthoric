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
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.network.IVxNetPacket;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.body.network.synchronization.manager.VxServerSyncManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.Map;

/**
 * A network packet (Client -> Server) that sends a ZSTD-compressed batch of custom data updates.
 * <p>
 * This handles updates where the client has authority.
 * It strictly validates that the client is allowed to modify the data.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public class C2SSynchronizedDataBatchPacket implements IVxNetPacket {

    /**
     * A map containing the data updates, mapping Network IDs to their serialized byte arrays.
     */
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
     * Decodes the packet from the network buffer.
     * <p>
     * This method reads the compressed binary blob, decompresses it using Zstd,
     * and reconstructs the data update map.
     * </p>
     *
     * @param buf The buffer to read from.
     * @return A new instance of the packet containing the decoded updates.
     * @throws IllegalStateException If decompression fails.
     */
    public static C2SSynchronizedDataBatchPacket decode(VxByteBuf buf) {
        try {
            int uncompressedSize = buf.readVarInt();
            byte[] compressedData = buf.readByteArray();

            // Decompress the data using the uncompressed size hint
            byte[] decompressedData = Zstd.decompress(compressedData, uncompressedSize);

            FriendlyByteBuf decompressedBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(decompressedData));
            int size = decompressedBuf.readVarInt();
            Map<Integer, byte[]> dataUpdates = new Int2ObjectArrayMap<>(size);

            for (int i = 0; i < size; i++) {
                int id = decompressedBuf.readVarInt();
                byte[] data = decompressedBuf.readByteArray();
                dataUpdates.put(id, data);
            }
            return new C2SSynchronizedDataBatchPacket(dataUpdates);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decompress C2S sync data batch packet", e);
        }
    }

    /**
     * Encodes the packet's data into the provided network buffer.
     * <p>
     * The updates are first written to a temporary buffer, then compressed using Zstd
     * to minimize bandwidth usage before being written to the final output buffer.
     * </p>
     *
     * @param buf The extended buffer to write data to.
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

            // Compress the serialized map
            byte[] compressedData = Zstd.compress(uncompressedData);
            buf.writeVarInt(uncompressedData.length);
            buf.writeByteArray(compressedData);
        } finally {
            tempBuf.release();
        }
    }

    /**
     * Handles the packet logic on the server side.
     * <p>
     * This method validates and applies the updates using the anti-cheat logic
     * provided by the server-side synchronization manager.
     * </p>
     *
     * @param context The network context (containing the player and server level).
     */
    @Override
    public void handle(NetworkManager.PacketContext context) {
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            VxPhysicsWorld world = VxPhysicsWorld.get(player.serverLevel().dimension());

            if (world == null) {
                return;
            }

            VxServerSyncManager syncManager = world.getBodyManager().getServerSyncManager();

            for (Map.Entry<Integer, byte[]> entry : this.dataUpdates.entrySet()) {
                // Delegate validation and application to the server sync manager
                syncManager.processClientUpdate(entry.getKey(), entry.getValue(), player);
            }
        });
    }
}