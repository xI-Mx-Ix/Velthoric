/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.config;

/**
 * Specific configuration for Motorcycles (2 wheels).
 * <p>
 * This class exists to allow future expansion for bike-specific features,
 * such as lean angle limits or balance controller PID settings.
 *
 * @author xI-Mx-Ix
 */
public class VxMotorcycleConfig extends VxWheeledVehicleConfig {

    public VxMotorcycleConfig(String id) {
        super(id);
    }
}