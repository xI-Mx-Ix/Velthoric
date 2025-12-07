/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.config;

import com.github.stephengold.joltjni.enumerate.ETransmissionMode;

/**
 * Configuration specific to car-like vehicles.
 * Includes engine, transmission settings, and box-shape dimensions.
 *
 * @author xI-Mx-Ix
 */
public class VxCarConfig extends VxVehicleConfig {

    private final float maxTorque;
    private final float maxRpm;
    private final float[] gearRatios;

    /**
     * Constructs a new car configuration.
     *
     * @param mass             The mass in kg.
     * @param torque           The maximum engine torque.
     * @param rpm              The maximum engine RPM.
     * @param gears            The gear ratios.
     * @param transmissionMode The transmission mode (Auto/Manual).
     */
    public VxCarConfig(float mass, float torque, float rpm, float[] gears, ETransmissionMode transmissionMode) {
        super(mass, transmissionMode);
        this.maxTorque = torque;
        this.maxRpm = rpm;
        this.gearRatios = gears;
    }

    public float getMaxTorque() {
        return maxTorque;
    }

    public float getMaxRpm() {
        return maxRpm;
    }

    public float[] getGearRatios() {
        return gearRatios;
    }
}