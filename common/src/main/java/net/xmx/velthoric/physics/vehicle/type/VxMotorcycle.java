/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.type;

import com.github.stephengold.joltjni.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.mounting.input.VxMountInput;
import net.xmx.velthoric.physics.vehicle.VxVehicle;
import net.xmx.velthoric.physics.vehicle.component.VxVehicleEngine;
import net.xmx.velthoric.physics.vehicle.component.VxVehicleTransmission;
import net.xmx.velthoric.physics.vehicle.config.VxMotorcycleConfig;
import net.xmx.velthoric.physics.vehicle.config.VxVehicleConfig;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * Standard motorcycle implementation.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxMotorcycle extends VxVehicle {

    private MotorcycleController controller;

    public VxMotorcycle(VxBodyType<? extends VxMotorcycle> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
    }

    @Environment(EnvType.CLIENT)
    public VxMotorcycle(VxBodyType<? extends VxMotorcycle> type, UUID id) {
        super(type, id);
    }

    @Override
    protected VxVehicleConfig createConfig() {
        return null;
    }

    /**
     * Called by subclass when config is known to initialize components specific to motorcycles.
     *
     * @param config The motorcycle configuration.
     */
    protected void applyMotoConfig(VxMotorcycleConfig config) {
        this.config = config;
        this.engine = new VxVehicleEngine(config.getMaxTorque(), 1000f, config.getMaxRpm());
        this.transmission = new VxVehicleTransmission(config.getGearRatios(), -3.0f);
    }

    @Override
    protected VehicleConstraintSettings createConstraintSettings(Body body) {
        VehicleConstraintSettings settings = new VehicleConstraintSettings();
        MotorcycleControllerSettings controllerSettings = new MotorcycleControllerSettings();

        if (this.config instanceof VxMotorcycleConfig motoConfig) {
            controllerSettings.getEngine().setMaxTorque(motoConfig.getMaxTorque());
            controllerSettings.getEngine().setMaxRpm(motoConfig.getMaxRpm());
            controllerSettings.getTransmission().setGearRatios(motoConfig.getGearRatios());
        }

        // Apply global configuration settings
        // Setting physical minRPM to almost zero prevents the motorcycle from moving forward
        // automatically (idle creep) when in gear.
        controllerSettings.getEngine().setMinRpm(0.1f);
        controllerSettings.getTransmission().setMode(this.config.getTransmissionMode());

        // Configure Differential (Connecting engine to the rear wheel)
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

        diff.setLeftWheel(-1); // No left wheel driven for bikes usually
        diff.setRightWheel(poweredIndex != -1 ? poweredIndex : 1);
        diff.setDifferentialRatio(4.0f);

        settings.setController(controllerSettings);
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

    @Override
    protected void updateJoltController() {
        super.updateJoltController();
        if (controller == null) return;

        float throttle = getInputThrottle();
        float brake = 0.0f;

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