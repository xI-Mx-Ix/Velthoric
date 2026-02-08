/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.lifecycle;

import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.event.api.VxLevelEvent;
import net.xmx.velthoric.event.api.VxServerLifecycleEvent;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

/**
 * Handles server and world lifecycle events to manage the physics simulation.
 *
 * @author xI-Mx-Ix
 */
public final class VxServerLifecycleHandler {

    public static void registerEvents() {
        VxLevelEvent.Load.EVENT.register(VxServerLifecycleHandler::onLevelLoad);
        VxLevelEvent.Unload.EVENT.register(VxServerLifecycleHandler::onLevelUnload);
        VxServerLifecycleEvent.Stopping.EVENT.register(VxServerLifecycleHandler::onServerStopping);
        TickEvent.SERVER_LEVEL_PRE.register(VxServerLifecycleHandler::onLevelTick);
    }

    /**
     * Shuts down all physics worlds when the server stops.
     */
    private static void onServerStopping(VxServerLifecycleEvent.Stopping event) {
        VxPhysicsWorld.shutdownAll();
    }

    /**
     * Creates a physics world for a server level when it loads.
     */
    private static void onLevelLoad(VxLevelEvent.Load event) {
        ServerLevel level = event.getLevel();
        if (!level.isClientSide()) {
            VxPhysicsWorld.getOrCreate(level);
        }
    }

    /**
     * Shuts down the physics world for a server level when it unloads.
     */
    private static void onLevelUnload(VxLevelEvent.Unload event) {
        ServerLevel level = event.getLevel();
        if (!level.isClientSide()) {
            VxPhysicsWorld.shutdown(level.dimension());
        }
    }

    /**
     * Ticks the appropriate physics world for a given server level.
     */
    private static void onLevelTick(ServerLevel level) {
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
        if (physicsWorld != null && physicsWorld.isRunning()) {
            physicsWorld.onGameTick(level);
        }
    }
}