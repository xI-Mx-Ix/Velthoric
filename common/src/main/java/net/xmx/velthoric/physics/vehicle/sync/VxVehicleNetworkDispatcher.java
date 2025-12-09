/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.sync;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.network.VxPacketHandler;
import net.xmx.velthoric.physics.body.manager.VxBodyManager;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.vehicle.VxVehicle;
import net.xmx.velthoric.physics.vehicle.part.impl.VxVehicleWheel;

import java.util.*;

/**
 * Collects dirty vehicle states and dispatches the efficient batch packet.
 *
 * @author xI-Mx-Ix
 */
public class VxVehicleNetworkDispatcher {

    public void dispatchUpdates(ServerLevel level, VxBodyManager manager, Map<Integer, Set<ServerPlayer>> bodyTrackers) {
        // 1. Identify dirty vehicles
        List<VxVehicle> dirtyVehicles = new ArrayList<>();
        for (VxBody body : manager.getAllBodies()) {
            if (body instanceof VxVehicle v && v.isVehicleStateDirty()) {
                dirtyVehicles.add(v);
                v.clearVehicleStateDirty();
            }
        }

        if (dirtyVehicles.isEmpty()) return;

        level.getServer().execute(() -> {
            // Group vehicles by the players who can see them to minimize packet count
            Map<ServerPlayer, List<VxVehicle>> playerBatches = new HashMap<>();

            for (VxVehicle vehicle : dirtyVehicles) {
                Set<ServerPlayer> trackers = bodyTrackers.get(vehicle.getNetworkId());
                if (trackers == null) continue;

                for (ServerPlayer player : trackers) {
                    playerBatches.computeIfAbsent(player, k -> new ArrayList<>()).add(vehicle);
                }
            }

            // Construct and send one packet per player containing all vehicles they see
            for (Map.Entry<ServerPlayer, List<VxVehicle>> entry : playerBatches.entrySet()) {
                sendBatchToPlayer(entry.getKey(), entry.getValue());
            }
        });
    }

    private void sendBatchToPlayer(ServerPlayer player, List<VxVehicle> vehicles) {
        int count = vehicles.size();
        int[] ids = new int[count];
        float[] speeds = new float[count];
        float[] rpms = new float[count];
        int[] gears = new int[count];
        float[] throttles = new float[count];
        float[] steers = new float[count];
        int[] wheelCounts = new int[count];

        FloatArrayList wRot = new FloatArrayList();
        FloatArrayList wSteer = new FloatArrayList();
        FloatArrayList wSusp = new FloatArrayList();

        for (int i = 0; i < count; i++) {
            VxVehicle v = vehicles.get(i);
            ids[i] = v.getNetworkId();
            speeds[i] = v.getSpeedKmh();
            rpms[i] = v.getEngine().getRpm();
            gears[i] = v.getTransmission().getGear();
            throttles[i] = v.getInputThrottle();
            steers[i] = v.getInputSteer();

            List<VxVehicleWheel> wheels = v.getWheels();
            wheelCounts[i] = wheels.size();

            for (VxVehicleWheel w : wheels) {
                wRot.add(w.getRotationAngle());
                wSteer.add(w.getSteerAngle());
                wSusp.add(w.getSuspensionLength());
            }
        }

        S2CVehicleDataBatchPacket packet = new S2CVehicleDataBatchPacket(
                count, ids, speeds, rpms, gears, throttles, steers, wheelCounts,
                wRot.toFloatArray(), wSteer.toFloatArray(), wSusp.toFloatArray()
        );
        VxPacketHandler.sendToPlayer(packet, player);
    }
}