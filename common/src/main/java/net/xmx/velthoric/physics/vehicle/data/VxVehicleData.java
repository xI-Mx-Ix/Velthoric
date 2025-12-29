/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.data;

import net.xmx.velthoric.physics.vehicle.data.slot.VehicleSeatSlot;
import net.xmx.velthoric.physics.vehicle.data.slot.VehicleWheelSlot;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The master configuration object for a vehicle chassis.
 * <p>
 * This class aggregates all component data (Engine, Transmission, Wheel Slots)
 * into a single definition. It represents the static properties of a vehicle type.
 *
 * @author xI-Mx-Ix
 */
public class VxVehicleData {

    /**
     * The unique identifier for this vehicle configuration.
     */
    private final String id;

    // --- Physics Body Properties ---

    /**
     * The total mass of the vehicle chassis in kilograms.
     */
    private float mass = 1500.0f;

    /**
     * The half-extents (half-size) of the chassis collision box.
     */
    private final Vector3f chassisHalfExtents = new Vector3f(1.0f, 0.5f, 2.0f);

    /**
     * The offset of the center of mass relative to the geometric center of the chassis.
     * Lowering the Y value improves stability.
     */
    private final Vector3f centerOfMassOffset = new Vector3f(0.0f, -0.5f, 0.0f);

    // --- Component Data ---

    /**
     * The engine configuration.
     */
    private final VehicleEngineData engine = new VehicleEngineData();

    /**
     * The transmission configuration.
     */
    private final VehicleTransmissionData transmission = new VehicleTransmissionData();

    /**
     * The list of defined wheel attachment slots.
     */
    private final List<VehicleWheelSlot> wheelSlots = new ArrayList<>();

    /**
     * The list of defined seat attachment slots.
     */
    private final List<VehicleSeatSlot> seatSlots = new ArrayList<>();

    // --- Defaults ---

    /**
     * The ID of the default wheel definition to install on slots that do not specify one.
     */
    private String defaultWheelId = "velthoric:wheel_standard";

    /**
     * The ID of the default seat definition to install on slots that do not specify one.
     */
    private String defaultSeatId = "velthoric:seat_standard";

    /**
     * Constructs a new vehicle data container.
     *
     * @param id The unique ID.
     */
    public VxVehicleData(String id) {
        this.id = id;
    }

    /**
     * Sets the mass of the vehicle.
     *
     * @param mass The mass in kg.
     * @return This instance for chaining.
     */
    public VxVehicleData setMass(float mass) {
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
    public VxVehicleData setChassisSize(float x, float y, float z) {
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
    public VxVehicleData setCenterOfMass(float x, float y, float z) {
        this.centerOfMassOffset.set(x, y, z);
        return this;
    }

    /**
     * Adds a wheel slot definition to the chassis.
     *
     * @param slot The wheel slot to add.
     * @return This instance for chaining.
     */
    public VxVehicleData addWheelSlot(VehicleWheelSlot slot) {
        this.wheelSlots.add(slot);
        return this;
    }

    /**
     * Adds a seat slot definition to the chassis.
     *
     * @param slot The seat slot to add.
     * @return This instance for chaining.
     */
    public VxVehicleData addSeatSlot(VehicleSeatSlot slot) {
        this.seatSlots.add(slot);
        return this;
    }

    /**
     * Sets the default wheel ID to use for this vehicle.
     *
     * @param wheelId The resource ID of the wheel definition.
     * @return This instance for chaining.
     */
    public VxVehicleData setDefaultWheel(String wheelId) {
        this.defaultWheelId = wheelId;
        return this;
    }

    /**
     * Sets the default seat ID to use for this vehicle.
     *
     * @param seatId The resource ID of the seat definition.
     * @return This instance for chaining.
     */
    public VxVehicleData setDefaultSeat(String seatId) {
        this.defaultSeatId = seatId;
        return this;
    }

    // --- Getters ---

    /**
     * Gets the vehicle ID.
     *
     * @return The ID string.
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the vehicle mass.
     *
     * @return The mass in kg.
     */
    public float getMass() {
        return mass;
    }

    /**
     * Gets the chassis half-extents.
     *
     * @return The vector containing half-width, half-height, and half-length.
     */
    public Vector3f getChassisHalfExtents() {
        return chassisHalfExtents;
    }

    /**
     * Gets the center of mass offset.
     *
     * @return The offset vector.
     */
    public Vector3f getCenterOfMassOffset() {
        return centerOfMassOffset;
    }

    /**
     * Gets the engine configuration.
     *
     * @return The engine data object.
     */
    public VehicleEngineData getEngine() {
        return engine;
    }

    /**
     * Gets the transmission configuration.
     *
     * @return The transmission data object.
     */
    public VehicleTransmissionData getTransmission() {
        return transmission;
    }

    /**
     * Gets the list of wheel slots.
     *
     * @return An unmodifiable list of wheel slots.
     */
    public List<VehicleWheelSlot> getWheelSlots() {
        return Collections.unmodifiableList(wheelSlots);
    }

    /**
     * Gets the list of seat slots.
     *
     * @return An unmodifiable list of seat slots.
     */
    public List<VehicleSeatSlot> getSeatSlots() {
        return Collections.unmodifiableList(seatSlots);
    }

    /**
     * Gets the ID of the default wheel.
     *
     * @return The wheel ID.
     */
    public String getDefaultWheelId() {
        return defaultWheelId;
    }

    /**
     * Gets the ID of the default seat.
     *
     * @return The seat ID.
     */
    public String getDefaultSeatId() {
        return defaultSeatId;
    }
}