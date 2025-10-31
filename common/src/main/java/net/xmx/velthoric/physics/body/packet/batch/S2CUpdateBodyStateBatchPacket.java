/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.packet.batch;

import com.github.stephengold.joltjni.RVec3;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.network.VxPacketUtils;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.client.VxClientBodyDataStore;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * A network packet that synchronizes a compressed batch of physics body states using a Structure of Arrays (SoA) layout.
 * This is highly efficient as it packs related data together, improving cache locality and reducing overhead
 * compared to sending an array of state objects (AoS). The payload is compressed to further reduce bandwidth.
 *
 * @author xI-Mx-Ix
 */
public class S2CUpdateBodyStateBatchPacket {

    private final int count;
    private final int[] networkIds;
    private final long[] timestamps;
    private final double[] posX, posY, posZ;
    private final float[] rotX, rotY, rotZ, rotW;
    private final float[] velX, velY, velZ;
    private final boolean[] isActive;

    /**
     * Constructs the packet from raw data arrays. This is used on the sending side
     * and by the decode method after data has been read from the buffer.
     */
    public S2CUpdateBodyStateBatchPacket(int count, int[] networkIds, long[] timestamps, double[] posX, double[] posY, double[] posZ, float[] rotX, float[] rotY, float[] rotZ, float[] rotW, float[] velX, float[] velY, float[] velZ, boolean[] isActive) {
        this.count = count;
        this.networkIds = networkIds;
        this.timestamps = timestamps;
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
     * Encodes the packet's data into a network buffer for sending.
     * It only writes the first {@code count} elements from the internal arrays.
     *
     * @param msg The packet instance to encode.
     * @param buf The buffer to write to.
     */
    public static void encode(S2CUpdateBodyStateBatchPacket msg, FriendlyByteBuf buf) {
        FriendlyByteBuf tempBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            tempBuf.writeVarInt(msg.count);
            for (int i = 0; i < msg.count; i++) {
                tempBuf.writeVarInt(msg.networkIds[i]);
                tempBuf.writeLong(msg.timestamps[i]);
                tempBuf.writeDouble(msg.posX[i]);
                tempBuf.writeDouble(msg.posY[i]);
                tempBuf.writeDouble(msg.posZ[i]);
                tempBuf.writeFloat(msg.rotX[i]);
                tempBuf.writeFloat(msg.rotY[i]);
                tempBuf.writeFloat(msg.rotZ[i]);
                tempBuf.writeFloat(msg.rotW[i]);
                tempBuf.writeBoolean(msg.isActive[i]);
                if (msg.isActive[i]) {
                    tempBuf.writeFloat(msg.velX[i]);
                    tempBuf.writeFloat(msg.velY[i]);
                    tempBuf.writeFloat(msg.velZ[i]);
                }
            }

            byte[] uncompressedData = new byte[tempBuf.readableBytes()];
            tempBuf.readBytes(uncompressedData);
            byte[] compressedData = VxPacketUtils.compress(uncompressedData);

            buf.writeVarInt(uncompressedData.length);
            buf.writeByteArray(compressedData);

        } catch (IOException e) {
            throw new IllegalStateException("Failed to compress body state batch packet", e);
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
    public static S2CUpdateBodyStateBatchPacket decode(FriendlyByteBuf buf) {
        try {
            int uncompressedSize = buf.readVarInt();
            byte[] compressedData = buf.readByteArray();
            byte[] decompressedData = VxPacketUtils.decompress(compressedData, uncompressedSize);
            FriendlyByteBuf decompressedBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(decompressedData));

            int count = decompressedBuf.readVarInt();
            int[] networkIds = new int[count];
            long[] timestamps = new long[count];
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

            for (int i = 0; i < count; i++) {
                networkIds[i] = decompressedBuf.readVarInt();
                timestamps[i] = decompressedBuf.readLong();
                posX[i] = decompressedBuf.readDouble();
                posY[i] = decompressedBuf.readDouble();
                posZ[i] = decompressedBuf.readDouble();
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

            return new S2CUpdateBodyStateBatchPacket(count, networkIds, timestamps, posX, posY, posZ, rotX, rotY, rotZ, rotW, velX, velY, velZ, isActive);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to decompress body state batch packet", e);
        }
    }

    /**
     * Handles the packet on the client side, efficiently applying the SoA updates.
     *
     * @param msg             The received packet.
     * @param contextSupplier A supplier for the network context.
     */
    public static void handle(S2CUpdateBodyStateBatchPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            VxClientBodyManager manager = VxClientBodyManager.getInstance();
            VxClientBodyDataStore store = manager.getStore();
            long clientReceiptTime = manager.getClock().getGameTimeNanos();

            for (int i = 0; i < msg.count; i++) {
                Integer index = store.getIndexForNetworkId(msg.networkIds[i]);
                if (index == null) continue; // Body might have been removed client-side.

                // Add a clock sync sample for each received state.
                manager.addClockSyncSample(msg.timestamps[i] - clientReceiptTime);

                // Shift state1 (the previous target state) to state0 (the new source state).
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
                store.state0_vertexData[index] = store.state1_vertexData[index]; // Shift vertex data as well.

                // Apply the new state data from the packet directly into state1 (the new target state).
                store.state1_timestamp[index] = msg.timestamps[i];
                store.state1_posX[index] = (float) msg.posX[i];
                store.state1_posY[index] = (float) msg.posY[i];
                store.state1_posZ[index] = (float) msg.posZ[i];
                store.state1_rotX[index] = msg.rotX[i];
                store.state1_rotY[index] = msg.rotY[i];
                store.state1_rotZ[index] = msg.rotZ[i];
                store.state1_rotW[index] = msg.rotW[i];
                store.state1_isActive[index] = msg.isActive[i];

                if (store.state1_isActive[index]) {
                    store.state1_velX[index] = msg.velX[i];
                    store.state1_velY[index] = msg.velY[i];
                    store.state1_velZ[index] = msg.velZ[i];
                } else {
                    // If the body is inactive, its velocity is zero.
                    store.state1_velX[index] = 0.0f;
                    store.state1_velY[index] = 0.0f;
                    store.state1_velZ[index] = 0.0f;
                }

                // Update the last known position for culling purposes.
                if (store.lastKnownPosition[index] == null) {
                    store.lastKnownPosition[index] = new RVec3();
                }
                store.lastKnownPosition[index].set((float) msg.posX[i], (float) msg.posY[i], (float) msg.posZ[i]);
            }
        });
    }
}