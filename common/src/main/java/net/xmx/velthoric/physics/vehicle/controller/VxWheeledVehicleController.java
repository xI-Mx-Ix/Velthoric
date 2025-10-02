/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.controller;

import com.github.stephengold.joltjni.WheeledVehicleController;

/**
 * Manages driver input for a standard wheeled vehicle, acting as a wrapper
 * around Jolt's WheeledVehicleController.
 *
 * @author xI-Mx-Ix
 */
public class VxWheeledVehicleController {

    private final WheeledVehicleController joltController;

    private float forwardInput = 0.0f;
    private float rightInput = 0.0f;
    private float brakeInput = 0.0f;
    private float handBrakeInput = 0.0f;

    public VxWheeledVehicleController(WheeledVehicleController joltController) {
        this.joltController = joltController;
    }

    public void setInput(float forward, float right, float brake, float handBrake) {
        this.forwardInput = forward;
        this.rightInput = right;
        this.brakeInput = brake;
        this.handBrakeInput = handBrake;
        this.joltController.setDriverInput(forward, right, brake, handBrake);
    }

    public float getForwardInput() {
        return forwardInput;
    }

    public float getRightInput() {
        return rightInput;
    }

    public float getBrakeInput() {
        return brakeInput;
    }

    public float getHandBrakeInput() {
        return handBrakeInput;
    }

    public WheeledVehicleController getJoltController() {
        return joltController;
    }
}