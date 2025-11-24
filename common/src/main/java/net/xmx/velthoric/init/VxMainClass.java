/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.init;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.builtin.VxRegisteredBodies;
import net.xmx.velthoric.init.registry.ModRegistries;
import net.xmx.velthoric.natives.VxNativeManager;
import net.xmx.velthoric.network.VxPacketHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The main entry point for the Velthoric mod.
 * <p>
 * This class handles the initialization of registries, networking, event listeners,
 * and the critical loading of Jolt Physics native libraries.
 *
 * @author xI-Mx-Ix
 */
public class VxMainClass {
    public static final String MODID = "velthoric";
    public static final Logger LOGGER = LogManager.getLogger("Velthoric");

    /**
     * Called during the common initialization phase (Server and Client).
     * Initializes registries, packets, and loads native libraries.
     */
    public static void onInit() {
        ModRegistries.register();
        VxRegisteredBodies.register();
        VxPacketHandler.register();
        RegisterEvents.register();
        VxNativeManager.initialize();
    }

    /**
     * Called during the client-specific initialization phase.
     * Sets up rendering factories and client-side event listeners.
     */
    @Environment(EnvType.CLIENT)
    public static void onClientInit() {
        VxRegisteredBodies.registerClientFactories();
        VxRegisteredBodies.registerClientRenderers();
        RegisterEvents.registerClient();
    }
}