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
import net.minecraft.client.multiplayer.ClientLevel;
import net.xmx.velthoric.builtin.drivable.renderer.VxSeatRenderer;
import net.xmx.velthoric.builtin.drivable.renderer.VxWheelRenderer;
import net.xmx.velthoric.physics.VxPhysicsLayers;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.type.factory.VxRigidBodyFactory;
import net.xmx.velthoric.physics.vehicle.config.VxCarConfig;
import net.xmx.velthoric.physics.vehicle.part.VxPart;
import net.xmx.velthoric.physics.vehicle.part.definition.VxSeatDefinition;
import net.xmx.velthoric.physics.vehicle.part.definition.VxWheelDefinition;
import net.xmx.velthoric.physics.vehicle.part.impl.VxVehicleSeat;
import net.xmx.velthoric.physics.vehicle.part.impl.VxVehicleWheel;
import net.xmx.velthoric.physics.vehicle.part.slot.VehicleSeatSlot;
import net.xmx.velthoric.physics.vehicle.part.slot.VehicleWheelSlot;
import net.xmx.velthoric.physics.vehicle.type.VxCar;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * A concrete implementation of a car using the new modular system.
 * This class defines the specific configuration data and model resolutions.
 *
 * @author xI-Mx-Ix
 */
public class CarImpl extends VxCar {

    /**
     * Creates the default configuration for this car model.
     */
    private static VxCarConfig createDefaultConfig() {
        VxCarConfig config = new VxCarConfig("builtin_car");

        // 1. Chassis
        config.setMass(1600.0f);
        config.setChassisSize(1.1f, 0.5f, 2.4f);
        config.setCenterOfMass(0.0f, -0.6f, 0.0f);

        // 2. Engine & Transmission
        config.getEngine()
                .setMaxTorque(7500.0f)
                .setMaxRpm(9000.0f)
                .setMinRpm(1000.0f);

        config.getTransmission()
                .setMode(ETransmissionMode.Manual)
                .setGearRatios(8.5f, 5.2f, 3.6f, 2.7f, 2.1f, 1.7f)
                .setSwitchTime(0.025f)
                .setReverseRatio(-3.0f);

        // 3. Wheel Slots
        float halfWidth = 1.15f;
        float halfLength = 2.0f;
        float yPos = -0.2f;
        float suspMin = 0.3f;
        float suspMax = 0.7f;
        float suspFreq = 1.8f;
        float suspDamp = 0.8f;

        // Add 4 wheels
        config.addWheelSlot(new VehicleWheelSlot("wheel_fl", new Vector3f(-halfWidth, yPos, halfLength))
                .setPowered(false).setSteerable(true)
                .setSuspension(suspMin, suspMax, suspFreq, suspDamp)
                .setMaxSteerAngle(35.0f).setBrakeTorque(8000.0f));

        config.addWheelSlot(new VehicleWheelSlot("wheel_fr", new Vector3f(halfWidth, yPos, halfLength))
                .setPowered(false).setSteerable(true)
                .setSuspension(suspMin, suspMax, suspFreq, suspDamp)
                .setMaxSteerAngle(35.0f).setBrakeTorque(8000.0f));

        config.addWheelSlot(new VehicleWheelSlot("wheel_rl", new Vector3f(-halfWidth, yPos, -halfLength))
                .setPowered(true).setSteerable(false)
                .setSuspension(suspMin, suspMax, suspFreq, suspDamp)
                .setBrakeTorque(4000.0f));

        config.addWheelSlot(new VehicleWheelSlot("wheel_rr", new Vector3f(halfWidth, yPos, -halfLength))
                .setPowered(true).setSteerable(false)
                .setSuspension(suspMin, suspMax, suspFreq, suspDamp)
                .setBrakeTorque(4000.0f));

        config.setDefaultWheel("builtin:sport_wheel");

        // 4. Seat Slots
        config.addSeatSlot(new VehicleSeatSlot("driver", new Vector3f(0.0f, 0.5f, 0.5f)).setDriver(true));
        config.addSeatSlot(new VehicleSeatSlot("passenger", new Vector3f(0.0f, 0.5f, -0.5f)).setDriver(false));

        config.setDefaultSeat("builtin:sport_seat");

        return config;
    }

    public CarImpl(VxBodyType<CarImpl> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id, createDefaultConfig());
    }

    @Environment(EnvType.CLIENT)
    public CarImpl(VxBodyType<CarImpl> type, UUID id) {
        super(type, id, createDefaultConfig());
    }

    @Override
    protected VxWheelDefinition resolveWheelDefinition(String wheelId) {
        // In a real system, this would query a registry.
        return new VxWheelDefinition("builtin:sport_wheel", 0.55f, 0.35f, 1.0f);
    }

    @Override
    protected VxSeatDefinition resolveSeatDefinition(String seatId) {
        return new VxSeatDefinition("builtin:sport_seat", new Vector3f(0.6f, 0.8f, 0.6f));
    }

    @Override
    protected VehicleCollisionTester createCollisionTester() {
        return new VehicleCollisionTesterCastCylinder(VxPhysicsLayers.MOVING);
    }

    @Override
    public int createJoltBody(VxRigidBodyFactory factory) {
        Vector3f extents = config.getChassisHalfExtents();
        try (ShapeSettings chassisShape = new BoxShapeSettings(new Vec3(extents.x, extents.y, extents.z))) {

            Vector3f com = config.getCenterOfMassOffset();
            Vec3 centerOfMassOffset = new Vec3(com.x, com.y, com.z);

            try (
                    ShapeSettings finalShapeSettings = new OffsetCenterOfMassShapeSettings(centerOfMassOffset, chassisShape);
                    BodyCreationSettings bcs = new BodyCreationSettings()
            ) {
                bcs.setShapeSettings(finalShapeSettings);
                bcs.setMotionType(EMotionType.Dynamic);
                bcs.setObjectLayer(VxPhysicsLayers.MOVING);
                bcs.setMotionQuality(EMotionQuality.LinearCast);
                bcs.setMaxLinearVelocity(500.0f);
                bcs.setMaxAngularVelocity(100.0f);
                bcs.getMassPropertiesOverride().setMass(config.getMass());
                bcs.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia);
                return factory.create(finalShapeSettings, bcs);
            }
        }
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void onBodyAdded(ClientLevel level) {
        super.onBodyAdded(level);
        // Register client-side renderers for parts
        for (VxPart part : this.getParts()) {
            if (part instanceof VxVehicleWheel) {
                part.setRenderer(new VxWheelRenderer());
            } else if (part instanceof VxVehicleSeat) {
                part.setRenderer(new VxSeatRenderer());
            }
        }
    }
}