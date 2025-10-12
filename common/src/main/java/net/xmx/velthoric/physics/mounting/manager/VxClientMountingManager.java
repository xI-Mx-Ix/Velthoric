/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.mounting.manager;

import net.xmx.velthoric.event.api.VxClientPlayerNetworkEvent;
import net.xmx.velthoric.physics.mounting.seat.VxSeat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the state of all mountable seats on the client side.
 * This class acts as a central store for seat information, providing
 * fast, map-based lookups for client-side logic and rendering.
 *
 * @author xI-Mx-Ix
 */
public final class VxClientMountingManager {

    private static final VxClientMountingManager INSTANCE = new VxClientMountingManager();

    /**
     * Maps a physics object's UUID to a map of its seats, indexed by each seat's UUID.
     * This nested map structure allows for O(1) lookup of a specific seat.
     */
    private final Map<UUID, Map<UUID, VxSeat>> objectToSeatsMap = new ConcurrentHashMap<>();

    private VxClientMountingManager() {
    }

    /**
     * Returns the singleton instance of the client mounting manager.
     *
     * @return The singleton instance.
     */
    public static VxClientMountingManager getInstance() {
        return INSTANCE;
    }

    /**
     * Registers the necessary client-side event listeners for this manager.
     */
    public static void registerEvents() {
        VxClientPlayerNetworkEvent.LoggingOut.EVENT.register(event -> INSTANCE.clearAll());
    }

    /**
     * Adds a seat to a physics object on the client side.
     * This is typically called from the object's client-side constructor.
     *
     * @param objectId The UUID of the object.
     * @param seat     The seat to add.
     */
    public void addSeat(UUID objectId, VxSeat seat) {
        this.objectToSeatsMap.computeIfAbsent(objectId, k -> new ConcurrentHashMap<>()).put(seat.getId(), seat);
    }

    /**
     * Removes all seat data associated with a specific physics object.
     * This is called when an object is removed from the client's world.
     *
     * @param objectId The UUID of the physics object.
     */
    public void removeSeatsForObject(UUID objectId) {
        this.objectToSeatsMap.remove(objectId);
    }

    /**
     * Retrieves all seats for a given physics object.
     *
     * @param objectId The UUID of the physics object.
     * @return A collection of seats for the object, or an empty collection if none exist.
     */
    public Collection<VxSeat> getSeats(UUID objectId) {
        Map<UUID, VxSeat> seats = this.objectToSeatsMap.get(objectId);
        return seats != null ? seats.values() : Collections.emptyList();
    }

    /**
     * Retrieves a specific seat for a given physics object using the seat's UUID.
     * This provides a highly efficient O(1) lookup.
     *
     * @param objectId The UUID of the physics object.
     * @param seatId The UUID of the seat.
     * @return An Optional containing the seat if found.
     */
    public Optional<VxSeat> getSeat(UUID objectId, UUID seatId) {
        Map<UUID, VxSeat> seats = this.objectToSeatsMap.get(objectId);
        return seats != null ? Optional.ofNullable(seats.get(seatId)) : Optional.empty();
    }

    /**
     * Clears all stored seat data. This is called when the player logs out.
     */
    public void clearAll() {
        this.objectToSeatsMap.clear();
    }
}