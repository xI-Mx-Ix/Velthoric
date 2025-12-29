/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.type;

import com.github.stephengold.joltjni.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.vehicle.VxVehicle;
import net.xmx.velthoric.physics.vehicle.data.VehicleEngineData;
import net.xmx.velthoric.physics.vehicle.data.VehicleTransmissionData;
import net.xmx.velthoric.physics.vehicle.data.VxVehicleData;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * Standard motorcycle implementation.
 * <p>
 * This class extends {@link VxVehicle} to provide specific logic for 2-wheeled vehicles
 * using Jolt's {@link MotorcycleController}. It handles the specific lean physics
 * and balance controllers required for bikes.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxMotorcycle extends VxVehicle {

    /**
     * The Jolt controller interface specific to motorcycles.
     */
    private MotorcycleController controller;

    /**
     * Server-side constructor.
     *
     * @param type  The body type.
     * @param world The physics world.
     * @param id    The unique ID.
     * @param data  The vehicle data configuration.
     */
    public VxMotorcycle(VxBodyType<? extends VxMotorcycle> type, VxPhysicsWorld world, UUID id, VxVehicleData data) {
        super(type, world, id, data);
    }

    /**
     * Client-side constructor.
     *
     * @param type The body type.
     * @param id   The unique ID.
     * @param data The vehicle data configuration.
     */
    @Environment(EnvType.CLIENT)
    public VxMotorcycle(VxBodyType<? extends VxMotorcycle> type, UUID id, VxVehicleData data) {
        super(type, id, data);
    }

    // --- Abstract Implementation ---

    @Override
    protected VehicleConstraintSettings createConstraintSettings(Body body) {
        VehicleConstraintSettings settings = new VehicleConstraintSettings();
        MotorcycleControllerSettings controllerSettings = new MotorcycleControllerSettings();

        // 1. Transfer Engine Settings from Data
        VehicleEngineData engineData = vehicleData.getEngine();
        controllerSettings.getEngine().setMaxTorque(engineData.getMaxTorque());
        controllerSettings.getEngine().setMaxRpm(engineData.getMaxRpm());

        // Setting physical minRPM to almost zero prevents the motorcycle from moving forward
        // automatically (idle creep) when in gear.
        controllerSettings.getEngine().setMinRpm(0.1f);

        // 2. Transfer Transmission Settings from Data
        VehicleTransmissionData transData = vehicleData.getTransmission();
        controllerSettings.getTransmission().setGearRatios(transData.getGearRatios());
        controllerSettings.getTransmission().setReverseGearRatios(transData.getReverseRatio());
        controllerSettings.getTransmission().setMode(transData.getMode());
        controllerSettings.getTransmission().setSwitchTime(transData.getSwitchTime());
        controllerSettings.getTransmission().setClutchStrength(transData.getClutchStrength());

        // Shift up point
        controllerSettings.getTransmission().setShiftUpRpm(engineData.getMaxRpm() * 0.9f);

        // 3. Configure Differential (Connecting engine to the rear wheel)
        controllerSettings.setNumDifferentials(1);
        VehicleDifferentialSettings diff = controllerSettings.getDifferential(0);

        // Find the powered wheel (usually the rear one)
        int poweredIndex = -1;
        for (int i = 0; i < wheels.size(); i++) {
            if (wheels.get(i).isPowered()) {
                poweredIndex = i;
                break;
            }
        }

        // For motorcycles, we typically drive one wheel.
        diff.setLeftWheel(-1); // No left wheel pairing
        diff.setRightWheel(poweredIndex != -1 ? poweredIndex : 1);
        diff.setDifferentialRatio(4.0f);

        settings.setController(controllerSettings);

        // Allow the bike to lean up to 60 degrees
        settings.setMaxPitchRollAngle((float) Math.toRadians(60));
        settings.setUp(new Vec3(0, 1, 0));

        return settings;
    }

    @Override
    protected void updateJoltControllerReference() {
        if (constraint != null && constraint.getController() instanceof MotorcycleController c) {
            this.controller = c;
            // Enable the built-in lean controller to stabilize the bike
            c.enableLeanController(true);
        }
    }
}