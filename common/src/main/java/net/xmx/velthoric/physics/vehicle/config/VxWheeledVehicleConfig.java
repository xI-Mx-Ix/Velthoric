/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.config;

import net.xmx.velthoric.physics.vehicle.part.slot.VehicleWheelSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for vehicles that operate on wheels and use an internal combustion engine.
 * <p>
 * This class extends the base configuration to include the powertrain (Engine, Transmission)
 * and wheel attachment points. It serves as the parent for Cars and Motorcycles.
 *
 * @author xI-Mx-Ix
 */
public class VxWheeledVehicleConfig extends VxVehicleConfig {

    /**
     * The engine configuration (Torque, RPM limits).
     */
    protected final VxEngineConfig engine = new VxEngineConfig();

    /**
     * The transmission configuration (Gear ratios, shift timing).
     */
    protected final VxTransmissionConfig transmission = new VxTransmissionConfig();

    /**
     * The list of defined wheel attachment slots.
     */
    protected final List<VehicleWheelSlot> wheelSlots = new ArrayList<>();

    /**
     * The ID of the default wheel definition to install on slots that do not specify one.
     */
    protected String defaultWheelId = "velthoric:wheel_standard";

    /**
     * Constructs a new wheeled vehicle configuration.
     *
     * @param id The unique configuration ID.
     */
    public VxWheeledVehicleConfig(String id) {
        super(id);
    }

    /**
     * Adds a wheel slot definition to the chassis.
     *
     * @param slot The wheel slot to add.
     * @return This instance for chaining.
     */
    public VxWheeledVehicleConfig addWheelSlot(VehicleWheelSlot slot) {
        this.wheelSlots.add(slot);
        return this;
    }

    /**
     * Sets the default wheel ID to use for this vehicle.
     *
     * @param wheelId The resource ID of the wheel definition.
     * @return This instance for chaining.
     */
    public VxWheeledVehicleConfig setDefaultWheel(String wheelId) {
        this.defaultWheelId = wheelId;
        return this;
    }

    // --- Getters ---

    public VxEngineConfig getEngine() {
        return engine;
    }

    public VxTransmissionConfig getTransmission() {
        return transmission;
    }

    public List<VehicleWheelSlot> getWheelSlots() {
        return Collections.unmodifiableList(wheelSlots);
    }

    public String getDefaultWheelId() {
        return defaultWheelId;
    }
}