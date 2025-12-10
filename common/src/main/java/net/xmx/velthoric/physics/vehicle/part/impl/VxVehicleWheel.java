/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.part.impl;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.WheelSettingsWv;
import com.github.stephengold.joltjni.operator.Op;
import dev.architectury.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.xmx.velthoric.math.VxOBB;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.body.client.VxRenderState;
import net.xmx.velthoric.physics.vehicle.VxVehicle;
import net.xmx.velthoric.physics.vehicle.part.VxPart;
import org.joml.Vector3f;

/**
 * Wraps Jolt's WheelSettingsWv and handles dynamic state interpolation.
 * This class ensures that the physics state (suspension, steering) is reflected
 * in both the visual rendering and the interaction hitboxes.
 *
 * @author xI-Mx-Ix
 */
public class VxVehicleWheel extends VxPart {

    private final WheelSettingsWv settings;
    private final boolean isPowered;
    private final boolean isSteerable;

    private float rotationAngle;
    private float steerAngle;
    private float suspensionLength;
    private boolean hasContact;

    private float prevRotation, targetRotation;
    private float prevSteer, targetSteer;
    private float prevSuspension, targetSuspension;

    /**
     * Constructs a wheel part.
     *
     * @param vehicle     The parent vehicle.
     * @param name        The unique name (e.g., "wheel_0").
     * @param settings    The Jolt wheel settings.
     * @param isPowered   If the wheel is connected to the engine.
     * @param isSteerable If the wheel can steer.
     */
    public VxVehicleWheel(VxVehicle vehicle, String name, WheelSettingsWv settings, boolean isPowered, boolean isSteerable) {
        super(vehicle, name,
                new Vector3f(settings.getPosition().getX(), settings.getPosition().getY(), settings.getPosition().getZ()),
                // Define AABB based on wheel orientation.
                // Car wheels are cylinders aligned along the local X-axis.
                // X = Width, Y = Radius, Z = Radius.
                new AABB(
                        -settings.getWidth() / 2, -settings.getRadius(), -settings.getRadius(),
                        settings.getWidth() / 2,  settings.getRadius(),  settings.getRadius()
                )
        );
        this.settings = settings;
        this.isPowered = isPowered;
        this.isSteerable = isSteerable;
        this.suspensionLength = (settings.getSuspensionMinLength() + settings.getSuspensionMaxLength()) * 0.5f;
    }

    @Override
    public boolean interact(Player player) {
        if (super.interact(player)) {
            return true;
        }
        // Interaction logic on server side (e.g. check tire status)
        if (!player.level().isClientSide) {
            return true;
        }
        return false;
    }

    /**
     * Calculates the world-space Oriented Bounding Box (OBB) for this wheel.
     * Unlike static parts, the wheel's position varies based on suspension compression,
     * and its rotation changes based on the steering angle.
     *
     * @param vehicleState The current interpolated render state of the parent vehicle.
     * @return The OBB representing the wheel's current dynamic position and orientation.
     */
    @Override
    public VxOBB getGlobalOBB(VxRenderState vehicleState) {
        // 1. Get the interpolated vehicle transform
        RVec3 vehiclePos = vehicleState.transform.getTranslation();
        Quat vehicleRot = vehicleState.transform.getRotation();

        // 2. Determine current dynamic values (Suspension & Steering)
        float currentSusp = this.suspensionLength;
        float currentSteer = this.steerAngle;

        // If on client, use interpolated values to match the renderer.
        // We use FabricLoader to check the environment type safely, as VxPhysicsWorld is server-only.
        if (Platform.getEnv() == EnvType.CLIENT) {
            float partialTicks = Minecraft.getInstance().getFrameTime();
            currentSusp = Mth.lerp(partialTicks, prevSuspension, targetSuspension);
            currentSteer = Mth.lerp(partialTicks, prevSteer, targetSteer);
        }

        // 3. Calculate the local offset caused by suspension
        // The suspension usually acts along the configured direction (typically Down/Y-).
        Vec3 suspDir = settings.getSuspensionDirection();
        Vector3f suspOffset = new Vector3f(suspDir.getX(), suspDir.getY(), suspDir.getZ());
        suspOffset.mul(currentSusp);

        // 4. Combine the static hardpoint position with the dynamic suspension offset
        Vector3f totalLocalPos = new Vector3f(this.localPosition).add(suspOffset);

        // 5. Transform this local position to world space
        // WorldPos = VehiclePos + (VehicleRot * TotalLocalPos)
        RVec3 worldOffset = new RVec3(totalLocalPos.x, totalLocalPos.y, totalLocalPos.z);
        worldOffset.rotateInPlace(vehicleRot);
        RVec3 finalPos = Op.plus(vehiclePos, worldOffset);

        // 6. Calculate the final rotation
        // The wheel rotates around the steering axis relative to the vehicle body.
        Vec3 steerAxis = settings.getSteeringAxis();
        // Create quaternion for steering angle around the steering axis using sRotation
        Quat steerQuat = Quat.sRotation(steerAxis, currentSteer);

        // Final Rotation = Vehicle Rotation * Steering Rotation
        Quat finalRot = Op.star(vehicleRot, steerQuat);

        // 7. Return the new OBB
        return new VxOBB(new VxTransform(finalPos, finalRot), this.localAABB);
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

    public WheelSettingsWv getSettings() {
        return settings;
    }

    public boolean isPowered() {
        return isPowered;
    }

    public boolean isSteerable() {
        return isSteerable;
    }

    // Getters for Packet Construction
    public float getRotationAngle() {
        return rotationAngle;
    }

    public float getSteerAngle() {
        return steerAngle;
    }

    public float getSuspensionLength() {
        return suspensionLength;
    }

    // Getters for Renderer interpolation
    @Environment(EnvType.CLIENT)
    public float getPrevRotation() {
        return prevRotation;
    }

    @Environment(EnvType.CLIENT)
    public float getTargetRotation() {
        return targetRotation;
    }

    @Environment(EnvType.CLIENT)
    public float getPrevSteer() {
        return prevSteer;
    }

    @Environment(EnvType.CLIENT)
    public float getTargetSteer() {
        return targetSteer;
    }

    @Environment(EnvType.CLIENT)
    public float getPrevSuspension() {
        return prevSuspension;
    }

    @Environment(EnvType.CLIENT)
    public float getTargetSuspension() {
        return targetSuspension;
    }
}