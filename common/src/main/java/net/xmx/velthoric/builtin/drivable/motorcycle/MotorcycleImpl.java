/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.drivable.motorcycle;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.AABB;
import net.xmx.velthoric.natives.VxLayers;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.type.factory.VxRigidBodyFactory;
import net.xmx.velthoric.physics.mounting.seat.VxSeat;
import net.xmx.velthoric.physics.vehicle.type.motorcycle.VxMotorcycle;
import net.xmx.velthoric.physics.vehicle.wheel.VxWheel;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.github.stephengold.joltjni.Jolt.degreesToRadians;
import static com.github.stephengold.joltjni.operator.Op.minus;

/**
 * A concrete implementation of a motorcycle, with physics properties tuned
 * for stable and responsive handling.
 *
 * @author xI-Mx-Ix
 */
public class MotorcycleImpl extends VxMotorcycle {

    /**
     * Server-side constructor.
     */
    public MotorcycleImpl(VxBodyType<MotorcycleImpl> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    /**
     * Client-side constructor.
     */
    @Environment(EnvType.CLIENT)
    public MotorcycleImpl(VxBodyType<MotorcycleImpl> type, UUID id) {
        super(type, id);
    }

    @Override
    protected VehicleConstraintSettings createConstraintSettings() {
        this.wheels = new ArrayList<>(2);

        final float back_wheel_radius = 0.31f;
        final float back_wheel_width = 0.05f;
        final float back_wheel_pos_z = -0.75f;
        final float back_suspension_min_length = 0.3f;
        final float back_suspension_max_length = 0.5f;
        final float back_suspension_freq = 2.0f;
        final float back_brake_torque = 250.0f;

        final float front_wheel_radius = 0.31f;
        final float front_wheel_width = 0.05f;
        final float front_wheel_pos_z = 0.75f;
        final float front_suspension_min_length = 0.3f;
        final float front_suspension_max_length = 0.5f;
        final float front_suspension_freq = 1.5f;
        final float front_brake_torque = 500.0f;

        final float half_vehicle_height = 0.3f;
        final float max_steering_angle = degreesToRadians(30);
        final float caster_angle = degreesToRadians(30);

        VehicleConstraintSettings vehicle = new VehicleConstraintSettings();
        vehicle.setMaxPitchRollAngle(degreesToRadians(60.0f));
        vehicle.setUp(new Vec3(0f, 1f, 0f));

        // --- Front Wheel ---
        WheelSettingsWv front = new WheelSettingsWv();
        front.setPosition(new Vec3(0.0f, -0.9f * half_vehicle_height, front_wheel_pos_z));
        front.setMaxSteerAngle(max_steering_angle);
        front.setSuspensionDirection(new Vec3(0, -1, (float) Math.tan(caster_angle)).normalized());
        front.setSteeringAxis(minus(front.getSuspensionDirection()));
        front.setRadius(front_wheel_radius);
        front.setWidth(front_wheel_width);
        front.setSuspensionMinLength(front_suspension_min_length);
        front.setSuspensionMaxLength(front_suspension_max_length);
        front.getSuspensionSpring().setFrequency(front_suspension_freq);
        front.setMaxBrakeTorque(front_brake_torque);

        // --- Rear Wheel ---
        WheelSettingsWv back = new WheelSettingsWv();
        back.setPosition(new Vec3(0.0f, -0.9f * half_vehicle_height, back_wheel_pos_z));
        back.setMaxSteerAngle(0.0f);
        back.setRadius(back_wheel_radius);
        back.setWidth(back_wheel_width);
        back.setSuspensionMinLength(back_suspension_min_length);
        back.setSuspensionMaxLength(back_suspension_max_length);
        back.getSuspensionSpring().setFrequency(back_suspension_freq);
        back.setMaxBrakeTorque(back_brake_torque);

        this.wheels.add(new VxWheel(front));
        this.wheels.add(new VxWheel(back));
        this.setSyncData(DATA_WHEELS_SETTINGS, this.wheels.stream().map(VxWheel::getSettings).collect(Collectors.toList()));

        vehicle.addWheels(front, back);

        // --- Controller ---
        MotorcycleControllerSettings controller = new MotorcycleControllerSettings();
        controller.getEngine().setMaxTorque(150.0f);
        controller.getEngine().setMinRpm(1000.0f);
        controller.getEngine().setMaxRpm(10000.0f);
        controller.getTransmission().setShiftDownRpm(2000.0f);
        controller.getTransmission().setShiftUpRpm(8000.0f);
        controller.getTransmission().setGearRatios(2.27f, 1.63f, 1.3f, 1.09f, 0.96f, 0.88f);
        controller.getTransmission().setReverseGearRatios(-4.0f);
        controller.getTransmission().setClutchStrength(2.0f);
        vehicle.setController(controller);

        // --- Differential ---
        controller.setNumDifferentials(1);
        VehicleDifferentialSettings differential = controller.getDifferential(0);
        differential.setLeftWheel(-1);
        differential.setRightWheel(1);
        differential.setDifferentialRatio(1.93f * 40.0f / 16.0f);

        return vehicle;
    }

    @Override
    protected VehicleCollisionTester createCollisionTester() {
        return new VehicleCollisionTesterCastCylinder(VxLayers.DYNAMIC, 0.5f * 0.05f);
    }

    @Override
    public void defineSeats(VxSeat.Builder builder) {
        // Driver's seat
        Vector3f seatOffset = new Vector3f(0.0f, 0.4f, 0.1f);
        AABB seatAABB = new AABB(
                seatOffset.x - 0.3, seatOffset.y - 0.4, seatOffset.z - 0.3,
                seatOffset.x + 0.3, seatOffset.y + 0.4, seatOffset.z + 0.3
        );
        String seatIdentifier = "driver_seat";
        VxSeat driverSeat = new VxSeat(this.getPhysicsId(), seatIdentifier, seatAABB, seatOffset, true);

        builder.addSeat(driverSeat);
    }

    @Override
    public int createJoltBody(VxRigidBodyFactory factory) {
        // The center of mass is lowered for stability. The offset is relative to the chassis's half-extents.
        Vec3 centerOfMassOffset = new Vec3(0, -this.getChassisHalfExtents().getY(), 0);

        try (
                ShapeSettings chassisShape = new BoxShapeSettings(getChassisHalfExtents());
                ShapeSettings finalShapeSettings = new OffsetCenterOfMassShapeSettings(centerOfMassOffset, chassisShape);
                BodyCreationSettings bcs = new BodyCreationSettings()
        ) {
            bcs.setShapeSettings(finalShapeSettings);
            bcs.setMotionType(EMotionType.Dynamic);
            bcs.setObjectLayer(VxLayers.DYNAMIC);
            bcs.setMotionQuality(EMotionQuality.LinearCast);
            bcs.getMassPropertiesOverride().setMass(240.0f);
            bcs.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia);

            return factory.create(finalShapeSettings, bcs);
        }
    }
}