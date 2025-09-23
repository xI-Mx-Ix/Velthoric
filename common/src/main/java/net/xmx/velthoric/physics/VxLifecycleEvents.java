/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics;

import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.event.api.VxLevelEvent;
import net.xmx.velthoric.event.api.VxServerLifecycleEvent;
import net.xmx.velthoric.physics.persistence.VxPersistenceManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

/**
 * Handles server and world lifecycle events to manage the physics simulation.
 *
 * @author xI-Mx-Ix
 */
public final class VxLifecycleEvents {

    public static void registerEvents() {
        VxServerLifecycleEvent.Starting.EVENT.register(VxLifecycleEvents::onServerStarting);
        VxLevelEvent.Load.EVENT.register(VxLifecycleEvents::onLevelLoad);
        VxLevelEvent.Unload.EVENT.register(VxLifecycleEvents::onLevelUnload);
        VxServerLifecycleEvent.Stopping.EVENT.register(VxLifecycleEvents::onServerStopping);
        TickEvent.SERVER_LEVEL_PRE.register(VxLifecycleEvents::onLevelTick);
    }

    /**
     * Initializes persistence systems when the server starts.
     */
    private static void onServerStarting(VxServerLifecycleEvent.Starting event) {
        VxPersistenceManager.initialize();
    }

    /**
     * Shuts down all physics worlds and persistence systems when the server stops.
     * The order is important: first, shut down worlds to trigger final saves,
     * then shut down the I/O executor that performs those saves.
     */
    private static void onServerStopping(VxServerLifecycleEvent.Stopping event) {
        VxPhysicsWorld.shutdownAll();
        VxPersistenceManager.shutdown();
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