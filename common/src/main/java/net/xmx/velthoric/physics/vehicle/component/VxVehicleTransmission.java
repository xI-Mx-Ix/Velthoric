/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.component;

/**
 * Handles gear shifting logic and ratios.
 *
 * @author xI-Mx-Ix
 */
public class VxVehicleTransmission {

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
     */
    public VxVehicleTransmission(float[] forwardRatios, float reverseRatio) {
        this.forwardRatios = forwardRatios;
        this.reverseRatio = reverseRatio;
        this.shiftTime = 0.4f; // Time in seconds to change gears
    }

    public void update(float dt) {
        if (shiftTimer > 0) {
            shiftTimer -= dt;
        }
    }

    public void shiftUp() {
        if (shiftTimer > 0) return;
        if (currentGear < forwardRatios.length) {
            currentGear++;
            shiftTimer = shiftTime;
        }
    }

    public void shiftDown() {
        if (shiftTimer > 0) return;
        if (currentGear > -1) {
            currentGear--;
            shiftTimer = shiftTime;
        }
    }

    /**
     * Gets the current effective gear ratio.
     *
     * @return The ratio, or 0.0 if in neutral or actively shifting (clutch in).
     */
    public float getCurrentRatio() {
        if (shiftTimer > 0) return 0.0f; // Simulate clutch depression during shift
        if (currentGear == 0) return 0.0f;
        if (currentGear == -1) return reverseRatio;
        return forwardRatios[currentGear - 1];
    }

    public int getGear() {
        return currentGear;
    }

    public void setSynchronizedGear(int gear) {
        this.currentGear = gear;
    }

    public boolean isShifting() {
        return shiftTimer > 0;
    }
}