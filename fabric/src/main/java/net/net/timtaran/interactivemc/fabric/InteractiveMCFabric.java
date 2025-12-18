/*
 * This file is part of InteractiveMC.
 * Licensed under LGPL 3.0.
 */

package net.net.timtaran.interactivemc.fabric;

import net.fabricmc.api.ModInitializer;
import net.timtaran.interactivemc.InteractiveMC;

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
    }
}