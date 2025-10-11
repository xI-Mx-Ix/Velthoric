/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.drivable.motorcycle;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import com.github.stephengold.joltjni.enumerate.ETransmissionMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.AABB;
import net.xmx.velthoric.natives.VxLayers;
import net.xmx.velthoric.physics.mounting.seat.VxSeat;
import net.xmx.velthoric.physics.object.registry.VxObjectType;
import net.xmx.velthoric.physics.object.type.factory.VxRigidBodyFactory;
import net.xmx.velthoric.physics.vehicle.type.motorcycle.VxMotorcycle;
import net.xmx.velthoric.physics.vehicle.wheel.VxWheel;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A concrete implementation of a motorcycle, defining its physical properties,
 * two-wheel setup, and using a MotorcycleController.
 *
 * @author xI-Mx-Ix
 */
public class MotorcycleImpl extends VxMotorcycle {

    /**
     * Server-side constructor.
     */
    public MotorcycleImpl(VxObjectType<MotorcycleImpl> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
        addDriverSeat();
    }

    /**
     * Client-side constructor.
     */
    @Environment(EnvType.CLIENT)
    public MotorcycleImpl(VxObjectType<MotorcycleImpl> type, UUID id) {
        super(type, id);
    }

    @Override
    protected VehicleConstraintSettings createConstraintSettings() {
        this.wheels = new ArrayList<>(2);

        float wheelRadius = 0.4f;
        float wheelWidth = 0.15f;
        float wheelBase = 1.4f;
        float suspensionMinLength = 0.15f;
        float suspensionMaxLength = 0.4f;
        float suspensionFrequency = 5.0f;
        float suspensionDamping = 0.8f;

        WheelSettingsWv rearWheel = new WheelSettingsWv();
        rearWheel.setPosition(new Vec3(0, 0, -wheelBase * 0.5f));
        rearWheel.setRadius(wheelRadius);
        rearWheel.setWidth(wheelWidth);
        rearWheel.setSuspensionMinLength(suspensionMinLength);
        rearWheel.setSuspensionMaxLength(suspensionMaxLength);
        rearWheel.getSuspensionSpring().setFrequency(suspensionFrequency);
        rearWheel.getSuspensionSpring().setDamping(suspensionDamping);
        rearWheel.setMaxSteerAngle(0.0f);

        WheelSettingsWv frontWheel = new WheelSettingsWv();
        frontWheel.setPosition(new Vec3(0, 0, wheelBase * 0.5f));
        frontWheel.setRadius(wheelRadius);
        frontWheel.setWidth(wheelWidth);
        frontWheel.setSuspensionMinLength(suspensionMinLength);
        frontWheel.setSuspensionMaxLength(suspensionMaxLength);
        frontWheel.getSuspensionSpring().setFrequency(suspensionFrequency);
        frontWheel.getSuspensionSpring().setDamping(suspensionDamping);
        frontWheel.setMaxSteerAngle((float) Math.toRadians(40.0));

        this.wheels.add(new VxWheel(rearWheel));
        this.wheels.add(new VxWheel(frontWheel));
        this.setSyncData(DATA_WHEELS_SETTINGS, this.wheels.stream().map(VxWheel::getSettings).collect(Collectors.toList()));

        MotorcycleControllerSettings controllerSettings = new MotorcycleControllerSettings();
        VehicleEngineSettings engineSettings = controllerSettings.getEngine();
        engineSettings.setMaxTorque(800.0f);
        engineSettings.setMaxRpm(12000.0f);
        engineSettings.setMinRpm(1000.0f);

        VehicleTransmissionSettings transmissionSettings = controllerSettings.getTransmission();
        transmissionSettings.setMode(ETransmissionMode.Auto);
        transmissionSettings.setGearRatios(2.9f, 2.1f, 1.6f, 1.3f, 1.1f);
        transmissionSettings.setReverseGearRatios(-2.5f);
        transmissionSettings.setShiftUpRpm(8000.0f);
        transmissionSettings.setShiftDownRpm(3000.0f);

        controllerSettings.setNumDifferentials(1);
        VehicleDifferentialSettings differential = controllerSettings.getDifferential(0);
        differential.setLeftWheel(0);
        differential.setRightWheel(-1);
        differential.setDifferentialRatio(2.5f);

        VehicleConstraintSettings settings = new VehicleConstraintSettings();
        settings.addWheels(rearWheel, frontWheel);
        settings.setController(controllerSettings);

        return settings;
    }

    @Override
    protected VehicleCollisionTester createCollisionTester() {
        return new VehicleCollisionTesterCastCylinder(VxLayers.DYNAMIC);
    }

    private void addDriverSeat() {
        Vector3f riderOffset = new Vector3f(0.0f, 0.7f, -0.2f);
        AABB localAABB = new AABB(
                riderOffset.x - 0.3, riderOffset.y - 0.4, riderOffset.z - 0.3,
                riderOffset.x + 0.3, riderOffset.y + 0.4, riderOffset.z + 0.3
        );
        VxSeat driverSeat = new VxSeat(UUID.randomUUID(), "driver_seat", localAABB, riderOffset, true);
        this.getPhysicsWorld().getMountingManager().addSeat(this.getPhysicsId(), driverSeat);
    }

    @Override
    public int createJoltBody(VxRigidBodyFactory factory) {
        try (
                ShapeSettings shapeSettings = new BoxShapeSettings(this.getSyncData(DATA_CHASSIS_HALF_EXTENTS));
                BodyCreationSettings bcs = new BodyCreationSettings()
        ) {
            bcs.setShapeSettings(shapeSettings);
            bcs.setMotionType(EMotionType.Dynamic);
            bcs.setObjectLayer(VxLayers.DYNAMIC);
            bcs.setMotionQuality(EMotionQuality.LinearCast);
            bcs.getMassPropertiesOverride().setMass(250f);
            bcs.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia);
            return factory.create(shapeSettings, bcs);
        }
    }
}