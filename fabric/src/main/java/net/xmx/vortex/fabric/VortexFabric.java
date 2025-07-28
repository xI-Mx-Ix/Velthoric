package net.xmx.vortex.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.xmx.vortex.init.VxMainClass;

public final class VortexFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        VxMainClass.onInit();
        onClientInit();
    }

    @Environment(EnvType.CLIENT)
    private void onClientInit() {
        VxMainClass.onClientInit();
    }
}
