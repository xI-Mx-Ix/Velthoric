/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.physics.mounting.manager;

import net.timtaran.interactivemc.physics.event.api.VxClientPlayerNetworkEvent;
import net.timtaran.interactivemc.physics.physics.mounting.seat.VxSeat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the state of all mountable seats on the client side.
 * This class acts as a central store for seat information, providing
 * fast, map-based lookups for client-side logic and rendering.
 *
 * @author xI-Mx-Ix
 */
public enum VxClientMountingManager {
    INSTANCE;

    /**
     * Maps a physics body's UUID to a map of its seats, indexed by each seat's UUID.
     * This nested map structure allows for O(1) lookup of a specific seat.
     */
    private final Map<UUID, Map<UUID, VxSeat>> bodyToSeatsMap = new ConcurrentHashMap<>();

    /**
     * Registers the necessary client-side event listeners for this manager.
     */
    public static void registerEvents() {
        VxClientPlayerNetworkEvent.LoggingOut.EVENT.register(event -> INSTANCE.clearAll());
    }

    /**
     * Adds a seat to a physics body on the client side.
     * This is typically called from the body's client-side constructor.
     *
     * @param physicsId The UUID of the body.
     * @param seat     The seat to add.
     */
    public void addSeat(UUID physicsId, VxSeat seat) {
        this.bodyToSeatsMap.computeIfAbsent(physicsId, k -> new ConcurrentHashMap<>()).put(seat.getId(), seat);
    }

    /**
     * Removes all seat data associated with a specific physics body.
     * This is called when a body is removed from the client's world.
     *
     * @param physicsId The UUID of the physics body.
     */
    public void removeSeatsForBody(UUID physicsId) {
        this.bodyToSeatsMap.remove(physicsId);
    }

    /**
     * Retrieves all seats for a given physics body.
     *
     * @param physicsId The UUID of the physics body.
     * @return A collection of seats for the body, or an empty collection if none exist.
     */
    public Collection<VxSeat> getSeats(UUID physicsId) {
        Map<UUID, VxSeat> seats = this.bodyToSeatsMap.get(physicsId);
        return seats != null ? seats.values() : Collections.emptyList();
    }

    /**
     * Retrieves a specific seat for a given physics body using the seat's UUID.
     * This provides a highly efficient O(1) lookup.
     *
     * @param physicsId The UUID of the physics body.
     * @param seatId The UUID of the seat.
     * @return An Optional containing the seat if found.
     */
    public Optional<VxSeat> getSeat(UUID physicsId, UUID seatId) {
        Map<UUID, VxSeat> seats = this.bodyToSeatsMap.get(physicsId);
        return seats != null ? Optional.ofNullable(seats.get(seatId)) : Optional.empty();
    }

    /**
     * Clears all stored seat data. This is called when the player logs out.
     */
    public void clearAll() {
        this.bodyToSeatsMap.clear();
    }
}