/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.fabric;

import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.fabricmc.api.ModInitializer;
import net.xmx.velthoric.init.VxMainClass;

/**
 * Main class for Fabric integration.
 * <p>
 * Initializes the mod and handles client-side initialization if running on the client.
 *
 * @author xI-Mx-Ix
 */
public final class VxFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        VxMainClass.onInit();
        if (Platform.getEnvironment() == Env.CLIENT) {
            VxMainClass.onClientInit();
        }
    }
}