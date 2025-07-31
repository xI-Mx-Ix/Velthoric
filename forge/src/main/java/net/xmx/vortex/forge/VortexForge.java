package net.xmx.vortex.forge;

import dev.architectury.platform.Platform;
import dev.architectury.platform.forge.EventBuses;
import dev.architectury.utils.Env;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.xmx.vortex.init.VxMainClass;

@Mod(VxMainClass.MODID)
public final class VortexForge {
    public VortexForge() {
        EventBuses.registerModEventBus(VxMainClass.MODID, FMLJavaModLoadingContext.get().getModEventBus());
        VxMainClass.onInit();
        if (Platform.getEnvironment() == Env.CLIENT) {
            VxMainClass.onClientInit();
        }
    }
}
