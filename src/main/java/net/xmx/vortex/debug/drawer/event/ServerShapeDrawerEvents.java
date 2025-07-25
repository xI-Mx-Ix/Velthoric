package net.xmx.vortex.debug.drawer.event;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.vortex.debug.drawer.ServerShapeDrawerManager;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

public final class ServerShapeDrawerEvents {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            VxPhysicsWorld.getAll().forEach(vxPhysicsWorld -> {
                ServerShapeDrawerManager manager = vxPhysicsWorld.getDebugDrawerManager();
                if (manager != null) {
                    manager.tick();
                }
            });
        }
    }
}