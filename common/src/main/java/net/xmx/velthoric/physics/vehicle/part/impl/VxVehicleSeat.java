/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.part.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.xmx.velthoric.physics.mounting.seat.VxSeat;
import net.xmx.velthoric.physics.vehicle.VxVehicle;
import net.xmx.velthoric.physics.vehicle.part.VxPart;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * A vehicle part that represents a seat.
 * Wraps the logical {@link VxSeat} and handles the mounting interaction.
 *
 * @author xI-Mx-Ix
 */
public class VxVehicleSeat extends VxPart {

    private final VxSeat seatData;

    /**
     * Constructs a seat part.
     *
     * @param vehicle  The parent vehicle.
     * @param seatData The logical seat definition containing IDs and offsets.
     */
    public VxVehicleSeat(VxVehicle vehicle, VxSeat seatData) {
        // We use the seatData's name, which ensures the UUID generation in super()
        // matches the UUID in seatData (assuming seatData used the same vehicle ID).
        super(vehicle, seatData.getName(), seatData.getRiderOffset(), seatData.getLocalAABB());
        this.seatData = seatData;

        // Verify UUID consistency (Debugging safety check)
        if (!this.partId.equals(seatData.getId())) {
            throw new IllegalStateException("Seat Part UUID mismatch! Part: " + this.partId + " Data: " + seatData.getId());
        }
    }

    @Override
    public boolean interact(Player player) {
        // Client-side logic is handled by super.interact() (sends packet)
        if (super.interact(player)) {
            return true;
        }

        // Server-side logic
        if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            VxPhysicsWorld world = vehicle.getPhysicsWorld();
            if (world != null) {
                // Delegate to the mounting manager
                world.getMountingManager().requestMounting(serverPlayer, vehicle.getPhysicsId(), this.partId);
                return true;
            }
        }
        return false;
    }

    public VxSeat getSeatData() {
        return seatData;
    }
}