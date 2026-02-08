/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.vehicle.module;

import net.minecraft.util.Mth;

/**
 * Handles the engine physics, including RPM calculation, torque output, and sound pitch.
 *
 * @author xI-Mx-Ix
 */
public class VxEngineModule {

    private final float maxTorque;
    private final float minRpm;
    private final float maxRpm;
    private final float inertia;

    private float currentRpm;
    private float currentTorque;

    /**
     * Constructs a new vehicle engine.
     *
     * @param maxTorque The maximum torque output in Newton-meters.
     * @param minRpm    The idle RPM.
     * @param maxRpm    The redline RPM.
     */
    public VxEngineModule(float maxTorque, float minRpm, float maxRpm) {
        this.maxTorque = maxTorque;
        this.minRpm = minRpm;
        this.maxRpm = maxRpm;
        this.currentRpm = minRpm;
        this.inertia = 0.5f; // Resistance to RPM change
    }

    /**
     * Updates the engine physics.
     *
     * @param throttle   The driver's throttle input (0.0 to 1.0).
     * @param wheelRpm   The average RPM of the drive wheels (physics feedback).
     * @param gearRatio  The current transmission gear ratio.
     * @param isClutched Whether the clutch is engaged.
     */
    public void update(float throttle, float wheelRpm, float gearRatio, boolean isClutched) {
        float targetRpm;

        if (!isClutched || gearRatio == 0.0f) {
            // Neutral or Clutch Disengaged: Engine revs freely based on throttle
            float idleTarget = Mth.lerp(throttle, minRpm, maxRpm);
            targetRpm = Mth.lerp(0.1f / inertia, currentRpm, idleTarget);
        } else {
            // Clutch Engaged: RPM is locked to wheel speed
            // Formula: Wheel RPM * Gear Ratio * Final Drive (approx 3.4)
            float drivenRpm = Math.abs(wheelRpm * gearRatio * 3.4f);
            targetRpm = Math.max(minRpm, drivenRpm);
        }

        // Apply limits and smoothing
        this.currentRpm = Mth.clamp(targetRpm, minRpm, maxRpm);
        this.currentTorque = maxTorque * throttle;
    }

    /**
     * Sets the RPM directly from a server packet.
     *
     * @param rpm The synchronized RPM.
     */
    public void setSynchronizedRpm(float rpm) {
        this.currentRpm = rpm;
    }

    public float getRpm() {
        return currentRpm;
    }

    public float getTorque() {
        return currentTorque;
    }

    public float getMaxRpm() {
        return maxRpm;
    }

    public float getMinRpm() {
        return minRpm;
    }
}