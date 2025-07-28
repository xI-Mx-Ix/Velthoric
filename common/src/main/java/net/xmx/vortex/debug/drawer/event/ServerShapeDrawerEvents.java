package net.xmx.vortex.debug.drawer.event;

import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.xmx.vortex.debug.drawer.ServerShapeDrawerManager;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

public final class ServerShapeDrawerEvents {

    public static void registerEvents() {
        TickEvent.SERVER_POST.register(ServerShapeDrawerEvents::onServerPostTick);
    }

    private static void onServerPostTick(MinecraftServer server) {
        VxPhysicsWorld.getAll().forEach(vxPhysicsWorld -> {
            ServerShapeDrawerManager manager = vxPhysicsWorld.getDebugDrawerManager();
            if (manager != null) {
                manager.tick();
            }
        });
    }
}
