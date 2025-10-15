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
 * A server-to-client packet that synchronizes the dynamic state of a vehicle.
 * This includes its speed, and for wheeled vehicles, the state of each wheel
 * (rotation, steering angle, suspension length).
 * It is sent periodically for vehicles whose state has been updated.
 *
 * @author xI-Mx-Ix
 */
public class S2CVehicleStatePacket {

    private final UUID vehicleId;
    private final float speedKmh;
    private final float[] rotationAngles;
    private final float[] steerAngles;
    private final float[] suspensionLengths;

    /**
     * Constructor used on the server to create the packet.
     * @param vehicleId The UUID of the vehicle being updated.
     * @param speedKmh The current speed of the vehicle in km/h.
     * @param wheelCount The number of wheels on the vehicle.
     * @param rotationAngles Array of rotation angles for each wheel.
     * @param steerAngles Array of steering angles for each wheel.
     * @param suspensionLengths Array of suspension lengths for each wheel.
     */
    public S2CVehicleStatePacket(UUID vehicleId, float speedKmh, int wheelCount, float[] rotationAngles, float[] steerAngles, float[] suspensionLengths) {
        this.vehicleId = vehicleId;
        this.speedKmh = speedKmh;
        // Create copies to ensure the data is immutable once the packet is created.
        this.rotationAngles = new float[wheelCount];
        this.steerAngles = new float[wheelCount];
        this.suspensionLengths = new float[wheelCount];
        if (wheelCount > 0) {
            System.arraycopy(rotationAngles, 0, this.rotationAngles, 0, wheelCount);
            System.arraycopy(steerAngles, 0, this.steerAngles, 0, wheelCount);
            System.arraycopy(suspensionLengths, 0, this.suspensionLengths, 0, wheelCount);
        }
    }

    /**
     * Constructor used on the client to decode the packet from a buffer.
     * @param buf The network buffer to read from.
     */
    public S2CVehicleStatePacket(FriendlyByteBuf buf) {
        this.vehicleId = buf.readUUID();
        this.speedKmh = buf.readFloat();
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
        buf.writeFloat(speedKmh);
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
    public static void handle(S2CVehicleStatePacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            VxClientBodyManager manager = VxClientBodyManager.getInstance();
            VxBody body = manager.getBody(msg.vehicleId);

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