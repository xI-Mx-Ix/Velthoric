/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.config;

/**
 * Configuration specific to motorcycles.
 *
 * @author xI-Mx-Ix
 */
public class VxMotorcycleConfig extends VxVehicleConfig {

    private final float maxTorque;
    private final float maxRpm;
    private final float[] gearRatios;

    /**
     * Constructs a new motorcycle configuration.
     *
     * @param mass        The mass in kg.
     * @param torque      The maximum engine torque.
     * @param rpm         The maximum engine RPM.
     * @param gears       The gear ratios.
     */
    public VxMotorcycleConfig(float mass, float torque, float rpm, float[] gears) {
        super(mass);
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