/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.drivable.car;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.*;
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
import net.xmx.velthoric.physics.vehicle.type.VxCar;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * A concrete implementation of a car using the new data-driven modular system.
 *
 * @author xI-Mx-Ix
 */
public class CarImpl extends VxCar {

    /**
     * Helper method to create the default configuration for this car.
     * In a production system, this would likely be loaded from a JSON resource.
     *
     * @return The complete vehicle data.
     */
    private static VxVehicleData createDefaultData() {
        VxVehicleData data = new VxVehicleData("builtin_car");

        // 1. Chassis Physics
        data.setMass(1600.0f);
        data.setChassisSize(1.1f, 0.5f, 2.4f); // Half extents
        data.setCenterOfMass(0.0f, -0.6f, 0.0f);

        // 2. Engine & Transmission
        data.getEngine()
                .setMaxTorque(7500.0f)
                .setMaxRpm(9000.0f)
                .setMinRpm(1000.0f);

        data.getTransmission()
                .setMode(ETransmissionMode.Manual)
                .setGearRatios(8.5f, 5.2f, 3.6f, 2.7f, 2.1f, 1.7f)
                .setSwitchTime(0.025f)
                .setReverseRatio(-3.0f);

        // 3. Wheel Slots
        // Position offsets
        float halfWidth = 1.15f;
        float halfLength = 2.0f;
        float yPos = -0.2f;

        // Suspension defaults for this car
        float suspMin = 0.3f;
        float suspMax = 0.7f;
        float suspFreq = 1.8f;
        float suspDamp = 0.8f;

        // Front Left
        data.addWheelSlot(new VehicleWheelSlot("wheel_fl", new Vector3f(-halfWidth, yPos, halfLength))
                .setPowered(false).setSteerable(true)
                .setSuspension(suspMin, suspMax, suspFreq, suspDamp)
                .setMaxSteerAngle(35.0f).setBrakeTorque(8000.0f));

        // Front Right
        data.addWheelSlot(new VehicleWheelSlot("wheel_fr", new Vector3f(halfWidth, yPos, halfLength))
                .setPowered(false).setSteerable(true)
                .setSuspension(suspMin, suspMax, suspFreq, suspDamp)
                .setMaxSteerAngle(35.0f).setBrakeTorque(8000.0f));

        // Rear Left
        data.addWheelSlot(new VehicleWheelSlot("wheel_rl", new Vector3f(-halfWidth, yPos, -halfLength))
                .setPowered(true).setSteerable(false)
                .setSuspension(suspMin, suspMax, suspFreq, suspDamp)
                .setBrakeTorque(4000.0f));

        // Rear Right
        data.addWheelSlot(new VehicleWheelSlot("wheel_rr", new Vector3f(halfWidth, yPos, -halfLength))
                .setPowered(true).setSteerable(false)
                .setSuspension(suspMin, suspMax, suspFreq, suspDamp)
                .setBrakeTorque(4000.0f));

        data.setDefaultWheel("builtin:sport_wheel");

        // 4. Seat Slots
        data.addSeatSlot(new VehicleSeatSlot("driver", new Vector3f(0.0f, 0.5f, 0.5f)).setDriver(true));
        data.addSeatSlot(new VehicleSeatSlot("passenger", new Vector3f(0.0f, 0.5f, -0.5f)).setDriver(false));

        data.setDefaultSeat("builtin:sport_seat");

        return data;
    }

    public CarImpl(VxBodyType<CarImpl> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id, createDefaultData());
    }

    @Environment(EnvType.CLIENT)
    public CarImpl(VxBodyType<CarImpl> type, UUID id) {
        super(type, id, createDefaultData());
    }

    // --- Abstract Implementations ---

    @Override
    protected VxWheelDefinition resolveWheelDefinition(String wheelId) {
        return new VxWheelDefinition("builtin:sport_wheel", 0.55f, 0.35f, 1.0f, "models/wheel.obj", "wheel");
    }

    @Override
    protected VxSeatDefinition resolveSeatDefinition(String seatId) {
        return new VxSeatDefinition("builtin:sport_seat", new Vector3f(0.6f, 0.8f, 0.6f), "models/seat.obj", "seat");
    }

    @Override
    protected VehicleCollisionTester createCollisionTester() {
        return new VehicleCollisionTesterCastCylinder(VxLayers.DYNAMIC);
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

                // Critical for stability at high speeds
                bcs.setMaxLinearVelocity(500.0f);
                bcs.setMaxAngularVelocity(100.0f);

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
        // Register renderers for parts on the client
        for (VxPart part : this.getParts()) {
            if (part instanceof VxVehicleWheel) {
                part.setRenderer(new VxWheelRenderer());
            } else if (part instanceof VxVehicleSeat) {
                part.setRenderer(new VxSeatRenderer());
            }
        }
    }
}