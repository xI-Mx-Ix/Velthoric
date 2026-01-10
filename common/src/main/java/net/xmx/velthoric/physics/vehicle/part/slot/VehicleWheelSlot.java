/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.part.slot;

import org.joml.Vector3f;

/**
 * Represents a hard-point on the vehicle chassis where a wheel can be attached.
 * <p>
 * This class defines the position relative to the chassis, the drivetrain connection
 * (powered/steerable), and the suspension geometry. It does <b>not</b> define the
 * dimensions or visual model of the tire itself.
 *
 * @author xI-Mx-Ix
 */
public class VehicleWheelSlot {

    /**
     * The unique name of this slot (e.g., "front_left").
     */
    private final String name;

    /**
     * The local position of the wheel attachment point relative to the chassis center of mass.
     */
    private final Vector3f position;

    /**
     * Whether this wheel is connected to the engine (drive wheel).
     */
    private boolean isPowered;

    /**
     * Whether this wheel responds to steering input.
     */
    private boolean isSteerable;

    // --- Suspension Physics ---

    /**
     * The minimum length of the suspension spring in meters.
     */
    private float suspensionMinLength = 0.3f;

    /**
     * The maximum length of the suspension spring in meters.
     */
    private float suspensionMaxLength = 0.5f;

    /**
     * The frequency of the suspension spring in Hertz (Hz).
     * Controls how bouncy the spring is.
     */
    private float suspensionFrequency = 2.0f;

    /**
     * The damping ratio of the suspension.
     * Controls how quickly oscillations settle.
     */
    private float suspensionDamping = 0.5f;

    // --- Wheel Constraints ---

    /**
     * The maximum torque the brakes can apply to this wheel.
     */
    private float maxBrakeTorque = 1500.0f;

    /**
     * The maximum angle this wheel can steer, in degrees.
     */
    private float maxSteerAngleDegrees = 30.0f;

    /**
     * Constructs a new wheel slot.
     *
     * @param name     The unique name of the slot.
     * @param position The position relative to the chassis.
     */
    public VehicleWheelSlot(String name, Vector3f position) {
        this.name = name;
        this.position = position;
    }

    /**
     * Sets whether this wheel is powered by the engine.
     *
     * @param powered True if powered, false otherwise.
     * @return This instance for chaining.
     */
    public VehicleWheelSlot setPowered(boolean powered) {
        this.isPowered = powered;
        return this;
    }

    /**
     * Sets whether this wheel can steer.
     *
     * @param steerable True if steerable, false otherwise.
     * @return This instance for chaining.
     */
    public VehicleWheelSlot setSteerable(boolean steerable) {
        this.isSteerable = steerable;
        return this;
    }

    /**
     * Configures the suspension properties for this slot.
     *
     * @param minLen The minimum length in meters.
     * @param maxLen The maximum length in meters.
     * @param freq   The frequency in Hz.
     * @param damp   The damping ratio.
     * @return This instance for chaining.
     */
    public VehicleWheelSlot setSuspension(float minLen, float maxLen, float freq, float damp) {
        this.suspensionMinLength = minLen;
        this.suspensionMaxLength = maxLen;
        this.suspensionFrequency = freq;
        this.suspensionDamping = damp;
        return this;
    }

    /**
     * Sets the maximum brake torque for this wheel.
     *
     * @param torque The brake torque in Nm.
     * @return This instance for chaining.
     */
    public VehicleWheelSlot setBrakeTorque(float torque) {
        this.maxBrakeTorque = torque;
        return this;
    }

    /**
     * Sets the maximum steering angle.
     *
     * @param degrees The angle in degrees.
     * @return This instance for chaining.
     */
    public VehicleWheelSlot setMaxSteerAngle(float degrees) {
        this.maxSteerAngleDegrees = degrees;
        return this;
    }

    // --- Getters ---

    /**
     * Gets the name of the slot.
     *
     * @return The slot name.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the position of the slot.
     *
     * @return The position vector.
     */
    public Vector3f getPosition() {
        return position;
    }

    /**
     * Checks if the wheel is powered.
     *
     * @return True if powered.
     */
    public boolean isPowered() {
        return isPowered;
    }

    /**
     * Checks if the wheel is steerable.
     *
     * @return True if steerable.
     */
    public boolean isSteerable() {
        return isSteerable;
    }

    /**
     * Gets the minimum suspension length.
     *
     * @return The length in meters.
     */
    public float getSuspensionMinLength() {
        return suspensionMinLength;
    }

    /**
     * Gets the maximum suspension length.
     *
     * @return The length in meters.
     */
    public float getSuspensionMaxLength() {
        return suspensionMaxLength;
    }

    /**
     * Gets the suspension frequency.
     *
     * @return The frequency in Hz.
     */
    public float getSuspensionFrequency() {
        return suspensionFrequency;
    }

    /**
     * Gets the suspension damping ratio.
     *
     * @return The damping ratio.
     */
    public float getSuspensionDamping() {
        return suspensionDamping;
    }

    /**
     * Gets the maximum brake torque.
     *
     * @return The torque in Nm.
     */
    public float getMaxBrakeTorque() {
        return maxBrakeTorque;
    }

    /**
     * Gets the maximum steering angle.
     *
     * @return The angle in degrees.
     */
    public float getMaxSteerAngleDegrees() {
        return maxSteerAngleDegrees;
    }
}