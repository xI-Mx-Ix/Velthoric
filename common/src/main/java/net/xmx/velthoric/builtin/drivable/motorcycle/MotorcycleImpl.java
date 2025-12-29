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
import net.minecraft.client.multiplayer.ClientLevel;
import net.xmx.velthoric.builtin.drivable.renderer.VxSeatRenderer;
import net.xmx.velthoric.builtin.drivable.renderer.VxWheelRenderer;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.type.factory.VxRigidBodyFactory;
import net.xmx.velthoric.physics.vehicle.data.VxVehicleData;
import net.xmx.velthoric.physics.vehicle.data.component.VxSeatDefinition;
import net.xmx.velthoric.physics.vehicle.data.component.VxWheelDefinition;
import net.xmx.velthoric.physics.vehicle.data.slot.VehicleSeatSlot;
import net.xmx.velthoric.physics.vehicle.data.slot.VehicleWheelSlot;
import net.xmx.velthoric.physics.vehicle.part.VxPart;
import net.xmx.velthoric.physics.vehicle.part.impl.VxVehicleSeat;
import net.xmx.velthoric.physics.vehicle.part.impl.VxVehicleWheel;
import net.xmx.velthoric.physics.vehicle.type.VxMotorcycle;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.joml.Vector3f;

import java.util.UUID;

import static com.github.stephengold.joltjni.Jolt.degreesToRadians;
import static com.github.stephengold.joltjni.operator.Op.minus;

/**
 * A concrete implementation of a motorcycle using the new modular system.
 *
 * @author xI-Mx-Ix
 */
public class MotorcycleImpl extends VxMotorcycle {

    private static VxVehicleData createDefaultData() {
        VxVehicleData data = new VxVehicleData("builtin_motorcycle");

        // 1. Chassis
        data.setMass(240.0f);
        data.setChassisSize(0.2f, 0.3f, 0.4f);
        data.setCenterOfMass(0.0f, -0.3f, 0.0f);

        // 2. Engine
        data.getEngine()
                .setMaxTorque(150.0f)
                .setMaxRpm(10000.0f);

        // 3. Transmission
        data.getTransmission()
                .setMode(ETransmissionMode.Auto)
                .setGearRatios(2.27f, 1.63f, 1.3f, 1.09f, 0.96f, 0.88f)
                .setReverseRatio(-3.0f)
                .setSwitchTime(0.025f)
                .setClutchStrength(2.0f);

        // 4. Wheels
        float wheelRadius = 0.31f;
        float wheelWidth = 0.05f;
        float yPos = -0.3f * 0.9f;

        // Front Wheel
        data.addWheelSlot(new VehicleWheelSlot("front", new Vector3f(0.0f, yPos, 0.75f))
                .setPowered(false).setSteerable(true)
                .setSuspension(0.3f, 0.5f, 1.5f, 0.5f)
                .setMaxSteerAngle(30.0f).setBrakeTorque(500.0f));

        // Rear Wheel
        data.addWheelSlot(new VehicleWheelSlot("rear", new Vector3f(0.0f, yPos, -0.75f))
                .setPowered(true).setSteerable(false)
                .setSuspension(0.3f, 0.5f, 2.0f, 0.5f)
                .setBrakeTorque(250.0f));

        data.setDefaultWheel("builtin:moto_wheel");

        // 5. Seat
        data.addSeatSlot(new VehicleSeatSlot("driver", new Vector3f(0.0f, 0.4f, 0.1f)).setDriver(true));
        data.setDefaultSeat("builtin:moto_seat");

        return data;
    }

    public MotorcycleImpl(VxBodyType<MotorcycleImpl> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id, createDefaultData());
    }

    @Environment(EnvType.CLIENT)
    public MotorcycleImpl(VxBodyType<MotorcycleImpl> type, UUID id) {
        super(type, id, createDefaultData());
    }

    @Override
    public void onBodyAdded(VxPhysicsWorld world) {
        super.onBodyAdded(world);

        // Apply advanced motorcycle physics (Caster angle, Steering axis)
        float casterAngle = degreesToRadians(30);
        Vec3 suspensionDir = new Vec3(0, -1, (float) Math.tan(casterAngle)).normalized();
        Vec3 steeringAxis = minus(suspensionDir);

        // Apply to front wheel
        if (!getWheels().isEmpty()) {
            VxVehicleWheel front = getWheels().get(0);
            front.getSettings().setSuspensionDirection(suspensionDir);
            front.getSettings().setSteeringAxis(steeringAxis);
        }
    }

    @Override
    protected VxWheelDefinition resolveWheelDefinition(String wheelId) {
        return new VxWheelDefinition("builtin:moto_wheel", 0.31f, 0.05f, 1.0f, "models/moto_wheel.obj", "wheel");
    }

    @Override
    protected VxSeatDefinition resolveSeatDefinition(String seatId) {
        return new VxSeatDefinition("builtin:moto_seat", new Vector3f(0.4f, 0.1f, 0.6f), "models/moto_seat.obj", "seat");
    }

    @Override
    protected VehicleCollisionTester createCollisionTester() {
        return new VehicleCollisionTesterCastCylinder(VxLayers.DYNAMIC, 0.5f * 0.05f);
    }

    @Override
    public int createJoltBody(VxRigidBodyFactory factory) {
        Vector3f extents = vehicleData.getChassisHalfExtents();
        try (ShapeSettings chassisShape = new BoxShapeSettings(new Vec3(extents.x, extents.y, extents.z))) {

            Vector3f com = vehicleData.getCenterOfMassOffset();
            Vec3 centerOfMassOffset = new Vec3(com.x, com.y, com.z);

            try (
                    ShapeSettings finalShapeSettings = new OffsetCenterOfMassShapeSettings(centerOfMassOffset, chassisShape);
                    BodyCreationSettings bcs = new BodyCreationSettings()
            ) {
                bcs.setShapeSettings(finalShapeSettings);
                bcs.setMotionType(EMotionType.Dynamic);
                bcs.setObjectLayer(VxLayers.DYNAMIC);
                bcs.setMotionQuality(EMotionQuality.LinearCast);

                bcs.getMassPropertiesOverride().setMass(vehicleData.getMass());
                bcs.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia);

                return factory.create(finalShapeSettings, bcs);
            }
        }
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void onBodyAdded(ClientLevel level) {
        super.onBodyAdded(level);
        for (VxPart part : this.getParts()) {
            if (part instanceof VxVehicleWheel) {
                part.setRenderer(new VxWheelRenderer());
            } else if (part instanceof VxVehicleSeat) {
                part.setRenderer(new VxSeatRenderer());
            }
        }
    }
}