/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.riding.manager;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.xmx.velthoric.event.api.VxClientPlayerNetworkEvent;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.riding.seat.VxSeat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the state of all rideable seats on the client side.
 * This class acts as a central store for seat information received from the server,
 * making it available for client-side logic and rendering.
 *
 * @author xI-Mx-Ix
 */
public final class VxClientRidingManager {

    private static final VxClientRidingManager INSTANCE = new VxClientRidingManager();

    /**
     * Maps a physics object's UUID to its list of associated seats.
     */
    private final Map<UUID, List<VxSeat>> objectToSeatsMap = new ConcurrentHashMap<>();

    private VxClientRidingManager() {
    }

    /**
     * Returns the singleton instance of the client riding manager.
     *
     * @return The singleton instance.
     */
    public static VxClientRidingManager getInstance() {
        return INSTANCE;
    }

    /**
     * Registers the necessary client-side event listeners for this manager.
     */
    public static void registerEvents() {
        VxClientPlayerNetworkEvent.LoggingOut.EVENT.register(event -> INSTANCE.clearAll());
    }

    /**
     * Reads seat data from a network buffer and associates it with a physics object.
     * This is typically called when an object is spawned.
     *
     * @param objectId The UUID of the physics object.
     * @param buf      The buffer containing the serialized seat data.
     */
    public void addSeatsFromBuffer(UUID objectId, VxByteBuf buf) {
        int seatCount = buf.readVarInt();
        if (seatCount > 0) {
            List<VxSeat> seats = new ObjectArrayList<>(seatCount);
            for (int i = 0; i < seatCount; i++) {
                seats.add(new VxSeat(buf));
            }
            this.objectToSeatsMap.put(objectId, seats);
        }
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
        return this.objectToSeatsMap.getOrDefault(objectId, Collections.emptyList());
    }

    /**
     * Clears all stored seat data. This is called when the player logs out.
     */
    public void clearAll() {
        this.objectToSeatsMap.clear();
    }
}