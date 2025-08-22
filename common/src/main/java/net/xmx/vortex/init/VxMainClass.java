package net.xmx.vortex.init;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.xmx.vortex.builtin.VxRegisteredObjects;
import net.xmx.vortex.init.registry.ModRegistries;
import net.xmx.vortex.network.NetworkHandler;
import net.xmx.vortex.natives.NativeJoltInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VxMainClass {
    public static final String MODID = "vortex";
    public static final Logger LOGGER = LogManager.getLogger();

    public static void onInit() {
        ModRegistries.register();

        VxRegisteredObjects.register();
        NetworkHandler.register();

        RegisterEvents.register();

        try {
            NativeJoltInitializer.initialize();
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