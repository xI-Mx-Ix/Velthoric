/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.sync;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.network.VxPacketHandler;
import net.xmx.velthoric.physics.body.manager.VxBodyManager;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.vehicle.VxVehicle;
import net.xmx.velthoric.physics.vehicle.wheel.VxWheel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the network synchronization of dynamic vehicle states.
 * This class is responsible for efficiently collecting vehicle data from 'dirty'
 * vehicles (e.g., wheel state, speed) and dispatching update packets to all
 * tracking clients.
 *
 * @author xI-Mx-Ix
 */
public class VxVehicleNetworkDispatcher {

    /**
     * Collects vehicle state updates for all dirty vehicles and sends them to tracking players.
     * This method is designed to be called from a dedicated network synchronization thread.
     *
     * @param level The server level.
     * @param manager The body manager containing all physics bodies.
     * @param bodyTrackers A map that tracks which players are watching which bodies.
     */
    public void dispatchUpdates(ServerLevel level, VxBodyManager manager, Map<UUID, Set<ServerPlayer>> bodyTrackers) {
        // Step 1: Collect all vehicles that have been marked with a dirty state.
        List<VxVehicle> dirtyVehicles = new ArrayList<>();
        for (VxBody body : manager.getAllBodies()) {
            if (body instanceof VxVehicle vehicle && vehicle.isVehicleStateDirty()) {
                dirtyVehicles.add(vehicle);
                // Reset the dirty flag immediately to prevent sending the same data multiple times.
                vehicle.clearVehicleStateDirty();
            }
        }

        // If no vehicles need updates, exit early.
        if (dirtyVehicles.isEmpty()) {
            return;
        }

        // Step 2: Schedule the packet creation and sending on the main server thread
        // for thread safety with Netty.
        level.getServer().execute(() -> {
            for (VxVehicle vehicle : dirtyVehicles) {
                // Find all players who are currently tracking this vehicle.
                Set<ServerPlayer> trackers = bodyTrackers.get(vehicle.getPhysicsId());
                if (trackers == null || trackers.isEmpty()) {
                    continue; // Skip if no one is watching this vehicle.
                }

                // Step 3: Gather the vehicle data for the packet constructor.
                float speedKmh = vehicle.getSpeedKmh();
                List<VxWheel> wheels = vehicle.getWheels();
                int wheelCount = wheels.size();

                float[] rotations = new float[wheelCount];
                float[] steers = new float[wheelCount];
                float[] suspensions = new float[wheelCount];

                if (wheelCount > 0) {
                    for (int i = 0; i < wheelCount; i++) {
                        VxWheel wheel = wheels.get(i);
                        rotations[i] = wheel.getRotationAngle();
                        steers[i] = wheel.getSteerAngle();
                        suspensions[i] = wheel.getSuspensionLength();
                    }
                }

                // Step 4: Create a single packet with all vehicle data.
                S2CVehicleStatePacket packet = new S2CVehicleStatePacket(vehicle.getPhysicsId(), speedKmh, rotations, steers, suspensions);

                // Step 5: Send the packet to every player tracking this vehicle.
                for (ServerPlayer player : trackers) {
                    VxPacketHandler.sendToPlayer(packet, player);
                }
            }
        });
    }
}