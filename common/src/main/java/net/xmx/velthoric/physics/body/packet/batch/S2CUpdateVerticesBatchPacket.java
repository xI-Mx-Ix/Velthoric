/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.packet.batch;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.client.VxClientBodyDataStore;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * A network packet that synchronizes a batch of soft body vertex data.
 * This is sent separately from the main body state to allow for different update rates
 * and to keep the main state packet smaller and faster.
 *
 * @author xI-Mx-Ix
 */
public class S2CUpdateVerticesBatchPacket {

    private final int count;
    private final UUID[] ids;
    private final float[][] vertexData;

    /**
     * Constructs the packet from raw data arrays.
     *
     * @param count      The number of objects in this batch.
     * @param ids        Array of soft body UUIDs.
     * @param vertexData A 2D array containing the vertex data for each body.
     */
    public S2CUpdateVerticesBatchPacket(int count, UUID[] ids, float[] @Nullable [] vertexData) {
        this.count = count;
        this.ids = ids;
        this.vertexData = vertexData;
    }

    /**
     * Constructs the packet by decoding it from a network buffer.
     *
     * @param buf The buffer to read from.
     */
    public S2CUpdateVerticesBatchPacket(FriendlyByteBuf buf) {
        this.count = buf.readVarInt();
        this.ids = new UUID[count];
        this.vertexData = new float[count][];

        for (int i = 0; i < count; i++) {
            ids[i] = buf.readUUID();
            if (buf.readBoolean()) { // Check if vertex data is present for this body
                int length = buf.readVarInt();
                vertexData[i] = new float[length];
                for (int j = 0; j < length; j++) {
                    vertexData[i][j] = buf.readFloat();
                }
            } else {
                vertexData[i] = null;
            }
        }
    }

    /**
     * Encodes the packet's data into a network buffer for sending.
     *
     * @param buf The buffer to write to.
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeUUID(ids[i]);
            float[] vertices = vertexData[i];
            if (vertices != null && vertices.length > 0) {
                buf.writeBoolean(true);
                buf.writeVarInt(vertices.length);
                for (float v : vertices) {
                    buf.writeFloat(v);
                }
            } else {
                buf.writeBoolean(false);
            }
        }
    }

    /**
     * Handles the packet on the client side, applying the vertex data updates.
     *
     * @param msg             The received packet.
     * @param contextSupplier A supplier for the network context.
     */
    public static void handle(S2CUpdateVerticesBatchPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            VxClientBodyManager manager = VxClientBodyManager.getInstance();
            VxClientBodyDataStore store = manager.getStore();

            for (int i = 0; i < msg.count; i++) {
                Integer index = store.getIndexForId(msg.ids[i]);
                if (index == null) continue; // Bodies might have been removed.

                // This directly updates the target vertex data in state1.
                // The interpolation logic will handle the transition smoothly.
                store.state1_vertexData[index] = msg.vertexData[i];
            }
        });
    }
}