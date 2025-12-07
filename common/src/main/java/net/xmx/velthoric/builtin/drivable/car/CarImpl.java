/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.drivable.car;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.AABB;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.type.factory.VxRigidBodyFactory;
import net.xmx.velthoric.physics.mounting.seat.VxSeat;
import net.xmx.velthoric.physics.vehicle.component.VxVehicleWheel;
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

    /**
     * Server-side constructor.
     *
     * @param type  The body type.
     * @param world The physics world.
     * @param id    The unique ID.
     */
    public CarImpl(VxBodyType<CarImpl> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    /**
     * Client-side constructor.
     *
     * @param type The body type.
     * @param id   The unique ID.
     */
    @Environment(EnvType.CLIENT)
    public CarImpl(VxBodyType<CarImpl> type, UUID id) {
        super(type, id);
    }

    @Override
    protected VxVehicleConfig createConfig() {
        float maxTorque = 7500.0f;
        float maxRpm = 9000.0f;
        float[] gears = new float[]{4.0f, 2.5f, 1.8f, 1.3f, 1.0f};

        VxCarConfig config = new VxCarConfig(1600.0f, maxTorque, maxRpm, gears);

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