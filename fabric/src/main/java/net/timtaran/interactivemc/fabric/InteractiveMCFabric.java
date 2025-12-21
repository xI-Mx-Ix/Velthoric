/*
 * This file is part of InteractiveMC.
 * Licensed under LGPL 3.0.
 */

package net.timtaran.interactivemc.fabric;

import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.fabricmc.api.ModInitializer;
import net.timtaran.interactivemc.init.InteractiveMC;

/**
 * Main class for Fabric integration.
 * <p>
 * Initializes the mod.
 *
 * @author timtaran
 */
public final class InteractiveMCFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        InteractiveMC.onInit();
        if (Platform.getEnvironment() == Env.CLIENT) {
            InteractiveMC.onClientInit();
        }
    }
}