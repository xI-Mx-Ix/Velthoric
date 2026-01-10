/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.module;

import net.minecraft.util.Mth;

/**
 * Handles gear shifting logic and ratios.
 *
 * @author xI-Mx-Ix
 */
public class VxTransmissionModule {

    private final float[] forwardRatios;
    private final float reverseRatio;
    private final float shiftTime;

    private int currentGear = 1; // 0 = Neutral, -1 = Reverse, 1+ = Forward
    private float shiftTimer = 0.0f;

    /**
     * Constructs a transmission.
     *
     * @param forwardRatios Array of gear ratios for forward gears.
     * @param reverseRatio  The ratio for reverse gear.
     * @param switchTime    The time in seconds to change gears.
     */
    public VxTransmissionModule(float[] forwardRatios, float reverseRatio, float switchTime) {
        this.forwardRatios = forwardRatios;
        this.reverseRatio = reverseRatio;
        this.shiftTime = switchTime;
    }

    /**
     * Updates the internal shift timer.
     *
     * @param dt Delta time in seconds.
     */
    public void update(float dt) {
        if (shiftTimer > 0) {
            shiftTimer -= dt;
        }
    }

    /**
     * Attempts to shift up one gear.
     * Checks array bounds to prevent exceeding the maximum gear.
     */
    public void shiftUp() {
        if (shiftTimer > 0) return;
        if (currentGear < forwardRatios.length) {
            currentGear++;
            shiftTimer = shiftTime;
        }
    }

    /**
     * Attempts to shift down one gear.
     * Stops at Reverse (-1).
     */
    public void shiftDown() {
        if (shiftTimer > 0) return;
        if (currentGear > -1) {
            currentGear--;
            shiftTimer = shiftTime;
        }
    }

    /**
     * Gets the current effective gear ratio.
     * Contains bounds checking to prevent crashes if the physics engine reports
     * a gear index that exceeds the defined Java gear ratios.
     *
     * @return The ratio, or 0.0 if in neutral, shifting, or out of bounds.
     */
    public float getCurrentRatio() {
        // Simulate clutch depression during shift (disconnect engine from wheels)
        if (shiftTimer > 0) return 0.0f;

        // Neutral
        if (currentGear == 0) return 0.0f;

        // Reverse
        if (currentGear == -1) return reverseRatio;

        // Forward Gears
        int index = currentGear - 1;
        if (index >= 0 && index < forwardRatios.length) {
            return forwardRatios[index];
        }

        // Fallback for invalid states (treat as Neutral)
        return 0.0f;
    }

    /**
     * Gets the current gear index.
     *
     * @return The gear index (1=1st, -1=Rev, 0=Neutral).
     */
    public int getGear() {
        return currentGear;
    }

    /**
     * Synchronizes the gear from the physics simulation (Automatic Mode).
     * Clamps the value to ensure it stays within the valid range of this transmission.
     *
     * @param gear The target gear from the physics engine.
     */
    public void setSynchronizedGear(int gear) {
        // Clamp between -1 (Reverse) and the maximum number of forward gears
        this.currentGear = Mth.clamp(gear, -1, forwardRatios.length);
    }

    /**
     * Checks if a shift operation is currently in progress.
     *
     * @return True if shifting.
     */
    public boolean isShifting() {
        return shiftTimer > 0;
    }
}