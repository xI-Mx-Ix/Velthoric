/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.car;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import com.github.stephengold.joltjni.enumerate.ETransmissionMode;
import com.github.stephengold.joltjni.std.StringStream;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.type.factory.VxRigidBodyFactory;
import net.xmx.velthoric.physics.riding.input.VxRideInput;
import net.xmx.velthoric.physics.riding.seat.VxSeat;
import net.xmx.velthoric.physics.vehicle.VxVehicle;
import net.xmx.velthoric.physics.vehicle.wheel.VxWheel;
import net.xmx.velthoric.natives.VxLayers;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author xI-Mx-Ix
 */
public class CarRigidBody extends VxVehicle {

    public CarRigidBody(VxObjectType<CarRigidBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);

        Vec3 chassisHalfExtents = new Vec3(1.1f, 0.5f, 2.4f);
        this.wheels = new ArrayList<>(4);

        float wheelRadius = 0.55f;
        float wheelWidth = 0.35f;

        float halfVehicleWidth = 1.0f;
        float halfVehicleLength = 2.0f;

        float suspensionMinLength = 0.3f;
        float suspensionMaxLength = 0.7f;
        float maxSteerAngleRad = (float) Math.toRadians(35.0);

        float suspensionFrequency = 1.8f;
        float suspensionDamping = 0.6f;

        WheelSettingsWv flWheel = new WheelSettingsWv();
        flWheel.setPosition(new Vec3(-halfVehicleWidth, -0.2f, halfVehicleLength));
        flWheel.setRadius(wheelRadius);
        flWheel.setWidth(wheelWidth);
        flWheel.setSuspensionMinLength(suspensionMinLength);
        flWheel.setSuspensionMaxLength(suspensionMaxLength);
        flWheel.getSuspensionSpring().setFrequency(suspensionFrequency);
        flWheel.getSuspensionSpring().setDamping(suspensionDamping);
        flWheel.setMaxSteerAngle(maxSteerAngleRad);
        flWheel.setMaxBrakeTorque(5000.0f);

        WheelSettingsWv frWheel = new WheelSettingsWv();
        frWheel.setPosition(new Vec3(halfVehicleWidth, -0.2f, halfVehicleLength));
        frWheel.setRadius(wheelRadius);
        frWheel.setWidth(wheelWidth);
        frWheel.setSuspensionMinLength(suspensionMinLength);
        frWheel.setSuspensionMaxLength(suspensionMaxLength);
        frWheel.getSuspensionSpring().setFrequency(suspensionFrequency);
        frWheel.getSuspensionSpring().setDamping(suspensionDamping);
        frWheel.setMaxSteerAngle(maxSteerAngleRad);
        frWheel.setMaxBrakeTorque(5000.0f);

        WheelSettingsWv rlWheel = new WheelSettingsWv();
        rlWheel.setPosition(new Vec3(-halfVehicleWidth, -0.2f, -halfVehicleLength));
        rlWheel.setRadius(wheelRadius);
        rlWheel.setWidth(wheelWidth);
        rlWheel.setMaxSteerAngle(0f);
        rlWheel.setSuspensionMinLength(suspensionMinLength);
        rlWheel.setSuspensionMaxLength(suspensionMaxLength);
        rlWheel.getSuspensionSpring().setFrequency(suspensionFrequency);
        rlWheel.getSuspensionSpring().setDamping(suspensionDamping);
        rlWheel.setMaxBrakeTorque(2500.0f);

        WheelSettingsWv rrWheel = new WheelSettingsWv();
        rrWheel.setPosition(new Vec3(halfVehicleWidth, -0.2f, -halfVehicleLength));
        rrWheel.setRadius(wheelRadius);
        rrWheel.setWidth(wheelWidth);
        rrWheel.setMaxSteerAngle(0f);
        rrWheel.setSuspensionMinLength(suspensionMinLength);
        rrWheel.setSuspensionMaxLength(suspensionMaxLength);
        rrWheel.getSuspensionSpring().setFrequency(suspensionFrequency);
        rrWheel.getSuspensionSpring().setDamping(suspensionDamping);
        rrWheel.setMaxBrakeTorque(2500.0f);

        this.wheels.add(new VxWheel(flWheel));
        this.wheels.add(new VxWheel(frWheel));
        this.wheels.add(new VxWheel(rlWheel));
        this.wheels.add(new VxWheel(rrWheel));

        // Set synchronized data which will be sent to clients
        this.setSyncData(DATA_CHASSIS_HALF_EXTENTS, chassisHalfExtents);
        this.setSyncData(DATA_WHEELS_SETTINGS, this.wheels.stream().map(VxWheel::getSettings).collect(Collectors.toList()));

        WheeledVehicleControllerSettings controllerSettings = new WheeledVehicleControllerSettings();

        VehicleEngineSettings engineSettings = controllerSettings.getEngine();
        engineSettings.setMaxTorque(2600.0f);
        engineSettings.setMaxRpm(26500.0f);
        engineSettings.setMinRpm(1000.0f);

        VehicleTransmissionSettings transmissionSettings = controllerSettings.getTransmission();
        transmissionSettings.setMode(ETransmissionMode.Auto);
        transmissionSettings.setGearRatios(3.5f, 2.0f, 1.4f, 1.0f, 0.75f);
        transmissionSettings.setReverseGearRatios(-3.0f);
        transmissionSettings.setShiftUpRpm(16000.0f);
        transmissionSettings.setShiftDownRpm(2000.0f);

        controllerSettings.setNumDifferentials(1);
        VehicleDifferentialSettings differential = controllerSettings.getDifferential(0);
        differential.setLeftWheel(2);
        differential.setRightWheel(3);
        differential.setDifferentialRatio(3.42f);

        this.constraintSettings = new VehicleConstraintSettings();
        this.constraintSettings.addWheels(flWheel, frWheel, rlWheel, rrWheel);
        this.constraintSettings.setController(controllerSettings);

        this.collisionTester = new VehicleCollisionTesterCastCylinder(VxLayers.DYNAMIC);

        addDriverSeat();
    }

    private void addDriverSeat() {
        Vector3f riderOffset = new Vector3f(0.0f, 0.5f, 0.0f);
        AABB localAABB = new AABB(
                riderOffset.x - 0.3, riderOffset.y - 0.4, riderOffset.z - 0.3,
                riderOffset.x + 0.3, riderOffset.y + 0.4, riderOffset.z + 0.3
        );
        VxSeat driverSeat = new VxSeat(UUID.randomUUID(), "driver_seat", localAABB, riderOffset, true);
        this.getWorld().getRidingManager().addSeat(this.getPhysicsId(), driverSeat);
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
            bcs.getMassPropertiesOverride().setMass(2000f);
            bcs.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia);

            return factory.create(shapeSettings, bcs);
        }
    }

    @Override
    public void writePersistenceData(VxByteBuf buf) {
        Vec3 halfExtents = this.getSyncData(DATA_CHASSIS_HALF_EXTENTS);
        buf.writeFloat(halfExtents.getX());
        buf.writeFloat(halfExtents.getY());
        buf.writeFloat(halfExtents.getZ());

        List<WheelSettingsWv> wheelSettings = this.getSyncData(DATA_WHEELS_SETTINGS);
        buf.writeVarInt(wheelSettings.size());
        for (WheelSettingsWv setting : wheelSettings) {
            try (StringStream stream = new StringStream()) {
                setting.saveBinaryState(new StreamOutWrapper(stream));
                byte[] bytes = stream.str().getBytes();
                buf.writeByteArray(bytes);
            }
        }
    }

    @Override
    public void readPersistenceData(VxByteBuf buf) {
        Vec3 halfExtents = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        this.setSyncData(DATA_CHASSIS_HALF_EXTENTS, halfExtents);

        int wheelCount = buf.readVarInt();
        if (wheelCount == this.wheels.size()) {
            for (VxWheel wheel : wheels) {
                byte[] bytes = buf.readByteArray();
                try (StringStream stream = new StringStream(new String(bytes))) {
                    // Restore state into the existing settings object
                    wheel.getSettings().restoreBinaryState(new StreamInWrapper(stream));
                }
            }
            // The underlying WheelSettingsWv objects were modified.
            // We must update the synchronized data to reflect this change and mark it as dirty for clients.
            this.setSyncData(DATA_WHEELS_SETTINGS, this.wheels.stream().map(VxWheel::getSettings).collect(Collectors.toList()));
        }
    }

    @Override
    public void onStopRiding(ServerPlayer player) {
        if (this.getController() != null) {
            this.getController().setInput(0.0f, 0.0f, 0.0f, 0.0f);
        }
    }

    @Override
    public void handleDriverInput(ServerPlayer player, VxRideInput input) {
        if (this.getController() == null) {
            return;
        }

        float forward = 0.0f;
        if (input.isForward()) forward = 1.0f;
        if (input.isBackward()) forward = -1.0f;

        float right = 0.0f;
        if (input.isRight()) right = 1.0f;
        if (input.isLeft()) right = -1.0f;

        float brake = input.isBackward() ? 1.0f : 0.0f;
        float handBrake = input.isUp() ? 1.0f : 0.0f;

        this.getController().setInput(forward, right, brake, handBrake);
    }
}