/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.vehicle.config;

import com.github.stephengold.joltjni.enumerate.ETransmissionMode;

/**
 * Defines the configuration of the vehicle's transmission system.
 * <p>
 * This includes gear ratios, shifting timing, and the type of transmission
 * (Automatic or Manual).
 *
 * @author xI-Mx-Ix
 */
public class VxTransmissionConfig {

    /**
     * The mode of transmission (Auto or Manual).
     */
    private ETransmissionMode mode = ETransmissionMode.Manual;

    /**
     * An array of gear ratios for forward gears.
     * Index 0 is 1st gear, Index 1 is 2nd gear, etc.
     */
    private float[] gearRatios = new float[]{3.5f, 2.0f, 1.4f, 1.0f};

    /**
     * The gear ratio for the reverse gear.
     * This value is typically negative in physics calculations.
     */
    private float reverseRatio = -3.0f;

    /**
     * The time it takes to switch gears in seconds.
     * During this time, no torque is transferred to the wheels.
     */
    private float switchTime = 0.5f;

    /**
     * The strength of the clutch.
     * Higher values mean the clutch engages more abruptly.
     */
    private float clutchStrength = 10.0f;

    /**
     * Gets the transmission mode.
     *
     * @return The transmission mode.
     */
    public ETransmissionMode getMode() {
        return mode;
    }

    /**
     * Sets the transmission mode.
     *
     * @param mode The new transmission mode.
     * @return This instance for chaining.
     */
    public VxTransmissionConfig setMode(ETransmissionMode mode) {
        this.mode = mode;
        return this;
    }

    /**
     * Gets the forward gear ratios.
     *
     * @return An array of float ratios.
     */
    public float[] getGearRatios() {
        return gearRatios;
    }

    /**
     * Sets the forward gear ratios.
     *
     * @param gearRatios The new gear ratios.
     * @return This instance for chaining.
     */
    public VxTransmissionConfig setGearRatios(float... gearRatios) {
        this.gearRatios = gearRatios;
        return this;
    }

    /**
     * Gets the reverse gear ratio.
     *
     * @return The reverse ratio.
     */
    public float getReverseRatio() {
        return reverseRatio;
    }

    /**
     * Sets the reverse gear ratio.
     *
     * @param reverseRatio The new reverse ratio.
     * @return This instance for chaining.
     */
    public VxTransmissionConfig setReverseRatio(float reverseRatio) {
        this.reverseRatio = reverseRatio;
        return this;
    }

    /**
     * Gets the time required to switch gears.
     *
     * @return The switch time in seconds.
     */
    public float getSwitchTime() {
        return switchTime;
    }

    /**
     * Sets the time required to switch gears.
     *
     * @param switchTime The switch time in seconds.
     * @return This instance for chaining.
     */
    public VxTransmissionConfig setSwitchTime(float switchTime) {
        this.switchTime = switchTime;
        return this;
    }

    /**
     * Gets the clutch strength.
     *
     * @return The clutch strength.
     */
    public float getClutchStrength() {
        return clutchStrength;
    }

    /**
     * Sets the clutch strength.
     *
     * @param clutchStrength The clutch strength.
     * @return This instance for chaining.
     */
    public VxTransmissionConfig setClutchStrength(float clutchStrength) {
        this.clutchStrength = clutchStrength;
        return this;
    }
}