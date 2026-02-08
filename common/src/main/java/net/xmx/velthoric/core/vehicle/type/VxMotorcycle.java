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
import net.xmx.velthoric.core.vehicle.config.VxMotorcycleConfig;
import net.xmx.velthoric.core.vehicle.config.VxEngineConfig;
import net.xmx.velthoric.core.vehicle.config.VxTransmissionConfig;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * Standard motorcycle implementation using the {@link VxMotorcycleConfig}.
 * <p>
 * This class specializes {@link VxWheeledVehicle} for 2-wheeled vehicles.
 * It uses Jolt's {@link MotorcycleController} which includes a specific
 * lean controller to maintain balance.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxMotorcycle extends VxWheeledVehicle {

    private MotorcycleController controller;

    public VxMotorcycle(VxBodyType<? extends VxMotorcycle> type, VxPhysicsWorld world, UUID id, VxMotorcycleConfig config) {
        super(type, world, id, config);
    }

    @Environment(EnvType.CLIENT)
    public VxMotorcycle(VxBodyType<? extends VxMotorcycle> type, UUID id, VxMotorcycleConfig config) {
        super(type, id, config);
    }

    @Override
    public VxMotorcycleConfig getConfig() {
        return (VxMotorcycleConfig) super.getConfig();
    }

    @Override
    protected VehicleConstraintSettings createConstraintSettings(Body body) {
        VehicleConstraintSettings settings = new VehicleConstraintSettings();
        MotorcycleControllerSettings controllerSettings = new MotorcycleControllerSettings();
        VxMotorcycleConfig config = getConfig();

        // 1. Engine
        VxEngineConfig engineData = config.getEngine();
        controllerSettings.getEngine().setMaxTorque(engineData.getMaxTorque());
        controllerSettings.getEngine().setMaxRpm(engineData.getMaxRpm());
        controllerSettings.getEngine().setMinRpm(0.1f);

        // 2. Transmission
        VxTransmissionConfig transData = config.getTransmission();
        controllerSettings.getTransmission().setGearRatios(transData.getGearRatios());
        controllerSettings.getTransmission().setReverseGearRatios(transData.getReverseRatio());
        controllerSettings.getTransmission().setMode(transData.getMode());
        controllerSettings.getTransmission().setSwitchTime(transData.getSwitchTime());
        controllerSettings.getTransmission().setClutchStrength(transData.getClutchStrength());
        controllerSettings.getTransmission().setShiftUpRpm(engineData.getMaxRpm() * 0.9f);

        // 3. Differential (Connecting engine to the rear wheel)
        controllerSettings.setNumDifferentials(1);
        VehicleDifferentialSettings diff = controllerSettings.getDifferential(0);

        int poweredIndex = -1;
        for (int i = 0; i < wheels.size(); i++) {
            if (wheels.get(i).isPowered()) {
                poweredIndex = i;
                break;
            }
        }

        diff.setLeftWheel(-1); // No left wheel
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