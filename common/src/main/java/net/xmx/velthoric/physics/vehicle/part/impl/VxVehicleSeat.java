/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.part.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.xmx.velthoric.bridge.mounting.seat.VxSeat;
import net.xmx.velthoric.physics.vehicle.VxVehicle;
import net.xmx.velthoric.physics.vehicle.part.slot.VehicleSeatSlot;
import net.xmx.velthoric.physics.vehicle.part.definition.VxSeatDefinition;
import net.xmx.velthoric.physics.vehicle.part.VxPart;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * Represents a seat part attached to the vehicle.
 * <p>
 * This class wraps the logical mounting point ({@link VxSeat}) and combines it with
 * the configurable visual definition ({@link VxSeatDefinition}) and chassis slot data.
 * It handles the player interaction logic for entering the vehicle.
 *
 * @author xI-Mx-Ix
 */
public class VxVehicleSeat extends VxPart {

    /**
     * The logical seat data used for the entity mounting system.
     */
    private final VxSeat seatData;

    /**
     * The chassis configuration for this seat slot (Position, Driver Status).
     */
    private final VehicleSeatSlot slot;

    /**
     * The current definition of the seat (Visual Model).
     */
    private VxSeatDefinition definition;

    /**
     * Constructs a seat part.
     *
     * @param vehicle    The parent vehicle.
     * @param seatData   The logical seat definition containing IDs and interaction logic.
     * @param slot       The chassis slot configuration.
     * @param definition The initial seat definition (visuals).
     */
    public VxVehicleSeat(VxVehicle vehicle, VxSeat seatData, VehicleSeatSlot slot, VxSeatDefinition definition) {
        super(vehicle, seatData.getName(), seatData.getRiderOffset(), seatData.getLocalAABB());
        this.seatData = seatData;
        this.slot = slot;
        this.definition = definition;
    }

    /**
     * Handles interaction with this seat.
     * <p>
     * On the client, this sends a packet to the server to request mounting.
     * On the server, this triggers the mounting manager logic.
     *
     * @param player The player interacting with the seat.
     * @return True if the interaction was handled.
     */
    @Override
    public boolean interact(Player player) {
        // Client-side logic: Send packet via parent VxPart implementation
        if (super.interact(player)) {
            return true;
        }

        // Server-side logic: Handle mounting
        if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            VxPhysicsWorld world = vehicle.getPhysicsWorld();
            if (world != null) {
                // Delegate mounting logic to the world manager
                world.getMountingManager().requestMounting(serverPlayer, vehicle.getPhysicsId(), this.partId);
                return true;
            }
        }
        return false;
    }

    /**
     * Updates the installed seat definition.
     * This changes the visual model referenced by the renderer.
     *
     * @param newDef The new seat definition.
     */
    public void setDefinition(VxSeatDefinition newDef) {
        this.definition = newDef;
        // Note: If dynamic AABB resizing is implemented in VxPart, update it here.
    }

    /**
     * Gets the logical seat data used for mounting.
     *
     * @return The seat logic object.
     */
    public VxSeat getSeatData() {
        return seatData;
    }

    /**
     * Gets the chassis slot configuration.
     *
     * @return The slot data.
     */
    public VehicleSeatSlot getSlot() {
        return slot;
    }

    /**
     * Gets the current seat definition.
     *
     * @return The definition object.
     */
    public VxSeatDefinition getDefinition() {
        return definition;
    }
}