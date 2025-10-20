package net.xmx.velthoric.physics.vehicle.controller;

import com.github.stephengold.joltjni.MotorcycleController;

/**
 * Manages driver input for a motorcycle vehicle, serving as a wrapper
 * around Jolt's {@link MotorcycleController}. It enables the lean controller
 * to use Jolt's well-tuned default parameters for stability.
 *
 * @author xI-Mx-Ix
 */
public class VxMotorcycleController {

    private final MotorcycleController joltController;

    private float forwardInput = 0.0f;
    private float rightInput = 0.0f;
    private float brakeInput = 0.0f;
    private float handBrakeInput = 0.0f;

    /**
     * Constructs a new motorcycle controller.
     *
     * @param joltController The underlying Jolt physics controller.
     */
    public VxMotorcycleController(MotorcycleController joltController) {
        this.joltController = joltController;

        // Enable the lean controller. This provides the crucial self-righting force
        // that keeps the two-wheeled vehicle stable.
        this.joltController.enableLeanController(true);
    }

    /**
     * Sets the driver's inputs for controlling the motorcycle.
     *
     * @param forward   The forward/reverse throttle input, ranging from -1.0 to 1.0.
     * @param right     The steering input, ranging from -1.0 (left) to 1.0 (right).
     * @param brake     The brake input, ranging from 0.0 to 1.0.
     * @param handBrake The handbrake input, ranging from 0.0 to 1.0.
     */
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

    public MotorcycleController getJoltController() {
        return joltController;
    }
}