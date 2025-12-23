/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.data.slot;

import org.joml.Vector3f;

/**
 * Represents a specific location on the vehicle chassis where a seat can be installed.
 * <p>
 * This class defines the position relative to the chassis and the functional role
 * of the seat (e.g., whether it is the driver's seat). It does not define the
 * visual appearance or the physical hit-box dimensions of the seat itself.
 *
 * @author xI-Mx-Ix
 */
public class VehicleSeatSlot {

    /**
     * The unique name of this seat slot (e.g., "driver_seat", "passenger_front").
     */
    private final String name;

    /**
     * The local position of the seat relative to the chassis center of mass.
     */
    private final Vector3f position;

    /**
     * Indicates if this seat is the primary control point for the vehicle.
     */
    private boolean isDriver;

    /**
     * Constructs a new seat slot.
     *
     * @param name     The unique name of the slot.
     * @param position The local position vector.
     */
    public VehicleSeatSlot(String name, Vector3f position) {
        this.name = name;
        this.position = position;
        this.isDriver = false;
    }

    /**
     * Sets whether this is a driver seat.
     *
     * @param isDriver True if this seat controls the vehicle.
     * @return This instance for chaining.
     */
    public VehicleSeatSlot setDriver(boolean isDriver) {
        this.isDriver = isDriver;
        return this;
    }

    /**
     * Gets the name of the slot.
     *
     * @return The slot name.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the position of the slot.
     *
     * @return The position vector.
     */
    public Vector3f getPosition() {
        return position;
    }

    /**
     * Checks if this is a driver seat.
     *
     * @return True if driver, false otherwise.
     */
    public boolean isDriver() {
        return isDriver;
    }
}