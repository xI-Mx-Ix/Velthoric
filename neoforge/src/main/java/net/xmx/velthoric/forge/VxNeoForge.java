/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.forge;

import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.neoforged.fml.common.Mod;
import net.xmx.velthoric.init.VxMainClass;

@Mod(VxMainClass.MODID)
public final class VxNeoForge {
    public VxNeoForge() {
        VxMainClass.onInit();
        if (Platform.getEnvironment() == Env.CLIENT) {
            VxMainClass.onClientInit();
        }
    }
}