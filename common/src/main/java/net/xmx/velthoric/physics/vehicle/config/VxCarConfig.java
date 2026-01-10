/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.config;

/**
 * Specific configuration for Cars (4+ wheels).
 * <p>
 * This class exists to allow future expansion for car-specific features,
 * such as complex differential settings or aerodynamic downforce curves,
 * without polluting the motorcycle configuration.
 *
 * @author xI-Mx-Ix
 */
public class VxCarConfig extends VxWheeledVehicleConfig {

    public VxCarConfig(String id) {
        super(id);
    }
}