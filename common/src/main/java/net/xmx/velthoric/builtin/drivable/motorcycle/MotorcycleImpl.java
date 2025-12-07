/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.builtin.drivable.motorcycle;

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
import net.xmx.velthoric.physics.vehicle.config.VxMotorcycleConfig;
import net.xmx.velthoric.physics.vehicle.config.VxVehicleConfig;
import net.xmx.velthoric.physics.vehicle.type.VxMotorcycle;
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

    private static final Vec3 CHASSIS_HALF_EXTENTS = new Vec3(0.2f, 0.3f, 0.4f);

    /**
     * Server-side constructor.
     *
     * @param type  The body type.
     * @param world The physics world.
     * @param id    The unique ID.
     */
    public MotorcycleImpl(VxBodyType<MotorcycleImpl> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    /**
     * Client-side constructor.
     *
     * @param type The body type.
     * @param id   The unique ID.
     */
    @Environment(EnvType.CLIENT)
    public MotorcycleImpl(VxBodyType<MotorcycleImpl> type, UUID id) {
        super(type, id);
    }

    @Override
    protected VxVehicleConfig createConfig() {
        float maxTorque = 150.0f;
        float maxRpm = 10000.0f;
        float[] gears = new float[]{2.27f, 1.63f, 1.3f, 1.09f, 0.96f, 0.88f};

        VxMotorcycleConfig config = new VxMotorcycleConfig(240.0f, maxTorque, maxRpm, gears);

        float wheelRadius = 0.31f;
        float wheelWidth = 0.05f;
        float halfVehicleHeight = 0.3f;
        float yPos = -0.9f * halfVehicleHeight;

        config.addWheel(new Vec3(0.0f, yPos, 0.75f), wheelRadius, wheelWidth, false, true);
        config.addWheel(new Vec3(0.0f, yPos, -0.75f), wheelRadius, wheelWidth, true, false);

        this.applyMotoConfig(config);

        return config;
    }

    @Override
    public void onBodyAdded(VxPhysicsWorld world) {
        super.onBodyAdded(world);

        float casterAngle = degreesToRadians(30);
        Vec3 suspensionDir = new Vec3(0, -1, (float) Math.tan(casterAngle)).normalized();
        Vec3 steeringAxis = minus(suspensionDir);

        VxVehicleWheel front = this.getWheels().get(0);
        WheelSettingsWv frontSettings = front.getSettings();
        frontSettings.setSuspensionDirection(suspensionDir);
        frontSettings.setSteeringAxis(steeringAxis);
        frontSettings.setMaxSteerAngle(degreesToRadians(30));
        frontSettings.setSuspensionMinLength(0.3f);
        frontSettings.setSuspensionMaxLength(0.5f);
        frontSettings.getSuspensionSpring().setFrequency(1.5f);
        frontSettings.setMaxBrakeTorque(500.0f);

        VxVehicleWheel back = this.getWheels().get(1);
        WheelSettingsWv backSettings = back.getSettings();
        backSettings.setMaxSteerAngle(0.0f);
        backSettings.setSuspensionMinLength(0.3f);
        backSettings.setSuspensionMaxLength(0.5f);
        backSettings.getSuspensionSpring().setFrequency(2.0f);
        backSettings.setMaxBrakeTorque(250.0f);

        if (constraint != null && constraint.getController() instanceof MotorcycleController controller) {
            controller.getTransmission().setClutchStrength(2.0f);
        }
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
        try (ShapeSettings chassisShape = new BoxShapeSettings(CHASSIS_HALF_EXTENTS)) {
            Vec3 centerOfMassOffset = new Vec3(0, -CHASSIS_HALF_EXTENTS.getY(), 0);

            try (
                    ShapeSettings finalShapeSettings = new OffsetCenterOfMassShapeSettings(centerOfMassOffset, chassisShape);
                    BodyCreationSettings bcs = new BodyCreationSettings()
            ) {
                bcs.setShapeSettings(finalShapeSettings);
                bcs.setMotionType(EMotionType.Dynamic);
                bcs.setObjectLayer(VxLayers.DYNAMIC);
                bcs.setMotionQuality(EMotionQuality.LinearCast);
                bcs.getMassPropertiesOverride().setMass(config.getMass());
                bcs.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia);

                return factory.create(finalShapeSettings, bcs);
            }
        }
    }
}