/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.vehicle.config;

import net.xmx.velthoric.core.vehicle.part.slot.VehicleSeatSlot;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The base configuration for any vehicle entity (Car, Boat, Plane, Tank).
 * <p>
 * This class defines properties common to all physical vehicles, such as mass,
 * chassis dimensions, and seating arrangements. It serves as the root of the
 * configuration hierarchy, allowing specific vehicle types to extend it with
 * their own required data (e.g., wheels, hydrodynamics, aerodynamics).
 *
 * @author xI-Mx-Ix
 */
public class VxVehicleConfig {

    /**
     * The unique identifier for this vehicle configuration.
     * Used for registry lookups and network synchronization.
     */
    protected final String id;

    // --- Physics Body Properties ---

    /**
     * The total mass of the vehicle chassis in kilograms.
     * <p>
     * This value affects how the vehicle reacts to collisions and how much
     * force is required to accelerate it.
     */
    protected float mass = 1500.0f;

    /**
     * The half-extents (half-size) of the chassis collision box.
     * <p>
     * Defined as (Width/2, Height/2, Length/2).
     */
    protected final Vector3f chassisHalfExtents = new Vector3f(1.0f, 0.5f, 2.0f);

    /**
     * The offset of the center of mass relative to the geometric center of the chassis.
     * <p>
     * Lowering the Y value significantly improves vehicle stability and reduces
     * the chance of rolling over during sharp turns.
     */
    protected final Vector3f centerOfMassOffset = new Vector3f(0.0f, -0.5f, 0.0f);

    // --- Component Data ---

    /**
     * The list of defined seat attachment slots.
     * Defines where players or entities can sit on this vehicle.
     */
    protected final List<VehicleSeatSlot> seatSlots = new ArrayList<>();

    // --- Defaults ---

    /**
     * The ID of the default seat definition to install on slots that do not specify one.
     * Used as a fallback during initialization.
     */
    protected String defaultSeatId = "velthoric:seat_standard";

    /**
     * Constructs a new base vehicle configuration.
     *
     * @param id The unique configuration ID.
     */
    public VxVehicleConfig(String id) {
        this.id = id;
    }

    /**
     * Sets the mass of the vehicle.
     *
     * @param mass The mass in kg.
     * @return This instance for chaining.
     */
    public VxVehicleConfig setMass(float mass) {
        this.mass = mass;
        return this;
    }

    /**
     * Sets the chassis dimensions.
     *
     * @param x Half-width.
     * @param y Half-height.
     * @param z Half-length.
     * @return This instance for chaining.
     */
    public VxVehicleConfig setChassisSize(float x, float y, float z) {
        this.chassisHalfExtents.set(x, y, z);
        return this;
    }

    /**
     * Sets the center of mass offset.
     *
     * @param x X Offset.
     * @param y Y Offset (Negative is down).
     * @param z Z Offset.
     * @return This instance for chaining.
     */
    public VxVehicleConfig setCenterOfMass(float x, float y, float z) {
        this.centerOfMassOffset.set(x, y, z);
        return this;
    }

    /**
     * Adds a seat slot definition to the chassis.
     *
     * @param slot The seat slot to add.
     * @return This instance for chaining.
     */
    public VxVehicleConfig addSeatSlot(VehicleSeatSlot slot) {
        this.seatSlots.add(slot);
        return this;
    }

    /**
     * Sets the default seat ID to use for this vehicle.
     *
     * @param seatId The resource ID of the seat definition.
     * @return This instance for chaining.
     */
    public VxVehicleConfig setDefaultSeat(String seatId) {
        this.defaultSeatId = seatId;
        return this;
    }

    // --- Getters ---

    public String getId() {
        return id;
    }

    public float getMass() {
        return mass;
    }

    public Vector3f getChassisHalfExtents() {
        return chassisHalfExtents;
    }

    public Vector3f getCenterOfMassOffset() {
        return centerOfMassOffset;
    }

    public List<VehicleSeatSlot> getSeatSlots() {
        return Collections.unmodifiableList(seatSlots);
    }

    public String getDefaultSeatId() {
        return defaultSeatId;
    }
}