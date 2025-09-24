/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.packet.batch;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.client.VxClientObjectManager;
import net.xmx.velthoric.physics.object.packet.SpawnData;

import java.util.List;
import java.util.function.Supplier;

/**
 * A network packet that contains a batch of physics objects to be spawned on the client.
 * This is more efficient than sending a separate packet for each individual object.
 *
 * @author xI-Mx-Ix
 */
public class S2CSpawnBodyBatchPacket {

    private final List<SpawnData> spawnDataList;

    /**
     * Constructs a new batch packet with a list of objects to spawn.
     *
     * @param spawnDataList The list of {@link SpawnData} for each object.
     */
    public S2CSpawnBodyBatchPacket(List<SpawnData> spawnDataList) {
        this.spawnDataList = spawnDataList;
    }

    /**
     * Constructs the packet by deserializing data from a network buffer.
     *
     * @param buf The buffer to read from.
     */
    public S2CSpawnBodyBatchPacket(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.spawnDataList = new ObjectArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.spawnDataList.add(new SpawnData(buf));
        }
    }

    /**
     * Serializes the packet's data into a network buffer.
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
     * @param msg             The received packet.
     * @param contextSupplier A supplier for the network packet context.
     */
    public static void handle(S2CSpawnBodyBatchPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            VxClientObjectManager manager = VxClientObjectManager.getInstance();
            // Iterate through each spawn data object and spawn it on the client.
            for (SpawnData data : msg.spawnDataList) {
                // Wrap the raw byte data into a buffer for the manager to read.
                VxByteBuf dataBuf = new VxByteBuf(Unpooled.wrappedBuffer(data.data));
                try {
                    manager.spawnObject(data.id, data.typeIdentifier, data.objectType, dataBuf, data.timestamp);
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