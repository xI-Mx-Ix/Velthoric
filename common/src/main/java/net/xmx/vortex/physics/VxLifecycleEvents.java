package net.xmx.vortex.physics;

import net.minecraft.server.level.ServerLevel;
import net.xmx.vortex.event.api.VxLevelEvent;
import net.xmx.vortex.event.api.VxServerLifecycleEvent;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

public final class VxLifecycleEvents {

    public static void registerEvents() {
        VxLevelEvent.Load.EVENT.register(VxLifecycleEvents::onLevelLoad);
        VxLevelEvent.Unload.EVENT.register(VxLifecycleEvents::onLevelUnload);
        VxServerLifecycleEvent.Stopping.EVENT.register(VxLifecycleEvents::onServerStopping);
    }

    private static void onServerStopping(VxServerLifecycleEvent.Stopping event) {
        VxPhysicsWorld.shutdownAll();
    }

    private static void onLevelLoad(VxLevelEvent.Load event) {
        ServerLevel level = event.getLevel();
        if (!level.isClientSide()) {
            VxPhysicsWorld.getOrCreate(level);
        }
    }

    private static void onLevelUnload(VxLevelEvent.Unload event) {
        ServerLevel level = event.getLevel();
        if (!level.isClientSide()) {
            VxPhysicsWorld.shutdown(level.dimension());
        }
    }
}
