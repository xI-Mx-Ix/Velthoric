package net.xmx.vortex.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.xmx.vortex.init.VxMainClass;

@Mod(VxMainClass.MODID)
public final class VortexForge {
    public VortexForge() {
        EventBuses.registerModEventBus(VxMainClass.MODID, FMLJavaModLoadingContext.get().getModEventBus());
        VxMainClass.onInit();
        onClientInit();
    }

    @OnlyIn(Dist.CLIENT)
    private void onClientInit() {
        VxMainClass.onClientInit();
    }
}
