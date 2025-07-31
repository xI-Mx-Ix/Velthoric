package net.xmx.vortex.fabric;

import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.fabricmc.api.ModInitializer;
import net.xmx.vortex.init.VxMainClass;

public final class VortexFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        VxMainClass.onInit();
        if (Platform.getEnvironment() == Env.CLIENT) {
            VxMainClass.onClientInit();
        }
    }
}
