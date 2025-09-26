/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.wheel;

/**
 * A data-transfer object holding the fully interpolated state of a single vehicle wheel
 * for one render frame.
 *
 * @param rotationAngle    The interpolated rotation angle around the wheel's axle.
 * @param steerAngle       The interpolated steering angle around the steering axis.
 * @param suspensionLength The interpolated current length of the suspension spring.
 * @author xI-Mx-Ix
 */
public record WheelRenderState(float rotationAngle, float steerAngle, float suspensionLength) {
}