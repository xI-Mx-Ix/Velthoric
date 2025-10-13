/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.sync;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.vehicle.VxVehicle;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * A server-to-client packet that synchronizes the dynamic state of a vehicle's wheels.
 * This includes rotation, steering angle, and suspension length for each wheel.
 * It is sent periodically for vehicles whose wheels have updated state.
 *
 * @author xI-Mx-Ix
 */
public class S2CUpdateWheelsPacket {

    private final UUID vehicleId;
    private final float[] rotationAngles;
    private final float[] steerAngles;
    private final float[] suspensionLengths;

    /**
     * Constructor used on the server to create the packet.
     * @param vehicleId The UUID of the vehicle being updated.
     * @param wheelCount The number of wheels on the vehicle.
     * @param rotationAngles Array of rotation angles for each wheel.
     * @param steerAngles Array of steering angles for each wheel.
     * @param suspensionLengths Array of suspension lengths for each wheel.
     */
    public S2CUpdateWheelsPacket(UUID vehicleId, int wheelCount, float[] rotationAngles, float[] steerAngles, float[] suspensionLengths) {
        this.vehicleId = vehicleId;
        // Create copies to ensure the data is immutable once the packet is created.
        this.rotationAngles = new float[wheelCount];
        this.steerAngles = new float[wheelCount];
        this.suspensionLengths = new float[wheelCount];
        System.arraycopy(rotationAngles, 0, this.rotationAngles, 0, wheelCount);
        System.arraycopy(steerAngles, 0, this.steerAngles, 0, wheelCount);
        System.arraycopy(suspensionLengths, 0, this.suspensionLengths, 0, wheelCount);
    }

    /**
     * Constructor used on the client to decode the packet from a buffer.
     * @param buf The network buffer to read from.
     */
    public S2CUpdateWheelsPacket(FriendlyByteBuf buf) {
        this.vehicleId = buf.readUUID();
        int wheelCount = buf.readVarInt();
        this.rotationAngles = new float[wheelCount];
        this.steerAngles = new float[wheelCount];
        this.suspensionLengths = new float[wheelCount];
        for (int i = 0; i < wheelCount; i++) {
            this.rotationAngles[i] = buf.readFloat();
            this.steerAngles[i] = buf.readFloat();
            this.suspensionLengths[i] = buf.readFloat();
        }
    }

    /**
     * Encodes the packet data into a network buffer for sending.
     * @param buf The buffer to write to.
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(vehicleId);
        buf.writeVarInt(rotationAngles.length);
        for (int i = 0; i < rotationAngles.length; i++) {
            buf.writeFloat(rotationAngles[i]);
            buf.writeFloat(steerAngles[i]);
            buf.writeFloat(suspensionLengths[i]);
        }
    }

    /**
     * Handles the packet on the client side.
     * This is executed on the client's network thread and queues the main logic
     * to run on the client's main thread to ensure thread safety.
     *
     * @param msg The received packet.
     * @param contextSupplier A supplier for the network context.
     */
    public static void handle(S2CUpdateWheelsPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            VxClientBodyManager manager = VxClientBodyManager.getInstance();
            VxBody body = manager.getBody(msg.vehicleId);

            if (body instanceof VxVehicle vehicle) {
                for (int i = 0; i < msg.rotationAngles.length; i++) {
                    vehicle.updateWheelState(i, msg.rotationAngles[i], msg.steerAngles[i], msg.suspensionLengths[i]);
                }
            }
        });
    }
}