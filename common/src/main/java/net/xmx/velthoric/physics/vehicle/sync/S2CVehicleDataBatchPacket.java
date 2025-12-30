/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.sync;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.physics.body.client.VxClientBodyManager;
import net.xmx.velthoric.physics.body.client.VxClientBodyDataStore;
import net.xmx.velthoric.physics.vehicle.VxVehicle;
import net.xmx.velthoric.physics.world.VxClientPhysicsWorld;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * A highly optimized batch packet that synchronizes multiple vehicles at once.
 * It uses a Structure-of-Arrays (SoA) layout within the packet to minimize overhead.
 *
 * @author xI-Mx-Ix
 */
public class S2CVehicleDataBatchPacket {

    private final int count;
    private final int[] networkIds;
    private final float[] speeds;
    private final float[] rpms;
    private final int[] gears;
    private final float[] throttles;
    private final float[] steers;

    // Flattened wheel data arrays.
    // Indexing: vehicleIndex * maxWheels + wheelIndex
    private final float[] wheelRotations;
    private final float[] wheelSteers;
    private final float[] wheelSuspensions;
    private final int[] wheelCounts; // Tracks how many wheels each vehicle has

    /**
     * Constructs a batch packet.
     * Arrays are expected to be sized correctly by the Dispatcher.
     */
    public S2CVehicleDataBatchPacket(int count, int[] networkIds, float[] speeds, float[] rpms, int[] gears, float[] throttles, float[] steers, int[] wheelCounts, float[] wheelRotations, float[] wheelSteers, float[] wheelSuspensions) {
        this.count = count;
        this.networkIds = networkIds;
        this.speeds = speeds;
        this.rpms = rpms;
        this.gears = gears;
        this.throttles = throttles;
        this.steers = steers;
        this.wheelCounts = wheelCounts;
        this.wheelRotations = wheelRotations;
        this.wheelSteers = wheelSteers;
        this.wheelSuspensions = wheelSuspensions;
    }

    public static void encode(S2CVehicleDataBatchPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.count);
        for (int i = 0; i < msg.count; i++) {
            buf.writeVarInt(msg.networkIds[i]);
            buf.writeFloat(msg.speeds[i]);
            buf.writeFloat(msg.rpms[i]);
            buf.writeVarInt(msg.gears[i]);
            buf.writeFloat(msg.throttles[i]);
            buf.writeFloat(msg.steers[i]);
            buf.writeVarInt(msg.wheelCounts[i]);
        }
        // Write flattened wheel data blocks
        buf.writeVarInt(msg.wheelRotations.length);
        for (int i = 0; i < msg.wheelRotations.length; i++) {
            buf.writeFloat(msg.wheelRotations[i]);
            buf.writeFloat(msg.wheelSteers[i]);
            buf.writeFloat(msg.wheelSuspensions[i]);
        }
    }

    public static S2CVehicleDataBatchPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        int[] netIds = new int[count];
        float[] speeds = new float[count];
        float[] rpms = new float[count];
        int[] gears = new int[count];
        float[] throttles = new float[count];
        float[] steers = new float[count];
        int[] wheelCounts = new int[count];

        for (int i = 0; i < count; i++) {
            netIds[i] = buf.readVarInt();
            speeds[i] = buf.readFloat();
            rpms[i] = buf.readFloat();
            gears[i] = buf.readVarInt();
            throttles[i] = buf.readFloat();
            steers[i] = buf.readFloat();
            wheelCounts[i] = buf.readVarInt();
        }

        int totalWheelData = buf.readVarInt();
        float[] wRot = new float[totalWheelData];
        float[] wSteer = new float[totalWheelData];
        float[] wSusp = new float[totalWheelData];

        for (int i = 0; i < totalWheelData; i++) {
            wRot[i] = buf.readFloat();
            wSteer[i] = buf.readFloat();
            wSusp[i] = buf.readFloat();
        }

        return new S2CVehicleDataBatchPacket(count, netIds, speeds, rpms, gears, throttles, steers, wheelCounts, wRot, wSteer, wSusp);
    }

    public static void handle(S2CVehicleDataBatchPacket msg, Supplier<NetworkManager.PacketContext> ctx) {
        ctx.get().queue(() -> {
            VxClientBodyManager manager = VxClientPhysicsWorld.getInstance().getBodyManager();
            VxClientBodyDataStore store = manager.getStore();

            int wheelDataOffset = 0;

            for (int i = 0; i < msg.count; i++) {
                Integer index = store.getIndexForNetworkId(msg.networkIds[i]);
                if (index == null) {
                    // Skip wheel data for unknown vehicles to keep offset correct
                    wheelDataOffset += msg.wheelCounts[i];
                    continue;
                }

                UUID uuid = store.getIdForIndex(index);
                if (manager.getBody(uuid) instanceof VxVehicle vehicle) {
                    vehicle.syncStateFromServer(
                        msg.speeds[i], 
                        msg.rpms[i], 
                        msg.gears[i], 
                        msg.throttles[i], 
                        msg.steers[i]
                    );

                    // Sync wheels
                    int vWheelCount = vehicle.getWheels().size();
                    int packetWheelCount = msg.wheelCounts[i];

                    for (int w = 0; w < packetWheelCount; w++) {
                        if (w < vWheelCount) {
                            vehicle.getWheels().get(w).updateClientTarget(
                                msg.wheelRotations[wheelDataOffset + w],
                                msg.wheelSteers[wheelDataOffset + w],
                                msg.wheelSuspensions[wheelDataOffset + w]
                            );
                        }
                    }
                    wheelDataOffset += packetWheelCount;
                }
            }
        });
    }
}