/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.init;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.velthoric.builtin.VxRegisteredObjects;
import net.xmx.velthoric.init.registry.ModRegistries;
import net.xmx.velthoric.network.NetworkHandler;
import net.xmx.velthoric.natives.VxNativeJolt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VxMainClass {
    public static final String MODID = "velthoric";
    public static final Logger LOGGER = LogManager.getLogger("Velthoric");

    public static void onInit() {
        ModRegistries.register();

        VxRegisteredObjects.register();
        NetworkHandler.register();

        RegisterEvents.register();

        try {
            VxNativeJolt.initialize();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Environment(EnvType.CLIENT)
    public static void onClientInit() {
        VxRegisteredObjects.registerClientRenderers();

        RegisterEvents.registerClient();
    }
}
