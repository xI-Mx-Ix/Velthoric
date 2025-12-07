/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.type;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.ETransmissionMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.mounting.input.VxMountInput;
import net.xmx.velthoric.physics.vehicle.VxVehicle;
import net.xmx.velthoric.physics.vehicle.component.VxVehicleEngine;
import net.xmx.velthoric.physics.vehicle.component.VxVehicleTransmission;
import net.xmx.velthoric.physics.vehicle.config.VxCarConfig;
import net.xmx.velthoric.physics.vehicle.config.VxVehicleConfig;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * Standard car implementation.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxCar extends VxVehicle {

    private WheeledVehicleController controller;

    public VxCar(VxBodyType<? extends VxCar> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    @Environment(EnvType.CLIENT)
    public VxCar(VxBodyType<? extends VxCar> type, UUID id) {
        super(type, id);
    }

    @Override
    protected VxVehicleConfig createConfig() {
        // Subclasses should return specific config
        return null;
    }

    /**
     * Called by subclass when config is known to initialize components specific to cars.
     *
     * @param config The car configuration.
     */
    protected void applyCarConfig(VxCarConfig config) {
        this.config = config;
        // Visual/Sound engine keeps 1000 RPM for aesthetics
        this.engine = new VxVehicleEngine(config.getMaxTorque(), 1000f, config.getMaxRpm());
        this.transmission = new VxVehicleTransmission(config.getGearRatios(), -3.0f);
    }

    @Override
    protected VehicleConstraintSettings createConstraintSettings(Body body) {
        VehicleConstraintSettings settings = new VehicleConstraintSettings();
        WheeledVehicleControllerSettings controllerSettings = new WheeledVehicleControllerSettings();

        // Transfer Config Settings to Jolt
        if (this.config instanceof VxCarConfig carConfig) {
            controllerSettings.getEngine().setMaxTorque(carConfig.getMaxTorque());
            controllerSettings.getEngine().setMaxRpm(carConfig.getMaxRpm());

            // This is the shift point for automatic transmission
            controllerSettings.getTransmission().setShiftUpRpm(carConfig.getMaxRpm() * 0.9f);
        } else {
            controllerSettings.getTransmission().setShiftUpRpm(6000f);
        }

        // Apply global configuration settings
        // Setting physical minRPM to almost zero prevents the vehicle from moving forward
        // automatically (idle creep) when in gear.
        controllerSettings.getEngine().setMinRpm(0.1f);
        controllerSettings.getTransmission().setMode(this.config.getTransmissionMode());

        // Configure Differentials (RWD Logic)
        controllerSettings.setNumDifferentials(1);
        VehicleDifferentialSettings diff = controllerSettings.getDifferential(0);

        // Find driven wheels from config
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

        if (drivenWheelsFound >= 2) {
            diff.setLeftWheel(secondLastDrivenIndex);
            diff.setRightWheel(lastDrivenIndex);
            diff.setDifferentialRatio(3.42f);
            diff.setLimitedSlipRatio(1.4f);
            diff.setEngineTorqueRatio(1.0f);
        } else {
            // Fallback if not enough powered wheels defined
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

    @Override
    protected void updateJoltController() {
        super.updateJoltController();
        if (controller == null) return;

        float throttle = getInputThrottle();
        float brake = 0.0f;

        // Basic Logic: If we are in forward gear but throttle is negative, apply brake.
        // If we are in reverse gear but throttle is positive, apply brake.
        int gear = transmission.getGear();
        if (gear > 0 && throttle < 0) {
            brake = Math.abs(throttle);
            throttle = 0;
        } else if (gear == -1 && throttle > 0) {
            brake = throttle;
            throttle = 0;
        }

        // Hill hold / Auto brake when stopping
        if (throttle == 0 && brake == 0 && Math.abs(getSpeedKmh()) < 1.0f) {
            brake = 0.5f;
        }

        float handbrake = currentInput.hasAction(VxMountInput.FLAG_HANDBRAKE) ? 1.0f : 0.0f;

        controller.setDriverInput(throttle, getInputSteer(), brake, handbrake);
    }
}