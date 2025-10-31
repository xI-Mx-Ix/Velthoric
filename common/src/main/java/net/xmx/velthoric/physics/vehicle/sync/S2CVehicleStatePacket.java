/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.sync;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.physics.body.client.VxClientBodyDataStore;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.vehicle.VxVehicle;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * A server-to-client packet that synchronizes the dynamic state of a vehicle.
 * This includes its speed, and for wheeled vehicles, the state of each wheel
 * (rotation, steering angle, suspension length). This packet uses an integer network ID
 * for efficient identification.
 *
 * @author xI-Mx-Ix
 */
public class S2CVehicleStatePacket {

    private final int networkId;
    private final float speedKmh;
    private final float[] rotationAngles;
    private final float[] steerAngles;
    private final float[] suspensionLengths;

    /**
     * Constructs the packet from raw data. Used on the sending side and by the decode method.
     */
    public S2CVehicleStatePacket(int networkId, float speedKmh, float[] rotationAngles, float[] steerAngles, float[] suspensionLengths) {
        this.networkId = networkId;
        this.speedKmh = speedKmh;
        this.rotationAngles = rotationAngles;
        this.steerAngles = steerAngles;
        this.suspensionLengths = suspensionLengths;
    }

    /**
     * Encodes the packet data into a network buffer for sending.
     * @param msg The packet instance to encode.
     * @param buf The buffer to write to.
     */
    public static void encode(S2CVehicleStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.networkId);
        buf.writeFloat(msg.speedKmh);
        buf.writeVarInt(msg.rotationAngles.length);
        for (int i = 0; i < msg.rotationAngles.length; i++) {
            buf.writeFloat(msg.rotationAngles[i]);
            buf.writeFloat(msg.steerAngles[i]);
            buf.writeFloat(msg.suspensionLengths[i]);
        }
    }

    /**
     * Decodes the packet from a network buffer.
     * @param buf The network buffer to read from.
     * @return A new instance of the packet.
     */
    public static S2CVehicleStatePacket decode(FriendlyByteBuf buf) {
        int networkId = buf.readVarInt();
        float speedKmh = buf.readFloat();
        int wheelCount = buf.readVarInt();
        float[] rotationAngles = new float[wheelCount];
        float[] steerAngles = new float[wheelCount];
        float[] suspensionLengths = new float[wheelCount];
        for (int i = 0; i < wheelCount; i++) {
            rotationAngles[i] = buf.readFloat();
            steerAngles[i] = buf.readFloat();
            suspensionLengths[i] = buf.readFloat();
        }
        return new S2CVehicleStatePacket(networkId, speedKmh, rotationAngles, steerAngles, suspensionLengths);
    }

    /**
     * Handles the packet on the client side.
     *
     * @param msg The received packet.
     * @param contextSupplier A supplier for the network context.
     */
    public static void handle(S2CVehicleStatePacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            VxClientBodyManager manager = VxClientBodyManager.getInstance();
            VxClientBodyDataStore store = manager.getStore();

            // Look up the body's internal index using the efficient network ID.
            Integer index = store.getIndexForNetworkId(msg.networkId);
            if (index == null) return;

            // Get the persistent UUID from the index to find the body instance.
            UUID vehicleUuid = store.getUuidForIndex(index);
            if (vehicleUuid == null) return;

            VxBody body = manager.getBody(vehicleUuid);

            if (body instanceof VxVehicle vehicle) {
                // Update the vehicle's overall state
                vehicle.updateVehicleState(msg.speedKmh);

                // Update the state of each wheel for rendering interpolation
                for (int i = 0; i < msg.rotationAngles.length; i++) {
                    vehicle.updateWheelState(i, msg.rotationAngles[i], msg.steerAngles[i], msg.suspensionLengths[i]);
                }
            }
        });
    }
}