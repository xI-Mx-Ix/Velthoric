/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.vehicle.config;

/**
 * Defines the static performance characteristics of a vehicle's engine.
 * <p>
 * This class contains the physical properties required to simulate the engine's
 * torque curve and rotational limits.
 *
 * @author xI-Mx-Ix
 */
public class VxEngineConfig {

    /**
     * The maximum torque the engine can produce in Newton-meters (Nm).
     */
    private float maxTorque = 500.0f;

    /**
     * The minimum RPM (idle speed) of the engine.
     * The engine will try to maintain this speed when the throttle is zero.
     */
    private float minRpm = 1000.0f;

    /**
     * The maximum RPM (redline) of the engine.
     * The engine torque typically drops off or cuts out at this speed.
     */
    private float maxRpm = 7000.0f;

    /**
     * Default constructor.
     */
    public VxEngineConfig() {
    }

    /**
     * Constructs a new engine data object with specific parameters.
     *
     * @param maxTorque The maximum torque in Nm.
     * @param minRpm    The idle RPM.
     * @param maxRpm    The maximum RPM.
     */
    public VxEngineConfig(float maxTorque, float minRpm, float maxRpm) {
        this.maxTorque = maxTorque;
        this.minRpm = minRpm;
        this.maxRpm = maxRpm;
    }

    /**
     * Gets the maximum torque.
     *
     * @return The torque in Newton-meters.
     */
    public float getMaxTorque() {
        return maxTorque;
    }

    /**
     * Sets the maximum torque.
     *
     * @param maxTorque The torque in Newton-meters.
     * @return This instance for chaining.
     */
    public VxEngineConfig setMaxTorque(float maxTorque) {
        this.maxTorque = maxTorque;
        return this;
    }

    /**
     * Gets the minimum (idle) RPM.
     *
     * @return The idle RPM.
     */
    public float getMinRpm() {
        return minRpm;
    }

    /**
     * Sets the minimum (idle) RPM.
     *
     * @param minRpm The idle RPM.
     * @return This instance for chaining.
     */
    public VxEngineConfig setMinRpm(float minRpm) {
        this.minRpm = minRpm;
        return this;
    }

    /**
     * Gets the maximum (redline) RPM.
     *
     * @return The maximum RPM.
     */
    public float getMaxRpm() {
        return maxRpm;
    }

    /**
     * Sets the maximum (redline) RPM.
     *
     * @param maxRpm The maximum RPM.
     * @return This instance for chaining.
     */
    public VxEngineConfig setMaxRpm(float maxRpm) {
        this.maxRpm = maxRpm;
        return this;
    }
}