/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.component;

import com.github.stephengold.joltjni.WheelSettingsWv;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Mth;

/**
 * Wraps Jolt's WheelSettingsWv and handles dynamic state interpolation.
 * This replaces the need for an external render state object.
 *
 * @author xI-Mx-Ix
 */
public class VxVehicleWheel {

    private final WheelSettingsWv settings;
    private final boolean isPowered;
    private final boolean isSteerable;

    // --- Physics State (Server) ---
    private float rotationAngle;
    private float steerAngle;
    private float suspensionLength;
    private boolean hasContact;

    // --- Interpolation State (Client) ---
    private float prevRotation, targetRotation;
    private float prevSteer, targetSteer;
    private float prevSuspension, targetSuspension;

    public VxVehicleWheel(WheelSettingsWv settings, boolean isPowered, boolean isSteerable) {
        this.settings = settings;
        this.isPowered = isPowered;
        this.isSteerable = isSteerable;
        this.suspensionLength = (settings.getSuspensionMinLength() + settings.getSuspensionMaxLength()) * 0.5f;
    }

    /**
     * Updates the dynamic physics state from the Jolt simulation.
     */
    public void updatePhysicsState(float rotation, float steer, float suspension, boolean contact) {
        this.rotationAngle = rotation;
        this.steerAngle = steer;
        this.suspensionLength = suspension;
        this.hasContact = contact;
    }

    /**
     * Updates the client-side target state for interpolation.
     */
    @Environment(EnvType.CLIENT)
    public void updateClientTarget(float rotation, float steer, float suspension) {
        this.prevRotation = this.targetRotation;
        this.prevSteer = this.targetSteer;
        this.prevSuspension = this.targetSuspension;

        this.targetRotation = rotation;
        this.targetSteer = steer;
        this.targetSuspension = suspension;
    }

    /**
     * Gets the interpolated rotation angle for rendering.
     */
    @Environment(EnvType.CLIENT)
    public float getRenderRotation(float partialTicks) {
        return Mth.lerp(partialTicks, prevRotation, targetRotation);
    }

    /**
     * Gets the interpolated steering angle for rendering.
     */
    @Environment(EnvType.CLIENT)
    public float getRenderSteer(float partialTicks) {
        return Mth.lerp(partialTicks, prevSteer, targetSteer);
    }

    /**
     * Gets the interpolated suspension length for rendering.
     */
    @Environment(EnvType.CLIENT)
    public float getRenderSuspension(float partialTicks) {
        return Mth.lerp(partialTicks, prevSuspension, targetSuspension);
    }

    public WheelSettingsWv getSettings() {
        return settings;
    }

    public boolean isPowered() {
        return isPowered;
    }

    public boolean isSteerable() {
        return isSteerable;
    }

    // Getters for Physics State (used for Network Packet construction)
    public float getRotationAngle() {
        return rotationAngle;
    }

    public float getSteerAngle() {
        return steerAngle;
    }

    public float getSuspensionLength() {
        return suspensionLength;
    }
}