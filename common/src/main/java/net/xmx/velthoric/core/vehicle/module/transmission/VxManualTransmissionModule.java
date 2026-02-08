/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.vehicle.module.transmission;

import com.github.stephengold.joltjni.WheeledVehicleController;
import net.xmx.velthoric.core.mounting.input.VxMountInput;

/**
 * Implements a sequential manual transmission.
 * <p>
 * This module requires explicit driver input to change gears. It simulates a clutch
 * disengagement during the gear shift duration defined in the configuration.
 * Input mapping (Gas vs. Brake) changes dynamically based on the selected gear.
 *
 * @author xI-Mx-Ix
 */
public class VxManualTransmissionModule implements VxTransmissionModule {

    private final float switchTime;
    private final int maxGears;

    private int currentGear = 1;
    private float shiftTimer = 0.0f;

    private boolean wasShiftUpPressed = false;
    private boolean wasShiftDownPressed = false;

    /**
     * Creates a new manual transmission module.
     *
     * @param switchTime The delay in seconds between initiating a shift and the gear engaging.
     * @param maxGears   The total number of forward gears available.
     */
    public VxManualTransmissionModule(float switchTime, int maxGears) {
        this.switchTime = switchTime;
        this.maxGears = maxGears;
    }

    @Override
    public void update(float dt, VxMountInput input, float speed, WheeledVehicleController controller) {
        // Update Shift Timer
        if (shiftTimer > 0) {
            shiftTimer -= dt;
        }

        // Handle Shift Inputs
        boolean shiftUp = input.hasAction(VxMountInput.FLAG_SHIFT_UP);
        if (shiftUp && !wasShiftUpPressed && currentGear < maxGears) {
            currentGear++;
            if (currentGear == 0) currentGear = 1; // Skip Neutral on sequential up-shift
            shiftTimer = switchTime;
        }
        wasShiftUpPressed = shiftUp;

        boolean shiftDown = input.hasAction(VxMountInput.FLAG_SHIFT_DOWN);
        if (shiftDown && !wasShiftDownPressed && currentGear > -1) {
            currentGear--;
            if (currentGear == 0) currentGear = -1; // Skip Neutral on sequential down-shift
            shiftTimer = switchTime;
        }
        wasShiftDownPressed = shiftDown;

        // Apply Gear and Clutch state to Jolt
        // Clutch is 0 (disengaged) while shifting, 1 (engaged) otherwise.
        float clutchFriction = (shiftTimer > 0) ? 0.0f : 1.0f;
        controller.getTransmission().set(currentGear, clutchFriction);

        // Map Input to Physics
        float forwardInput = 0.0f;
        float brakeInput = 0.0f;
        float rawThrottle = input.getForwardAmount();

        if (currentGear > 0) {
            // Forward Gears: Positive input is Throttle, Negative is Brake
            if (rawThrottle >= 0) {
                forwardInput = rawThrottle;
            } else {
                brakeInput = Math.abs(rawThrottle);
            }
        } else if (currentGear == -1) {
            // Reverse Gear: Negative input is Throttle, Positive is Brake
            if (rawThrottle <= 0) {
                forwardInput = Math.abs(rawThrottle);
            } else {
                brakeInput = rawThrottle;
            }
        } else {
            // Neutral: Input revs engine but applies no load
            forwardInput = Math.abs(rawThrottle);
        }

        float handBrake = input.hasAction(VxMountInput.FLAG_HANDBRAKE) ? 1.0f : 0.0f;
        controller.setDriverInput(forwardInput, input.getRightAmount(), brakeInput, handBrake);
    }

    @Override
    public int getDisplayGear() {
        return currentGear;
    }
}