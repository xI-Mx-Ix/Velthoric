/*
 * This file is part of InteractiveMC.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.neoforge;

import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.neoforged.fml.common.Mod;
import net.timtaran.interactivemc.init.InteractiveMC;

/**
 * Main class for NeoForge integration.
 * <p>
 * Initializes the mod.
 *
 * @author timtaran
 */

@Mod(InteractiveMC.MOD_ID)
public final class InteractiveMCNeoForge {
    public InteractiveMCNeoForge() {
        InteractiveMC.onInit();
        if (Platform.getEnvironment() == Env.CLIENT) {
            InteractiveMC.onClientInit();
        }
    }
}