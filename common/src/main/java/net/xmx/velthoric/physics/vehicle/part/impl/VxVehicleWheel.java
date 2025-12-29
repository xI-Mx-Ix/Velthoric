/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.part.impl;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.AABB;
import net.xmx.velthoric.math.VxOBB;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.body.client.VxRenderState;
import net.xmx.velthoric.physics.vehicle.VxVehicle;
import net.xmx.velthoric.physics.vehicle.data.slot.VehicleWheelSlot;
import net.xmx.velthoric.physics.vehicle.data.component.VxWheelDefinition;
import net.xmx.velthoric.physics.vehicle.part.VxPart;
import org.joml.Vector3f;

/**
 * Represents a dynamic wheel part attached to the vehicle.
 * <p>
 * This class serves as the bridge between the logical slot (attachment point on chassis)
 * and the specific wheel item (definition) currently installed. It handles both
 * the physics state updates (from Jolt) and the client-side interpolation for rendering.
 *
 * @author xI-Mx-Ix
 */
public class VxVehicleWheel extends VxPart {

    /**
     * The Jolt physics settings for this wheel.
     * Contains suspension spring data, friction, and steering limits.
     */
    private final WheelSettingsWv settings;

    /**
     * The chassis slot configuration that created this wheel.
     * Defines position on the car and drivetrain capabilities (powered/steerable).
     */
    private final VehicleWheelSlot slot;

    /**
     * The current definition of the wheel type.
     * Defines physical dimensions (radius, width) and visual properties (model).
     */
    private VxWheelDefinition definition;

    // --- Physics State (Updated from Jolt) ---

    /**
     * The current rotation angle of the wheel around its axle (rolling) in radians.
     */
    private float rotationAngle;

    /**
     * The current steering angle of the wheel around the vertical axis in radians.
     */
    private float steerAngle;

    /**
     * The current length of the suspension spring in meters.
     */
    private float suspensionLength;

    /**
     * Indicates whether the wheel is currently touching the ground.
     */
    private boolean hasContact;

    // --- Client Interpolation State ---

    private float prevRotation;
    private float targetRotation;
    private float prevSteer;
    private float targetSteer;
    private float prevSuspension;
    private float targetSuspension;

    /**
     * Constructs a new vehicle wheel part.
     *
     * @param vehicle    The parent vehicle.
     * @param name       The name of the part (usually the slot name).
     * @param settings   The Jolt physics settings, pre-configured with slot data.
     * @param slot       The slot configuration.
     * @param definition The initial wheel definition.
     */
    public VxVehicleWheel(VxVehicle vehicle, String name, WheelSettingsWv settings, VehicleWheelSlot slot, VxWheelDefinition definition) {
        super(vehicle, name,
                new Vector3f(settings.getPosition().getX(), settings.getPosition().getY(), settings.getPosition().getZ()),
                createWheelAABB(definition)
        );
        this.settings = settings;
        this.slot = slot;
        this.definition = definition;

        // Initialize suspension length to the resting position (midpoint) to prevent visual popping on spawn
        this.suspensionLength = (settings.getSuspensionMinLength() + settings.getSuspensionMaxLength()) * 0.5f;
        this.targetSuspension = this.suspensionLength;
        this.prevSuspension = this.suspensionLength;
    }

    /**
     * Helper method to create an Axis-Aligned Bounding Box (AABB) from a wheel definition.
     *
     * @param def The wheel definition.
     * @return The calculated AABB centered at the wheel's origin.
     */
    private static AABB createWheelAABB(VxWheelDefinition def) {
        float r = def.radius();
        float w = def.width();
        // AABB centered at the wheel's origin.
        // X-axis is width (axle), Y and Z are radius/height.
        return new AABB(-w / 2, -r, -r, w / 2, r, r);
    }

    /**
     * Calculates the world-space Oriented Bounding Box (OBB) for this wheel.
     * <p>
     * Unlike static parts, the wheel OBB moves relative to the chassis based on
     * suspension compression and rotates based on steering.
     * <p>
     * <b>Note:</b> This implementation explicitly ignores the wheel's rolling rotation (RPM)
     * for the hitbox, so the box stays aligned with the ground even if the wheel spins.
     *
     * @param vehicleState The current render state of the parent vehicle.
     * @return The OBB in world space adjusting for suspension and steering.
     */
    @Override
    public VxOBB getGlobalOBB(VxRenderState vehicleState) {
        // 1. Get Vehicle Transform
        RVec3 vehiclePos = vehicleState.transform.getTranslation();
        Quat vehicleRot = vehicleState.transform.getRotation();

        // 2. Determine local state (Suspension & Steer)
        // If we are on the client, we use the interpolation target for smooth hitboxes.
        // If on server, we use the physics state.
        float effectiveSuspension = vehicle.getPhysicsWorld() == null ? this.targetSuspension : this.suspensionLength;
        float effectiveSteer = vehicle.getPhysicsWorld() == null ? this.targetSteer : this.steerAngle;

        // 3. Calculate Local Position relative to chassis (Hardpoint + SuspensionDir * Length)
        Vec3 hardPoint = settings.getPosition();
        Vec3 suspDir = settings.getSuspensionDirection();

        // offset = SuspDir * Length
        Vec3 suspOffset = Op.star(suspDir, effectiveSuspension);
        // localPos = HardPoint + Offset
        Vec3 localPos = Op.plus(hardPoint, suspOffset);

        // 4. Transform Local Position to World Position
        // rotatedPos = VehicleRot * localPos
        Vec3 rotatedPos = Op.star(vehicleRot, localPos);
        // worldPos = VehiclePos + rotatedPos
        RVec3 worldPos = Op.plus(vehiclePos, rotatedPos);

        // 5. Calculate Rotation (Vehicle Rotation * Steering Rotation)
        // We do NOT include the rolling rotation (rotationAngle) here.
        Vec3 steerAxis = settings.getSteeringAxis();
        // Fixed: Use sRotation instead of fromAxisAngle
        Quat steerRot = Quat.sRotation(steerAxis, effectiveSteer);

        // Final Rot = VehicleRot * SteerRot
        Quat worldRot = Op.star(vehicleRot, steerRot);

        // 6. Return OBB
        return new VxOBB(new VxTransform(worldPos, worldRot), this.localAABB);
    }

    /**
     * Updates the installed wheel definition.
     * <p>
     * This changes the reference used by the renderer to determine which model to draw.
     * Note: This does not automatically update the Jolt physics settings; that must be handled
     * by the vehicle controller logic if swapping at runtime.
     *
     * @param newDef The new wheel definition.
     */
    public void setDefinition(VxWheelDefinition newDef) {
        this.definition = newDef;
    }

    /**
     * Gets the current wheel definition.
     *
     * @return The definition (visuals/dimensions).
     */
    public VxWheelDefinition getDefinition() {
        return definition;
    }

    /**
     * Gets the chassis slot configuration.
     *
     * @return The slot data.
     */
    public VehicleWheelSlot getSlot() {
        return slot;
    }

    /**
     * Gets the Jolt wheel settings.
     *
     * @return The settings object.
     */
    public WheelSettingsWv getSettings() {
        return settings;
    }

    /**
     * Checks if this wheel is powered by the engine.
     * Convenience method delegating to the slot configuration.
     *
     * @return True if powered.
     */
    public boolean isPowered() {
        return slot.isPowered();
    }

    /**
     * Checks if this wheel is steerable.
     * Convenience method delegating to the slot configuration.
     *
     * @return True if steerable.
     */
    public boolean isSteerable() {
        return slot.isSteerable();
    }

    // --- Physics Update Methods ---

    /**
     * Updates the local physics state with data from the Jolt simulation.
     * This is typically called every physics tick by the parent vehicle.
     *
     * @param rotation   The total rotation angle in radians.
     * @param steer      The steering angle in radians.
     * @param suspension The current suspension length in meters.
     * @param contact    True if the wheel is touching the ground.
     */
    public void updatePhysicsState(float rotation, float steer, float suspension, boolean contact) {
        this.rotationAngle = rotation;
        this.steerAngle = steer;
        this.suspensionLength = suspension;
        this.hasContact = contact;
    }

    // --- Client Interpolation Methods ---

    /**
     * Updates the client-side interpolation targets.
     * This method is called when a network packet containing new vehicle state arrives
     * on the client. It pushes the previous target to "prev" and sets the new "target".
     *
     * @param rotation   The target rotation angle.
     * @param steer      The target steering angle.
     * @param suspension The target suspension length.
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

    // --- Physics State Getters (For Networking) ---

    /**
     * Gets the current rotation angle of the wheel (rolling).
     *
     * @return Angle in radians.
     */
    public float getRotationAngle() {
        return rotationAngle;
    }

    /**
     * Gets the current steering angle of the wheel.
     *
     * @return Angle in radians.
     */
    public float getSteerAngle() {
        return steerAngle;
    }

    /**
     * Gets the current suspension length.
     *
     * @return Length in meters.
     */
    public float getSuspensionLength() {
        return suspensionLength;
    }

    // --- Client Getters ---

    /**
     * Gets the rotation angle from the previous frame.
     * Used for linear interpolation during rendering.
     *
     * @return Angle in radians.
     */
    @Environment(EnvType.CLIENT)
    public float getPrevRotation() {
        return prevRotation;
    }

    /**
     * Gets the target rotation angle for the current frame.
     * Used for linear interpolation during rendering.
     *
     * @return Angle in radians.
     */
    @Environment(EnvType.CLIENT)
    public float getTargetRotation() {
        return targetRotation;
    }

    /**
     * Gets the steering angle from the previous frame.
     *
     * @return Angle in radians.
     */
    @Environment(EnvType.CLIENT)
    public float getPrevSteer() {
        return prevSteer;
    }

    /**
     * Gets the target steering angle for the current frame.
     *
     * @return Angle in radians.
     */
    @Environment(EnvType.CLIENT)
    public float getTargetSteer() {
        return targetSteer;
    }

    /**
     * Gets the suspension length from the previous frame.
     *
     * @return Length in meters.
     */
    @Environment(EnvType.CLIENT)
    public float getPrevSuspension() {
        return prevSuspension;
    }

    /**
     * Gets the target suspension length for the current frame.
     *
     * @return Length in meters.
     */
    @Environment(EnvType.CLIENT)
    public float getTargetSuspension() {
        return targetSuspension;
    }
}