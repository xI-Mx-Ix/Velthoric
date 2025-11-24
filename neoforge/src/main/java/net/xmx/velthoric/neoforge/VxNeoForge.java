/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.neoforge;

import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.neoforged.fml.common.Mod;
import net.xmx.velthoric.init.VxMainClass;

/**
 * Main class for NeoForge integration.
 * <p>
 * Initializes the mod and handles client-side initialization if running on the client.
 *
 * @author xI-Mx-Ix
 */
@Mod(VxMainClass.MODID)
public final class VxNeoForge {
    public VxNeoForge() {
        VxMainClass.onInit();
        if (Platform.getEnvironment() == Env.CLIENT) {
            VxMainClass.onClientInit();
        }
    }
}