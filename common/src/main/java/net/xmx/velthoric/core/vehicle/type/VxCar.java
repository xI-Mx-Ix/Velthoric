/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.vehicle.type;

import com.github.stephengold.joltjni.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.core.body.registry.VxBodyType;
import net.xmx.velthoric.core.vehicle.VxWheeledVehicle;
import net.xmx.velthoric.core.vehicle.config.VxCarConfig;
import net.xmx.velthoric.core.vehicle.config.VxEngineConfig;
import net.xmx.velthoric.core.vehicle.config.VxTransmissionConfig;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * Standard car implementation using the {@link VxCarConfig}.
 * <p>
 * This class specializes {@link VxWheeledVehicle} for 4-wheeled vehicles.
 * It automatically configures differentials based on powered wheel slots
 * and applies car-specific physics settings.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxCar extends VxWheeledVehicle {

    private WheeledVehicleController controller;

    public VxCar(VxBodyType<? extends VxCar> type, VxPhysicsWorld world, UUID id, VxCarConfig config) {
        super(type, world, id, config);
    }

    @Environment(EnvType.CLIENT)
    public VxCar(VxBodyType<? extends VxCar> type, UUID id, VxCarConfig config) {
        super(type, id, config);
    }

    @Override
    public VxCarConfig getConfig() {
        return (VxCarConfig) super.getConfig();
    }

    @Override
    protected VehicleConstraintSettings createConstraintSettings(Body body) {
        VehicleConstraintSettings settings = new VehicleConstraintSettings();
        WheeledVehicleControllerSettings controllerSettings = new WheeledVehicleControllerSettings();
        VxCarConfig config = getConfig();

        // 1. Engine
        VxEngineConfig engineData = config.getEngine();
        controllerSettings.getEngine().setMaxTorque(engineData.getMaxTorque());
        controllerSettings.getEngine().setMaxRpm(engineData.getMaxRpm());
        controllerSettings.getEngine().setMinRpm(0.1f); // Avoid idle creep

        // 2. Transmission
        VxTransmissionConfig transData = config.getTransmission();
        controllerSettings.getTransmission().setGearRatios(transData.getGearRatios());
        controllerSettings.getTransmission().setReverseGearRatios(transData.getReverseRatio());
        controllerSettings.getTransmission().setMode(transData.getMode());
        controllerSettings.getTransmission().setSwitchTime(transData.getSwitchTime());
        controllerSettings.getTransmission().setShiftUpRpm(engineData.getMaxRpm() * 0.9f);

        // 3. Differentials (Auto-detection logic)
        controllerSettings.setNumDifferentials(1);
        VehicleDifferentialSettings diff = controllerSettings.getDifferential(0);

        int drivenWheelsFound = 0;
        int lastDrivenIndex = -1;
        int secondLastDrivenIndex = -1;

        // Scan wheels to find which are powered
        for (int i = 0; i < wheels.size(); i++) {
            if (wheels.get(i).isPowered()) {
                secondLastDrivenIndex = lastDrivenIndex;
                lastDrivenIndex = i;
                drivenWheelsFound++;
            }
        }

        if (drivenWheelsFound >= 2) {
            // Standard differential connecting two wheels
            diff.setLeftWheel(secondLastDrivenIndex);
            diff.setRightWheel(lastDrivenIndex);
            diff.setDifferentialRatio(3.42f);
            diff.setLimitedSlipRatio(1.4f);
            diff.setEngineTorqueRatio(1.0f);
        } else {
            // Fallback for FWD or incomplete config
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