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
import net.minecraft.world.phys.AABB;
import net.xmx.velthoric.builtin.drivable.renderer.VxSeatRenderer;
import net.xmx.velthoric.builtin.drivable.renderer.VxWheelRenderer;
import net.xmx.velthoric.physics.vehicle.part.VxPart;
import net.xmx.velthoric.physics.vehicle.part.impl.VxVehicleSeat;
import net.xmx.velthoric.physics.vehicle.part.impl.VxVehicleWheel;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.type.factory.VxRigidBodyFactory;
import net.xmx.velthoric.physics.mounting.seat.VxSeat;
import net.xmx.velthoric.physics.vehicle.config.VxCarConfig;
import net.xmx.velthoric.physics.vehicle.config.VxVehicleConfig;
import net.xmx.velthoric.physics.vehicle.type.VxCar;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * A concrete implementation of a car using the new modular system.
 *
 * @author xI-Mx-Ix
 */
public class CarImpl extends VxCar {

    private static final Vec3 CHASSIS_HALF_EXTENTS = new Vec3(1.1f, 0.5f, 2.4f);

    public CarImpl(VxBodyType<CarImpl> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    @Environment(EnvType.CLIENT)
    public CarImpl(VxBodyType<CarImpl> type, UUID id) {
        super(type, id);
    }

    @Override
    protected VxVehicleConfig createConfig() {
        float maxTorque = 7500.0f;
        float maxRpm = 9000.0f;
        float[] gears = new float[]{8.5f, 5.2f, 3.6f, 2.7f, 2.1f, 1.7f};
        VxCarConfig config = new VxCarConfig(1600.0f, maxTorque, maxRpm, gears, ETransmissionMode.Manual);

        float wheelRadius = 0.55f;
        float wheelWidth = 0.35f;
        float halfWidth = 1.15f;
        float halfLength = 2.0f;
        float yPos = -0.2f;

        config.addWheel(new Vec3(-halfWidth, yPos, halfLength), wheelRadius, wheelWidth, false, true);
        config.addWheel(new Vec3(halfWidth, yPos, halfLength), wheelRadius, wheelWidth, false, true);
        config.addWheel(new Vec3(-halfWidth, yPos, -halfLength), wheelRadius, wheelWidth, true, false);
        config.addWheel(new Vec3(halfWidth, yPos, -halfLength), wheelRadius, wheelWidth, true, false);

        this.applyCarConfig(config);
        return config;
    }

    @Override
    public void onBodyAdded(VxPhysicsWorld world) {
        super.onBodyAdded(world);
        float suspensionFreq = 1.8f;
        float suspensionDamping = 0.8f;

        for (VxVehicleWheel wheel : this.getWheels()) {
            WheelSettingsWv settings = wheel.getSettings();
            settings.setSuspensionMinLength(0.3f);
            settings.setSuspensionMaxLength(0.7f);
            settings.getSuspensionSpring().setFrequency(suspensionFreq);
            settings.getSuspensionSpring().setDamping(suspensionDamping);

            if (wheel.isSteerable()) {
                settings.setMaxSteerAngle((float) Math.toRadians(35.0));
                settings.setMaxBrakeTorque(8000.0f);
            } else {
                settings.setMaxSteerAngle(0f);
                settings.setMaxBrakeTorque(4000.0f);
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

    @Override
    protected VehicleCollisionTester createCollisionTester() {
        return new VehicleCollisionTesterCastCylinder(VxLayers.DYNAMIC);
    }

    @Override
    public void defineSeats(VxSeat.Builder builder) {
        // Front seat (driver)
        Vector3f frontOffset = new Vector3f(0.0f, 0.5f, 0.5f);

        // Define AABB centered at (0,0,0). The offset determines placement in the vehicle.
        AABB frontAABB = new AABB(-0.3, -0.4, -0.3, 0.3, 0.4, 0.3);
        VxSeat frontSeat = new VxSeat(this.getPhysicsId(), "front_seat", frontAABB, frontOffset, true);

        // Rear seat (passenger)
        Vector3f rearOffset = new Vector3f(0.0f, 0.5f, -0.5f);

        // Define AABB centered at (0,0,0).
        AABB rearAABB = new AABB(-0.3, -0.4, -0.3, 0.3, 0.4, 0.3);
        VxSeat rearSeat = new VxSeat(this.getPhysicsId(), "rear_seat", rearAABB, rearOffset, false);

        builder.addSeat(frontSeat);
        builder.addSeat(rearSeat);
    }

    @Override
    public int createJoltBody(VxRigidBodyFactory factory) {
        try (ShapeSettings chassisShape = new BoxShapeSettings(CHASSIS_HALF_EXTENTS)) {
            Vec3 centerOfMassOffset = new Vec3(0f, -0.6f, 0f);
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
                bcs.getMassPropertiesOverride().setMass(config.getMass());
                bcs.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia);
                return factory.create(finalShapeSettings, bcs);
            }
        }
    }
}