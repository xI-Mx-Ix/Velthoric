/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.wheel;

import com.github.stephengold.joltjni.WheelSettingsWv;

/**
 * A wrapper for Jolt's WheelSettingsWv that also holds dynamic, per-frame state
 * for rendering and logic purposes.
 *
 * @author xI-Mx-Ix
 */
public class VxWheel {

    private final WheelSettingsWv settings;

    // --- Dynamic State (updated each physics tick) ---
    private float rotationAngle = 0.0f;
    private float steerAngle = 0.0f;
    private float suspensionLength = 0.0f;
    private boolean hasContact = false;

    public VxWheel(WheelSettingsWv settings) {
        this.settings = settings;
        this.suspensionLength = (settings.getSuspensionMaxLength() + settings.getSuspensionMinLength()) * 0.5f;
    }

    public WheelSettingsWv getSettings() {
        return settings;
    }

    // --- Getters for dynamic state ---

    public float getRotationAngle() {
        return rotationAngle;
    }

    public float getSteerAngle() {
        return steerAngle;
    }

    public float getSuspensionLength() {
        return suspensionLength;
    }

    public boolean hasContact() {
        return hasContact;
    }

    // --- Setters for dynamic state (called by VxVehicle during physics tick) ---

    public void setRotationAngle(float rotationAngle) {
        this.rotationAngle = rotationAngle;
    }

    public void setSteerAngle(float steerAngle) {
        this.steerAngle = steerAngle;
    }

    public void setSuspensionLength(float suspensionLength) {
        this.suspensionLength = suspensionLength;
    }

    public void setHasContact(boolean hasContact) {
        this.hasContact = hasContact;
    }
}