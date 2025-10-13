/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.packet.batch;

import com.github.stephengold.joltjni.RVec3;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.client.VxClientBodyDataStore;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * A network packet that synchronizes a batch of physics body states using a Structure of Arrays (SoA) layout.
 * This is highly efficient as it packs related data together, improving cache locality and reducing overhead
 * compared to sending an array of state objects (AoS).
 *
 * @author xI-Mx-Ix
 */
public class S2CUpdateBodyStateBatchPacket {

    private final int count;
    private final UUID[] ids;
    private final long[] timestamps;
    private final double[] posX, posY, posZ;
    private final float[] rotX, rotY, rotZ, rotW;
    private final float[] velX, velY, velZ;
    private final boolean[] isActive;

    /**
     * Constructs the packet from raw data arrays. The provided arrays may be larger than
     * {@code count}; only the first {@code count} elements will be used.
     *
     * @param count      The number of valid bodies in this batch.
     * @param ids        Array of body UUIDs.
     * @param timestamps Array of server-side timestamps for each state.
     * @param posX       Array of X positions.
     * @param posY       Array of Y positions.
     * @param posZ       Array of Z positions.
     * @param rotX       Array of X rotation components.
     * @param rotY       Array of Y rotation components.
     * @param rotZ       Array of Z rotation components.
     * @param rotW       Array of W rotation components.
     * @param velX       Array of X linear velocities.
     * @param velY       Array of Y linear velocities.
     * @param velZ       Array of Z linear velocities.
     * @param isActive   Array of active states.
     */
    public S2CUpdateBodyStateBatchPacket(int count, UUID[] ids, long[] timestamps, double[] posX, double[] posY, double[] posZ, float[] rotX, float[] rotY, float[] rotZ, float[] rotW, float[] velX, float[] velY, float[] velZ, boolean[] isActive) {
        this.count = count;
        this.ids = ids;
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
     * Constructs the packet by decoding it from a network buffer.
     *
     * @param buf The buffer to read from.
     */
    public S2CUpdateBodyStateBatchPacket(FriendlyByteBuf buf) {
        this.count = buf.readVarInt();
        this.ids = new UUID[count];
        this.timestamps = new long[count];
        this.posX = new double[count];
        this.posY = new double[count];
        this.posZ = new double[count];
        this.rotX = new float[count];
        this.rotY = new float[count];
        this.rotZ = new float[count];
        this.rotW = new float[count];
        this.velX = new float[count];
        this.velY = new float[count];
        this.velZ = new float[count];
        this.isActive = new boolean[count];

        for (int i = 0; i < count; i++) {
            ids[i] = buf.readUUID();
            timestamps[i] = buf.readLong();
            posX[i] = buf.readDouble();
            posY[i] = buf.readDouble();
            posZ[i] = buf.readDouble();
            rotX[i] = buf.readFloat();
            rotY[i] = buf.readFloat();
            rotZ[i] = buf.readFloat();
            rotW[i] = buf.readFloat();
            isActive[i] = buf.readBoolean();
            if (isActive[i]) {
                velX[i] = buf.readFloat();
                velY[i] = buf.readFloat();
                velZ[i] = buf.readFloat();
            }
        }
    }

    /**
     * Encodes the packet's data into a network buffer for sending.
     * It only writes the first {@code count} elements from the internal arrays.
     *
     * @param buf The buffer to write to.
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            // This now safely handles the case where the ids[i] could be null
            // if an error happened during packet construction, although the dispatcher
            // now prevents this. A defensive check is good practice.
            UUID uuid = ids[i];
            if (uuid == null) {
                // This should not happen with the new dispatcher logic, but as a fallback,
                // write a sentinel UUID to prevent crashing the client. The client should ignore it.
                buf.writeUUID(new UUID(0, 0));
            } else {
                buf.writeUUID(uuid);
            }
            buf.writeLong(timestamps[i]);
            buf.writeDouble(posX[i]);
            buf.writeDouble(posY[i]);
            buf.writeDouble(posZ[i]);
            buf.writeFloat(rotX[i]);
            buf.writeFloat(rotY[i]);
            buf.writeFloat(rotZ[i]);
            buf.writeFloat(rotW[i]);
            buf.writeBoolean(isActive[i]);
            if (isActive[i]) {
                buf.writeFloat(velX[i]);
                buf.writeFloat(velY[i]);
                buf.writeFloat(velZ[i]);
            }
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
                // Ignore sentinel UUIDs from potential encoding fallbacks.
                if (msg.ids[i].getMostSignificantBits() == 0 && msg.ids[i].getLeastSignificantBits() == 0) {
                    continue;
                }

                Integer index = store.getIndexForId(msg.ids[i]);
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