/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.component;

import net.minecraft.util.Mth;

/**
 * A helper class to manage smooth steering interpolation for vehicles.
 * It gradually moves a current steering value towards a target value each tick,
 * preventing jerky, instantaneous changes in direction.
 *
 * @author xI-Mx-Ix
 */
public class VxSteering {

    private final float steerSpeed;
    private float currentAngle;
    private float targetAngle;

    /**
     * Constructs a new steering helper.
     *
     * @param steerSpeed The rate at which the steering angle changes towards the target, in units per second.
     */
    public VxSteering(float steerSpeed) {
        this.steerSpeed = steerSpeed;
        this.currentAngle = 0.0f;
        this.targetAngle = 0.0f;
    }

    /**
     * Sets the desired target steering angle. The value is clamped between -1.0 (full left) and 1.0 (full right).
     *
     * @param targetAngle The target angle, typically from player input.
     */
    public void setTargetAngle(float targetAngle) {
        this.targetAngle = Mth.clamp(targetAngle, -1.0f, 1.0f);
    }

    /**
     * Updates the current steering angle by moving it towards the target.
     * This method should be called once per physics tick to ensure smooth interpolation.
     *
     * @param deltaTime The time elapsed since the last tick (e.g., 1.0 / 20.0 for a 20 TPS server).
     */
    public void update(float deltaTime) {
        // Only update if there is a significant difference to avoid floating point inaccuracies.
        if (Mth.abs(currentAngle - targetAngle) > 0.001f) {
            float step = steerSpeed * deltaTime;
            if (targetAngle > currentAngle) {
                currentAngle = Math.min(currentAngle + step, targetAngle);
            } else if (targetAngle < currentAngle) {
                currentAngle = Math.max(currentAngle - step, targetAngle);
            }
        }
    }

    /**
     * Resets the steering to a neutral (center) position immediately.
     */
    public void reset() {
        this.currentAngle = 0.0f;
        this.targetAngle = 0.0f;
    }

    /**
     * Gets the current, interpolated steering angle.
     *
     * @return The current steering angle, between -1.0 and 1.0.
     */
    public float getCurrentAngle() {
        return currentAngle;
    }
}