/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics;

import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.event.api.VxLevelEvent;
import net.xmx.velthoric.event.api.VxServerLifecycleEvent;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

public final class VxLifecycleEvents {

    public static void registerEvents() {
        VxLevelEvent.Load.EVENT.register(VxLifecycleEvents::onLevelLoad);
        VxLevelEvent.Unload.EVENT.register(VxLifecycleEvents::onLevelUnload);
        VxServerLifecycleEvent.Stopping.EVENT.register(VxLifecycleEvents::onServerStopping);
        TickEvent.SERVER_PRE.register(VxLifecycleEvents::onServerTick);
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

    private static void onServerTick(MinecraftServer server) {
        VxPhysicsWorld.getAll().forEach(world -> {
            world.getObjectManager().onGameTick();
            world.getRidingManager().onGameTick();
        });
    }
}

