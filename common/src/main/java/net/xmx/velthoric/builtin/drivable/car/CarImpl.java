/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.drivable.car;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import com.github.stephengold.joltjni.enumerate.ETransmissionMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.AABB;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.type.factory.VxRigidBodyFactory;
import net.xmx.velthoric.physics.mounting.seat.VxSeat;
import net.xmx.velthoric.physics.vehicle.type.car.VxCar;
import net.xmx.velthoric.physics.vehicle.wheel.VxWheel;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A concrete implementation of a car, defining its physical properties like
 * chassis size, wheel setup, engine, and transmission.
 *
 * @author xI-Mx-Ix
 */
public class CarImpl extends VxCar {

    /**
     * Server-side constructor.
     */
    public CarImpl(VxBodyType<CarImpl> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    /**
     * Client-side constructor.
     */
    @Environment(EnvType.CLIENT)
    public CarImpl(VxBodyType<CarImpl> type, UUID id) {
        super(type, id);
    }

    @Override
    protected VehicleConstraintSettings createConstraintSettings() {
        this.wheels = new ArrayList<>(4);

        // --- Wheel and Suspension Physical Properties ---
        float wheelRadius = 0.55f;
        float wheelWidth = 0.35f;
        // Increased vehicle width for a more stable platform.
        float halfVehicleWidth = 1.15f;
        float halfVehicleLength = 2.0f;
        float suspensionMinLength = 0.3f;
        float suspensionMaxLength = 0.7f;
        float maxSteerAngleRad = (float) Math.toRadians(35.0);

        // Suspension stiffness and damping. Slightly softer for better compliance.
        float suspensionFrequency = 1.8f;
        float suspensionDamping = 0.8f;

        // --- Front-Left Wheel ---
        WheelSettingsWv flWheel = new WheelSettingsWv();
        flWheel.setPosition(new Vec3(-halfVehicleWidth, -0.2f, halfVehicleLength));
        flWheel.setRadius(wheelRadius);
        flWheel.setWidth(wheelWidth);
        flWheel.setSuspensionMinLength(suspensionMinLength);
        flWheel.setSuspensionMaxLength(suspensionMaxLength);
        flWheel.getSuspensionSpring().setFrequency(suspensionFrequency);
        flWheel.getSuspensionSpring().setDamping(suspensionDamping);
        flWheel.setMaxSteerAngle(maxSteerAngleRad);
        flWheel.setMaxBrakeTorque(8000.0f);

        // --- Front-Right Wheel ---
        WheelSettingsWv frWheel = new WheelSettingsWv();
        frWheel.setPosition(new Vec3(halfVehicleWidth, -0.2f, halfVehicleLength));
        frWheel.setRadius(wheelRadius);
        frWheel.setWidth(wheelWidth);
        frWheel.setSuspensionMinLength(suspensionMinLength);
        frWheel.setSuspensionMaxLength(suspensionMaxLength);
        frWheel.getSuspensionSpring().setFrequency(suspensionFrequency);
        frWheel.getSuspensionSpring().setDamping(suspensionDamping);
        frWheel.setMaxSteerAngle(maxSteerAngleRad);
        frWheel.setMaxBrakeTorque(8000.0f);

        // --- Rear-Left Wheel ---
        WheelSettingsWv rlWheel = new WheelSettingsWv();
        rlWheel.setPosition(new Vec3(-halfVehicleWidth, -0.2f, -halfVehicleLength));
        rlWheel.setRadius(wheelRadius);
        rlWheel.setWidth(wheelWidth);
        rlWheel.setMaxSteerAngle(0f);
        rlWheel.setSuspensionMinLength(suspensionMinLength);
        rlWheel.setSuspensionMaxLength(suspensionMaxLength);
        rlWheel.getSuspensionSpring().setFrequency(suspensionFrequency);
        rlWheel.getSuspensionSpring().setDamping(suspensionDamping);
        rlWheel.setMaxBrakeTorque(4000.0f);

        // --- Rear-Right Wheel ---
        WheelSettingsWv rrWheel = new WheelSettingsWv();
        rrWheel.setPosition(new Vec3(halfVehicleWidth, -0.2f, -halfVehicleLength));
        rrWheel.setRadius(wheelRadius);
        rrWheel.setWidth(wheelWidth);
        rrWheel.setMaxSteerAngle(0f);
        rrWheel.setSuspensionMinLength(suspensionMinLength);
        rrWheel.setSuspensionMaxLength(suspensionMaxLength);
        rrWheel.getSuspensionSpring().setFrequency(suspensionFrequency);
        rrWheel.getSuspensionSpring().setDamping(suspensionDamping);
        rrWheel.setMaxBrakeTorque(4000.0f);

        this.wheels.add(new VxWheel(flWheel));
        this.wheels.add(new VxWheel(frWheel));
        this.wheels.add(new VxWheel(rlWheel));
        this.wheels.add(new VxWheel(rrWheel));
        this.setSyncData(DATA_WHEELS_SETTINGS, this.wheels.stream().map(VxWheel::getSettings).collect(Collectors.toList()));

        // --- Controller Settings for a powerful, sporty vehicle ---
        WheeledVehicleControllerSettings controllerSettings = new WheeledVehicleControllerSettings();

        VehicleEngineSettings engineSettings = controllerSettings.getEngine();
        engineSettings.setMaxTorque(7500.0f);
        engineSettings.setMaxRpm(9000.0f);
        engineSettings.setMinRpm(1000.0f);

        VehicleTransmissionSettings transmissionSettings = controllerSettings.getTransmission();
        transmissionSettings.setMode(ETransmissionMode.Auto);
        transmissionSettings.setGearRatios(4.0f, 2.5f, 1.8f, 1.3f, 1.0f);
        transmissionSettings.setReverseGearRatios(-3.5f);
        transmissionSettings.setShiftUpRpm(8000.0f);
        transmissionSettings.setShiftDownRpm(3000.0f);

        // --- RWD SETUP ---
        // Set the number of differentials to 1 (only for the rear axle).
        controllerSettings.setNumDifferentials(1);

        // A limited slip differential ensures torque is transferred to the wheel with more grip,
        // which is crucial for stability and traction during cornering. A higher value means
        // more locking between the left and right wheels.
        float limitedSlipRatio = 3.0f;

        // Configure the single rear differential.
        VehicleDifferentialSettings rearDifferential = controllerSettings.getDifferential(0);
        rearDifferential.setLeftWheel(2);  // Index of Rear-Left wheel
        rearDifferential.setRightWheel(3); // Index of Rear-Right wheel
        rearDifferential.setDifferentialRatio(4.11f);
        // Set the engine torque ratio to 1.0, so 100% of the torque goes to this axle.
        rearDifferential.setEngineTorqueRatio(1.0f);
        rearDifferential.setLimitedSlipRatio(limitedSlipRatio);

        // The front differential settings are removed.

        // --- Final Constraint Settings ---
        VehicleConstraintSettings settings = new VehicleConstraintSettings();
        settings.addWheels(flWheel, frWheel, rlWheel, rrWheel);
        settings.setController(controllerSettings);

        settings.setNumAntiRollBars(2);

        // Reduced anti-roll bar stiffness to allow for more body roll, preventing lift-off.
        VehicleAntiRollBar frontArb = settings.getAntiRollBar(0);
        frontArb.setLeftWheel(0);
        frontArb.setRightWheel(1);
        frontArb.setStiffness(3000.0f);

        VehicleAntiRollBar rearArb = settings.getAntiRollBar(1);
        rearArb.setLeftWheel(2);
        rearArb.setRightWheel(3);
        rearArb.setStiffness(3000.0f);

        return settings;
    }

    @Override
    protected VehicleCollisionTester createCollisionTester() {
        return new VehicleCollisionTesterCastCylinder(VxLayers.DYNAMIC);
    }

    @Override
    public void defineSeats(VxSeat.Builder builder) {
        // Front seat (driver)
        Vector3f frontOffset = new Vector3f(0.0f, 0.5f, 0.5f);
        AABB frontAABB = new AABB(
                frontOffset.x - 0.3, frontOffset.y - 0.4, frontOffset.z - 0.3,
                frontOffset.x + 0.3, frontOffset.y + 0.4, frontOffset.z + 0.3
        );
        String frontIdentifier = "front_seat";
        VxSeat frontSeat = new VxSeat(this.getPhysicsId(), frontIdentifier, frontAABB, frontOffset, true);

        // Rear seat (passenger)
        Vector3f rearOffset = new Vector3f(0.0f, 0.5f, -0.5f);
        AABB rearAABB = new AABB(
                rearOffset.x - 0.3, rearOffset.y - 0.4, rearOffset.z - 0.3,
                rearOffset.x + 0.3, rearOffset.y + 0.4, rearOffset.z + 0.3
        );
        String rearIdentifier = "rear_seat";
        VxSeat rearSeat = new VxSeat(this.getPhysicsId(), rearIdentifier, rearAABB, rearOffset, false);

        builder.addSeat(frontSeat);
        builder.addSeat(rearSeat);
    }

    @Override
    public int createJoltBody(VxRigidBodyFactory factory) {
        try (ShapeSettings chassisShape = new BoxShapeSettings(this.getSyncData(DATA_CHASSIS_HALF_EXTENTS))) {
            // A non-AutoCloseable object for the center of mass offset.
            // A much lower center of mass is crucial for high-speed stability.
            Vec3 centerOfMassOffset = new Vec3(0f, -0.6f, 0f);

            // Use the offset shape to lower the center of mass for better stability.
            try (
                    ShapeSettings finalShapeSettings = new OffsetCenterOfMassShapeSettings(centerOfMassOffset, chassisShape);
                    BodyCreationSettings bcs = new BodyCreationSettings()
            ) {
                bcs.setShapeSettings(finalShapeSettings);
                bcs.setMotionType(EMotionType.Dynamic);
                bcs.setObjectLayer(VxLayers.DYNAMIC);
                bcs.setMotionQuality(EMotionQuality.LinearCast);
                bcs.setMaxLinearVelocity(10000000f);
                bcs.setMaxAngularVelocity(10000000f);
                // Reduced mass for a sportier and more nimble feel.
                bcs.getMassPropertiesOverride().setMass(1600f);
                bcs.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia);

                return factory.create(finalShapeSettings, bcs);
            }
        }
    }
}