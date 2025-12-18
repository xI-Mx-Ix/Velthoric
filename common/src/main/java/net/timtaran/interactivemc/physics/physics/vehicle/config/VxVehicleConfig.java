/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.physics.vehicle.config;

import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.ETransmissionMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Base configuration for any vehicle.
 * This class stores universal physical properties like mass, wheel layout,
 * and transmission mode, but does not enforce a specific chassis shape.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxVehicleConfig {

    private final float mass;
    private final ETransmissionMode transmissionMode;
    private final List<WheelInfo> wheels = new ArrayList<>();

    /**
     * Constructs a new vehicle configuration.
     *
     * @param mass             The mass of the vehicle in kilograms.
     * @param transmissionMode The transmission mode (Auto/Manual).
     */
    public VxVehicleConfig(float mass, ETransmissionMode transmissionMode) {
        this.mass = mass;
        this.transmissionMode = transmissionMode;
    }

    /**
     * Adds a wheel definition to the configuration.
     *
     * @param pos       The local position of the wheel relative to the chassis center.
     * @param radius    The radius of the wheel.
     * @param width     The width of the wheel.
     * @param powered   Whether this wheel is connected to the engine/transmission.
     * @param steerable Whether this wheel can steer.
     */
    public void addWheel(Vec3 pos, float radius, float width, boolean powered, boolean steerable) {
        this.wheels.add(new WheelInfo(pos, radius, width, powered, steerable));
    }

    /**
     * Gets the mass of the vehicle.
     *
     * @return The mass in kg.
     */
    public float getMass() {
        return mass;
    }

    /**
     * Gets the configured transmission mode.
     *
     * @return The transmission mode.
     */
    public ETransmissionMode getTransmissionMode() {
        return transmissionMode;
    }

    /**
     * Gets the list of defined wheels.
     *
     * @return The list of wheel information.
     */
    public List<WheelInfo> getWheels() {
        return wheels;
    }

    /**
     * Represents the static configuration of a single wheel.
     */
    public record WheelInfo(Vec3 position, float radius, float width, boolean powered, boolean steerable) {
    }
}