/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.sync;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.network.VxPacketHandler;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.vehicle.VxVehicle;
import net.xmx.velthoric.physics.vehicle.wheel.VxWheel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the network synchronization of dynamic wheel states for all vehicles.
 * This class is responsible for efficiently collecting wheel data from 'dirty'
 * vehicles and dispatching update packets to all tracking clients.
 *
 * @author xI-Mx-Ix
 */
public class VxWheelNetworkDispatcher {

    /**
     * Collects wheel state updates for all dirty vehicles and sends them to tracking players.
     * This method is designed to be called from a dedicated network synchronization thread.
     *
     * @param level The server level.
     * @param manager The object manager containing all physics bodies.
     * @param objectTrackers A map that tracks which players are watching which objects.
     */
    public void dispatchUpdates(ServerLevel level, VxObjectManager manager, Map<UUID, Set<ServerPlayer>> objectTrackers) {
        // Step 1: Collect all vehicles that have been marked with dirty wheel states.
        List<VxVehicle> dirtyVehicles = new ArrayList<>();
        for (VxBody body : manager.getAllObjects()) {
            if (body instanceof VxVehicle vehicle && vehicle.areWheelsDirty()) {
                dirtyVehicles.add(vehicle);
                // Reset the dirty flag immediately to prevent sending the same data multiple times.
                vehicle.clearWheelsDirty();
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
                Set<ServerPlayer> trackers = objectTrackers.get(vehicle.getPhysicsId());
                if (trackers == null || trackers.isEmpty()) {
                    continue; // Skip if no one is watching this vehicle.
                }

                List<VxWheel> wheels = vehicle.getWheels();
                int wheelCount = wheels.size();
                if (wheelCount == 0) continue;

                // Step 3: Gather the wheel data into arrays for the packet constructor.
                float[] rotations = new float[wheelCount];
                float[] steers = new float[wheelCount];
                float[] suspensions = new float[wheelCount];

                for (int i = 0; i < wheelCount; i++) {
                    VxWheel wheel = wheels.get(i);
                    rotations[i] = wheel.getRotationAngle();
                    steers[i] = wheel.getSteerAngle();
                    suspensions[i] = wheel.getSuspensionLength();
                }

                // Step 4: Create a single packet with all wheel data for this vehicle.
                S2CUpdateWheelsPacket packet = new S2CUpdateWheelsPacket(vehicle.getPhysicsId(), wheelCount, rotations, steers, suspensions);

                // Step 5: Send the packet to every player tracking this vehicle.
                for (ServerPlayer player : trackers) {
                    VxPacketHandler.sendToPlayer(packet, player);
                }
            }
        });
    }
}