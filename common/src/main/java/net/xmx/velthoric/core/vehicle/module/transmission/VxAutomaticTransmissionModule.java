/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.vehicle.module.transmission;

import com.github.stephengold.joltjni.WheeledVehicleController;
import net.xmx.velthoric.core.mounting.input.VxMountInput;

/**
 * Implements an automatic transmission with arcade-style reversing.
 * <p>
 * This module relies on the Jolt physics engine to handle shifting between forward gears
 * based on RPM. It manages the high-level state transition between "Drive" and "Reverse"
 * when the vehicle is stopped and the driver presses the opposing input.
 *
 * @author xI-Mx-Ix
 */
public class VxAutomaticTransmissionModule implements VxTransmissionModule {

    private static final float DIRECTION_SWITCH_THRESHOLD_MS = 0.5f;

    /**
     * 1 for Drive, -1 for Reverse.
     */
    private int driveDirection = 1;

    // Stores the last known gear from Jolt for display purposes
    private int displayGear = 1;

    @Override
    public void update(float dt, VxMountInput input, float speed, WheeledVehicleController controller) {
        float throttleInput = input.getForwardAmount();
        float absSpeed = Math.abs(speed);

        // Handle Direction Switching at Standstill
        if (absSpeed < DIRECTION_SWITCH_THRESHOLD_MS) {
            if (throttleInput < -0.1f) {
                driveDirection = -1;
            } else if (throttleInput > 0.1f) {
                driveDirection = 1;
            }
        }

        float forwardOutput = 0.0f;
        float brakeOutput = 0.0f;

        // Map Inputs based on Drive Direction
        if (driveDirection == 1) {
            // Drive Mode
            if (throttleInput >= 0) {
                forwardOutput = throttleInput;
            } else {
                brakeOutput = Math.abs(throttleInput);
            }
            
            // Allow Jolt to manage forward gears, but ensure we aren't stuck in Reverse gear logic
            // If the controller is currently in reverse gear from a previous state, Jolt auto-logic
            // should correct it once forward input is received.
        } else {
            // Reverse Mode
            if (throttleInput <= 0) {
                forwardOutput = Math.abs(throttleInput);
                // Force Jolt into Reverse Gear (-1) for logic consistency
                controller.getTransmission().set(-1, 1.0f);
            } else {
                brakeOutput = throttleInput;
                controller.getTransmission().set(-1, 1.0f);
            }
        }

        float handBrake = input.hasAction(VxMountInput.FLAG_HANDBRAKE) ? 1.0f : 0.0f;
        controller.setDriverInput(forwardOutput, input.getRightAmount(), brakeOutput, handBrake);

        // Update display gear from internal Jolt state
        this.displayGear = controller.getTransmission().getCurrentGear();
    }

    @Override
    public int getDisplayGear() {
        return displayGear;
    }
}