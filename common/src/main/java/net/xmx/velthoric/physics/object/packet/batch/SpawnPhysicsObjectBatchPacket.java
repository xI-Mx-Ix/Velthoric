/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.packet.batch;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.packet.SpawnData;

import java.util.List;
import java.util.function.Supplier;

/**
 * A network packet that contains a batch of {@link SpawnData} for new physics objects
 * to be created on the client. Batching reduces network overhead.
 *
 * @author xI-Mx-Ix
 */
public class SpawnPhysicsObjectBatchPacket {

    // The list of spawn data objects.
    private final List<SpawnData> spawnDataList;

    /**
     * Constructs a new packet with a list of spawn data to send.
     *
     * @param spawnDataList The list of {@link SpawnData}.
     */
    public SpawnPhysicsObjectBatchPacket(List<SpawnData> spawnDataList) {
        this.spawnDataList = spawnDataList;
    }

    /**
     * Constructs a packet by decoding it from a network buffer.
     *
     * @param buf The buffer to read from.
     */
    public SpawnPhysicsObjectBatchPacket(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.spawnDataList = new ObjectArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.spawnDataList.add(new SpawnData(buf));
        }
    }

    /**
     * Encodes the packet's data into a network buffer for sending.
     *
     * @param buf The buffer to write to.
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(spawnDataList.size());
        for (SpawnData data : spawnDataList) {
            data.encode(buf);
        }
    }

    /**
     * Handles the packet on the client side.
     *
     * @param msg            The received packet message.
     * @param contextSupplier A supplier for the network context.
     */
    public static void handle(SpawnPhysicsObjectBatchPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            // Executed on the client thread.
            VxClientObjectManager manager = VxClientObjectManager.getInstance();
            for (SpawnData data : msg.spawnDataList) {
                // Wrap the raw byte data in a buffer for the manager to read.
                FriendlyByteBuf dataBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data.data));
                try {
                    manager.spawnObject(data.id, data.typeIdentifier, data.objectType, dataBuf, data.timestamp);
                } finally {
                    // Ensure the temporary buffer is released.
                    if (dataBuf.refCnt() > 0) {
                        dataBuf.release();
                    }
                }
            }
        });
    }
}