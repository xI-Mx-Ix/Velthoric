/*
This file is part of Velthoric.
Licensed under LGPL 3.0.
*/
package net.xmx.velthoric.forge;

import dev.architectury.platform.Platform;
import dev.architectury.platform.forge.EventBuses;
import dev.architectury.utils.Env;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.xmx.velthoric.init.VxMainClass;

@Mod(VxMainClass.MODID)
public final class VxForge {
    public VxForge() {
        EventBuses.registerModEventBus(VxMainClass.MODID, FMLJavaModLoadingContext.get().getModEventBus());
        VxMainClass.onInit();
        if (Platform.getEnvironment() == Env.CLIENT) {
            VxMainClass.onClientInit();
        }
    }
}
