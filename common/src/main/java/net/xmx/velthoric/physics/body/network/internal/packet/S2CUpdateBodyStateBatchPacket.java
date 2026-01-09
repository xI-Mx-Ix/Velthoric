/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.network.internal.packet;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.network.VxPacketUtils;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.client.VxClientBodyDataStore;
import net.xmx.velthoric.physics.world.VxClientPhysicsWorld;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * A network packet that synchronizes a compressed batch of physics body states.
 * <p>
 * <b>Optimization Note:</b> To minimize GC pressure on the server, this packet supports a
 * "Pre-Serialized" mode. When created on the server, it holds only the raw compressed {@code byte[]}
 * payload. When decoded on the client, it populates the Structure-of-Arrays (SoA) fields
 * for efficient processing.
 *
 * @author xI-Mx-Ix
 */
public class S2CUpdateBodyStateBatchPacket {

    // --- Server-Side Payload ---
    private final byte[] preCompressedData;
    private final int uncompressedSize;

    // --- Client-Side Decoded Data ---
    private int count;
    private long timestamp;
    private int[] networkIds;
    private double[] posX, posY, posZ;
    private float[] rotX, rotY, rotZ, rotW;
    private float[] velX, velY, velZ;
    private boolean[] isActive;

    /**
     * Server-Side Constructor.
     * Creates a packet wrapping pre-serialized and compressed data.
     * This avoids allocating the large SoA arrays on the server heap.
     *
     * @param uncompressedSize The size of the data before compression (needed for Zstd).
     * @param preCompressedData The compressed payload.
     */
    public S2CUpdateBodyStateBatchPacket(int uncompressedSize, byte[] preCompressedData) {
        this.uncompressedSize = uncompressedSize;
        this.preCompressedData = preCompressedData;
    }

    /**
     * Client-Side Constructor.
     * Used by the decoder to populate the object with usable data arrays.
     */
    public S2CUpdateBodyStateBatchPacket(int count, long timestamp, int[] networkIds, double[] posX, double[] posY, double[] posZ, float[] rotX, float[] rotY, float[] rotZ, float[] rotW, float[] velX, float[] velY, float[] velZ, boolean[] isActive) {
        this.uncompressedSize = 0;
        this.preCompressedData = null;
        this.count = count;
        this.timestamp = timestamp;
        this.networkIds = networkIds;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.rotX = rotX;
        this.rotY = rotY;
        this.rotZ = rotZ;
        this.rotW = rotW;
        this.velX = velX;
        this.velY = velY;
        this.velZ = velZ;
        this.isActive = isActive;
    }

    /**
     * Encodes the packet into the network stream.
     * Since the data is already serialized and compressed by the Dispatcher,
     * this simply writes the byte array.
     *
     * @param msg The packet instance.
     * @param buf The output buffer.
     */
    public static void encode(S2CUpdateBodyStateBatchPacket msg, FriendlyByteBuf buf) {
        if (msg.preCompressedData == null) {
            throw new IllegalStateException("Attempted to encode a client-side packet on the server, or packet data is missing.");
        }
        buf.writeVarInt(msg.uncompressedSize);
        buf.writeByteArray(msg.preCompressedData);
    }

    /**
     * Decodes the packet from the network stream.
     * Decompresses the data and reconstructs the SoA arrays for client consumption.
     *
     * @param buf The input buffer.
     * @return A new packet instance containing populated arrays.
     */
    public static S2CUpdateBodyStateBatchPacket decode(FriendlyByteBuf buf) {
        try {
            int uncompressedSize = buf.readVarInt();
            byte[] compressedData = buf.readByteArray();
            byte[] decompressedData = VxPacketUtils.decompress(compressedData, uncompressedSize);

            // Wrap the byte array to read primitives
            FriendlyByteBuf decompressedBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(decompressedData));

            // Read Header
            int count = decompressedBuf.readVarInt();
            long timestamp = decompressedBuf.readLong();
            double baseX = decompressedBuf.readDouble();
            double baseY = decompressedBuf.readDouble();
            double baseZ = decompressedBuf.readDouble();

            // Allocate Client Arrays
            int[] networkIds = new int[count];
            double[] posX = new double[count];
            double[] posY = new double[count];
            double[] posZ = new double[count];
            float[] rotX = new float[count];
            float[] rotY = new float[count];
            float[] rotZ = new float[count];
            float[] rotW = new float[count];
            float[] velX = new float[count];
            float[] velY = new float[count];
            float[] velZ = new float[count];
            boolean[] isActive = new boolean[count];

            // Read Body Data
            for (int i = 0; i < count; i++) {
                networkIds[i] = decompressedBuf.readVarInt();

                // Reconstruct absolute position
                posX[i] = baseX + decompressedBuf.readFloat();
                posY[i] = baseY + decompressedBuf.readFloat();
                posZ[i] = baseZ + decompressedBuf.readFloat();

                rotX[i] = decompressedBuf.readFloat();
                rotY[i] = decompressedBuf.readFloat();
                rotZ[i] = decompressedBuf.readFloat();
                rotW[i] = decompressedBuf.readFloat();

                isActive[i] = decompressedBuf.readBoolean();
                if (isActive[i]) {
                    velX[i] = decompressedBuf.readFloat();
                    velY[i] = decompressedBuf.readFloat();
                    velZ[i] = decompressedBuf.readFloat();
                }
            }
            decompressedBuf.release();

            return new S2CUpdateBodyStateBatchPacket(count, timestamp, networkIds, posX, posY, posZ, rotX, rotY, rotZ, rotW, velX, velY, velZ, isActive);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to decompress body state batch packet", e);
        }
    }

    /**
     * Handles the packet on the client side.
     * Applies the updates to the client body manager.
     *
     * @param msg             The received packet.
     * @param contextSupplier A supplier for the network context.
     */
    public static void handle(S2CUpdateBodyStateBatchPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            VxClientBodyManager manager = VxClientPhysicsWorld.getInstance().getBodyManager();
            VxClientBodyDataStore store = manager.getStore();
            long clientReceiptTime = manager.getClock().getGameTimeNanos();

            // Use the single packet timestamp to calculate the clock offset once.
            // This is statistically more stable than calculating it per body.
            manager.addClockSyncSample(msg.timestamp - clientReceiptTime);

            for (int i = 0; i < msg.count; i++) {
                Integer index = store.getIndexForNetworkId(msg.networkIds[i]);
                if (index == null) continue;

                // Cycle states for interpolation
                store.state0_timestamp[index] = store.state1_timestamp[index];
                store.state0_posX[index] = store.state1_posX[index];
                store.state0_posY[index] = store.state1_posY[index];
                store.state0_posZ[index] = store.state1_posZ[index];
                store.state0_rotX[index] = store.state1_rotX[index];
                store.state0_rotY[index] = store.state1_rotY[index];
                store.state0_rotZ[index] = store.state1_rotZ[index];
                store.state0_rotW[index] = store.state1_rotW[index];
                store.state0_velX[index] = store.state1_velX[index];
                store.state0_velY[index] = store.state1_velY[index];
                store.state0_velZ[index] = store.state1_velZ[index];
                store.state0_isActive[index] = store.state1_isActive[index];
                store.state0_vertexData[index] = store.state1_vertexData[index];

                // Update latest state
                store.state1_timestamp[index] = msg.timestamp;
                store.state1_posX[index] = msg.posX[i];
                store.state1_posY[index] = msg.posY[i];
                store.state1_posZ[index] = msg.posZ[i];
                store.state1_rotX[index] = msg.rotX[i];
                store.state1_rotY[index] = msg.rotY[i];
                store.state1_rotZ[index] = msg.rotZ[i];
                store.state1_rotW[index] = msg.rotW[i];
                store.state1_isActive[index] = msg.isActive[i];

                if (msg.isActive[i]) {
                    store.state1_velX[index] = msg.velX[i];
                    store.state1_velY[index] = msg.velY[i];
                    store.state1_velZ[index] = msg.velZ[i];
                } else {
                    store.state1_velX[index] = 0.0f;
                    store.state1_velY[index] = 0.0f;
                    store.state1_velZ[index] = 0.0f;
                }

                // Update culling position
                if (store.lastKnownPosition[index] == null) {
                    store.lastKnownPosition[index] = new com.github.stephengold.joltjni.RVec3();
                }
                store.lastKnownPosition[index].set(msg.posX[i], msg.posY[i], msg.posZ[i]);
            }
        });
    }
}