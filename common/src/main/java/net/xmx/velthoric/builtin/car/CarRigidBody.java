/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.car;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
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
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.UUID;

public class CarRigidBody extends VxVehicle {

    private final Vec3 chassisHalfExtents;

    public CarRigidBody(VxObjectType<CarRigidBody> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);

        this.chassisHalfExtents = new Vec3(0.9f, 0.4f, 2.0f);
        this.wheels = new ArrayList<>(4);

        float wheelRadius = 0.45f;
        float wheelWidth = 0.3f;
        float halfVehicleWidth = 0.8f;
        float halfVehicleLength = 1.5f;
        float suspensionMinLength = 0.3f;
        float suspensionMaxLength = 0.5f;
        float maxSteerAngleRad = (float) Math.toRadians(35.0);

        WheelSettingsWv flWheel = new WheelSettingsWv();
        flWheel.setPosition(new Vec3(-halfVehicleWidth, -0.2f, halfVehicleLength));
        flWheel.setRadius(wheelRadius);
        flWheel.setWidth(wheelWidth);
        flWheel.setSuspensionMinLength(suspensionMinLength);
        flWheel.setSuspensionMaxLength(suspensionMaxLength);
        flWheel.setMaxSteerAngle(maxSteerAngleRad);

        WheelSettingsWv frWheel = new WheelSettingsWv();
        frWheel.setPosition(new Vec3(halfVehicleWidth, -0.2f, halfVehicleLength));
        frWheel.setRadius(wheelRadius);
        frWheel.setWidth(wheelWidth);
        frWheel.setSuspensionMinLength(suspensionMinLength);
        frWheel.setSuspensionMaxLength(suspensionMaxLength);
        frWheel.setMaxSteerAngle(maxSteerAngleRad);

        WheelSettingsWv rlWheel = new WheelSettingsWv();
        rlWheel.setPosition(new Vec3(-halfVehicleWidth, -0.2f, -halfVehicleLength));
        rlWheel.setRadius(wheelRadius);
        rlWheel.setWidth(wheelWidth);
        rlWheel.setMaxSteerAngle(0f);
        rlWheel.setSuspensionMinLength(suspensionMinLength);
        rlWheel.setSuspensionMaxLength(suspensionMaxLength);

        WheelSettingsWv rrWheel = new WheelSettingsWv();
        rrWheel.setPosition(new Vec3(halfVehicleWidth, -0.2f, -halfVehicleLength));
        rrWheel.setRadius(wheelRadius);
        rrWheel.setWidth(wheelWidth);
        rrWheel.setMaxSteerAngle(0f);
        rrWheel.setSuspensionMinLength(suspensionMinLength);
        rrWheel.setSuspensionMaxLength(suspensionMaxLength);

        this.wheels.add(new VxWheel(flWheel));
        this.wheels.add(new VxWheel(frWheel));
        this.wheels.add(new VxWheel(rlWheel));
        this.wheels.add(new VxWheel(rrWheel));

        WheeledVehicleControllerSettings controllerSettings = new WheeledVehicleControllerSettings();
        controllerSettings.setNumDifferentials(1);
        VehicleDifferentialSettings differential = controllerSettings.getDifferential(0);
        differential.setLeftWheel(2);
        differential.setRightWheel(3);

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

        VxSeat driverSeat = new VxSeat("driver_seat", localAABB, riderOffset, true);

        this.getWorld().getRidingManager().addSeat(this.getPhysicsId(), driverSeat);
    }

    @Override
    public int createJoltBody(VxRigidBodyFactory factory) {
        try (
                ShapeSettings shapeSettings = new BoxShapeSettings(this.chassisHalfExtents);
                BodyCreationSettings bcs = new BodyCreationSettings()
        ) {
            bcs.setShapeSettings(shapeSettings);
            bcs.setMotionType(EMotionType.Dynamic);
            bcs.setObjectLayer(VxLayers.DYNAMIC);
            bcs.getMassPropertiesOverride().setMass(1500f);
            bcs.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia);

            return factory.create(shapeSettings, bcs);
        }
    }

    @Override
    public void writeSyncData(VxByteBuf buf) {
        buf.writeFloat(chassisHalfExtents.getX());
        buf.writeFloat(chassisHalfExtents.getY());
        buf.writeFloat(chassisHalfExtents.getZ());
        buf.writeVarInt(wheels.size());
        for (VxWheel wheel : wheels) {
            try (StringStream stream = new StringStream()) {
                wheel.getSettings().saveBinaryState(new StreamOutWrapper(stream));
                byte[] bytes = stream.str().getBytes();
                buf.writeByteArray(bytes);
            }
        }
    }

    @Override
    public void writePersistenceData(VxByteBuf buf) {
        writeSyncData(buf);
    }

    @Override
    public void readPersistenceData(VxByteBuf buf) {
        chassisHalfExtents.set(buf.readFloat(), buf.readFloat(), buf.readFloat());
        int wheelCount = buf.readVarInt();
        if (wheelCount == this.wheels.size()) {
            for (VxWheel wheel : wheels) {
                byte[] bytes = buf.readByteArray();
                try (StringStream stream = new StringStream(new String(bytes))) {
                    wheel.getSettings().restoreBinaryState(new StreamInWrapper(stream));
                }
            }
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