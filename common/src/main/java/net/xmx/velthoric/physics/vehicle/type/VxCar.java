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
 * Standard car implementation.
 * <p>
 * This class extends {@link VxVehicle} to provide specific logic for 4-wheeled vehicles
 * using Jolt's {@link WheeledVehicleController}. It handles the setup of differentials
 * and specific input mappings for cars.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxCar extends VxVehicle {

    /**
     * The Jolt controller interface specific to cars.
     */
    private WheeledVehicleController controller;

    /**
     * Server-side constructor.
     *
     * @param type  The body type.
     * @param world The physics world.
     * @param id    The unique ID.
     * @param data  The vehicle data configuration.
     */
    public VxCar(VxBodyType<? extends VxCar> type, VxPhysicsWorld world, UUID id, VxVehicleData data) {
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
    public VxCar(VxBodyType<? extends VxCar> type, UUID id, VxVehicleData data) {
        super(type, id, data);
    }

    // --- Abstract Implementation ---

    @Override
    protected VehicleConstraintSettings createConstraintSettings(Body body) {
        VehicleConstraintSettings settings = new VehicleConstraintSettings();
        WheeledVehicleControllerSettings controllerSettings = new WheeledVehicleControllerSettings();

        // 1. Transfer Engine Settings from Data
        VehicleEngineData engineData = vehicleData.getEngine();
        controllerSettings.getEngine().setMaxTorque(engineData.getMaxTorque());
        controllerSettings.getEngine().setMaxRpm(engineData.getMaxRpm());

        // Setting physical minRPM to almost zero prevents the vehicle from moving forward
        // automatically (idle creep) when in gear.
        controllerSettings.getEngine().setMinRpm(0.1f);

        // 2. Transfer Transmission Settings from Data
        VehicleTransmissionData transData = vehicleData.getTransmission();
        controllerSettings.getTransmission().setGearRatios(transData.getGearRatios());
        controllerSettings.getTransmission().setReverseGearRatios(transData.getReverseRatio());
        controllerSettings.getTransmission().setMode(transData.getMode());
        controllerSettings.getTransmission().setSwitchTime(transData.getSwitchTime());

        // Calculate shift point relative to redline (e.g. 90%)
        controllerSettings.getTransmission().setShiftUpRpm(engineData.getMaxRpm() * 0.9f);

        // 3. Configure Differentials (Auto-detect from Wheel Setup)
        controllerSettings.setNumDifferentials(1);
        VehicleDifferentialSettings diff = controllerSettings.getDifferential(0);

        // Find which wheels are powered to set up the differential correctly
        int drivenWheelsFound = 0;
        int lastDrivenIndex = -1;
        int secondLastDrivenIndex = -1;

        for (int i = 0; i < wheels.size(); i++) {
            if (wheels.get(i).isPowered()) {
                secondLastDrivenIndex = lastDrivenIndex;
                lastDrivenIndex = i;
                drivenWheelsFound++;
            }
        }

        // If we have at least two driven wheels (e.g., Rear Left and Rear Right), connect them.
        if (drivenWheelsFound >= 2) {
            diff.setLeftWheel(secondLastDrivenIndex);
            diff.setRightWheel(lastDrivenIndex);
            diff.setDifferentialRatio(3.42f); // Standard Final Drive ratio
            diff.setLimitedSlipRatio(1.4f);
            diff.setEngineTorqueRatio(1.0f);
        } else {
            // Fallback if not enough powered wheels defined (e.g. FWD or error)
            diff.setLeftWheel(0);
            diff.setRightWheel(1);
        }

        settings.setController(controllerSettings);
        settings.setUp(new Vec3(0, 1, 0));

        return settings;
    }

    @Override
    protected void updateJoltControllerReference() {
        if (constraint != null && constraint.getController() instanceof WheeledVehicleController c) {
            this.controller = c;
        }
    }
}