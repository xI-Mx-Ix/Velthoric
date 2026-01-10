/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.sync;

/**
 * Represents the synchronized physical state of a single wheel at a specific tick.
 * <p>
 * This record is used to transport data from the server physics simulation to clients
 * for visual interpolation (rendering).
 *
 * @param rotation   The rotation angle of the wheel around its axle (rolling) in radians.
 * @param steer      The steering angle of the wheel around the vertical axis in radians.
 * @param suspension The current compression length of the suspension in meters.
 * @author xI-Mx-Ix
 */
public record VxVehicleWheelState(
    float rotation,
    float steer,
    float suspension
) {
    /**
     * A default state representing a resting wheel.
     */
    public static final VxVehicleWheelState DEFAULT = new VxVehicleWheelState(0.0f, 0.0f, 0.0f);
}