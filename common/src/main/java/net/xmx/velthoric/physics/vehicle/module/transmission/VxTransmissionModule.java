/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.vehicle.module.transmission;

import com.github.stephengold.joltjni.WheeledVehicleController;
import net.xmx.velthoric.bridge.mounting.input.VxMountInput;

/**
 * Interface for translating driver inputs into Jolt vehicle physics commands.
 * <p>
 * Implementations manage the mapping of throttle and brake inputs based on the
 * transmission state (e.g., reversing logic) and handle gear shifting mechanisms.
 *
 * @author xI-Mx-Ix
 */
public interface VxTransmissionModule {

    /**
     * Processes driver input and updates the Jolt vehicle controller.
     *
     * @param dt         The delta time for this simulation step in seconds.
     * @param input      The raw input state from the driver.
     * @param speed      The signed forward speed of the vehicle in m/s.
     * @param controller The Jolt controller instance to update.
     */
    void update(float dt, VxMountInput input, float speed, WheeledVehicleController controller);

    /**
     * Gets the current gear index for display and network synchronization.
     * <p>
     * 0 represents Neutral, -1 represents Reverse, and positive numbers represent forward gears.
     *
     * @return The current gear index.
     */
    int getDisplayGear();
}