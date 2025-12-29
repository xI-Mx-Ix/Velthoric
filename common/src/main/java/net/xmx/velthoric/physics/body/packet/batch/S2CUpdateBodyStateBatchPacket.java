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
 * <p>
 * Optimizations implemented:
 * <ul>
 *     <li><b>Global Timestamp:</b> The simulation timestamp is sent once per packet header, assuming all bodies in the batch belong to the same simulation tick.</li>
 *     <li><b>Relative Positioning:</b> A high-precision 'base' position (anchor) is sent in the header. Individual body positions are sent as floats relative to this anchor. This retains {@code double} precision locally while reducing network bandwidth by using {@code float} (12 bytes vs 24 bytes per body).</li>
 *     <li><b>Zstd Compression:</b> The entire payload is compressed to minimize bandwidth usage.</li>
 * </ul>
 *
 * @author xI-Mx-Ix
 */
public class S2CUpdateBodyStateBatchPacket {

    private final int count;
    private final long timestamp;
    private final int[] networkIds;
    // Stored as doubles internally to maintain compatibility with the ClientDataStore
    private final double[] posX, posY, posZ;
    private final float[] rotX, rotY, rotZ, rotW;
    private final float[] velX, velY, velZ;
    private final boolean[] isActive;

    /**
     * Constructs the packet with absolute positions and a single global timestamp.
     * The encoding process will handle the conversion to relative positions.
     *
     * @param count      The number of bodies in this batch.
     * @param timestamp  The server-side simulation timestamp for this tick.
     * @param networkIds Array of network IDs.
     * @param posX       Array of absolute X positions.
     * @param posY       Array of absolute Y positions.
     * @param posZ       Array of absolute Z positions.
     * @param rotX       Array of rotation X components (quaternion).
     * @param rotY       Array of rotation Y components (quaternion).
     * @param rotZ       Array of rotation Z components (quaternion).
     * @param rotW       Array of rotation W components (quaternion).
     * @param velX       Array of linear velocity X components.
     * @param velY       Array of linear velocity Y components.
     * @param velZ       Array of linear velocity Z components.
     * @param isActive   Array indicating if the body is currently active (awake).
     */
    public S2CUpdateBodyStateBatchPacket(int count, long timestamp, int[] networkIds, double[] posX, double[] posY, double[] posZ, float[] rotX, float[] rotY, float[] rotZ, float[] rotW, float[] velX, float[] velY, float[] velZ, boolean[] isActive) {
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
     * Encodes the packet's data into a network buffer for sending.
     * Applies relative position encoding and Zstd compression.
     *
     * @param msg The packet instance to encode.
     * @param buf The buffer to write to.
     */
    public static void encode(S2CUpdateBodyStateBatchPacket msg, FriendlyByteBuf buf) {
        FriendlyByteBuf tempBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            // Write Header
            tempBuf.writeVarInt(msg.count);
            tempBuf.writeLong(msg.timestamp);

            // Determine the anchor point (base position) for relative encoding.
            // Using the position of the first body is usually sufficient to bring offsets into float range.
            double baseX = msg.count > 0 ? msg.posX[0] : 0.0;
            double baseY = msg.count > 0 ? msg.posY[0] : 0.0;
            double baseZ = msg.count > 0 ? msg.posZ[0] : 0.0;

            tempBuf.writeDouble(baseX);
            tempBuf.writeDouble(baseY);
            tempBuf.writeDouble(baseZ);

            // Write Body Data
            for (int i = 0; i < msg.count; i++) {
                tempBuf.writeVarInt(msg.networkIds[i]);

                // Calculate and write relative positions cast to float.
                // This maintains precision near the anchor point.
                tempBuf.writeFloat((float) (msg.posX[i] - baseX));
                tempBuf.writeFloat((float) (msg.posY[i] - baseY));
                tempBuf.writeFloat((float) (msg.posZ[i] - baseZ));

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

            // Compress payload
            byte[] uncompressedData = new byte[tempBuf.readableBytes()];
            tempBuf.readBytes(uncompressedData);
            byte[] compressedData = VxPacketUtils.compress(uncompressedData);

            // Write final packet structure
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
     * Reconstructs absolute positions from the base anchor and relative offsets.
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

            // Read Header
            int count = decompressedBuf.readVarInt();
            long timestamp = decompressedBuf.readLong();
            double baseX = decompressedBuf.readDouble();
            double baseY = decompressedBuf.readDouble();
            double baseZ = decompressedBuf.readDouble();

            // Allocate Arrays
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

                // Reconstruct absolute double position: Base (double) + Offset (float)
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

            // Use the single packet timestamp to calculate the clock offset once.
            // This is statistically more stable than calculating it per body.
            manager.addClockSyncSample(msg.timestamp - clientReceiptTime);

            for (int i = 0; i < msg.count; i++) {
                Integer index = store.getIndexForNetworkId(msg.networkIds[i]);
                if (index == null) continue; // Body might have been removed client-side.

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
                store.state0_vertexData[index] = store.state1_vertexData[index];

                // Apply the new state data from the packet directly into state1.
                // The position arrays in 'msg' are already reconstructed as absolute doubles.
                store.state1_timestamp[index] = msg.timestamp;
                store.state1_posX[index] = msg.posX[i];
                store.state1_posY[index] = msg.posY[i];
                store.state1_posZ[index] = msg.posZ[i];
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
                    store.state1_velX[index] = 0.0f;
                    store.state1_velY[index] = 0.0f;
                    store.state1_velZ[index] = 0.0f;
                }

                // Update the last known position for culling purposes.
                if (store.lastKnownPosition[index] == null) {
                    store.lastKnownPosition[index] = new RVec3();
                }
                store.lastKnownPosition[index].set(msg.posX[i], msg.posY[i], msg.posZ[i]);
            }
        });
    }
}