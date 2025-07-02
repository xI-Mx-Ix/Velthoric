package net.xmx.xbullet.physics;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;

public class PhysicsLifecycleEvents {

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        XBullet.LOGGER.debug("Server is stopping. Shutting down all PhysicsWorld instances.");
        PhysicsWorld.shutdownAll();
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {

        if (event.getLevel() instanceof ServerLevel level && !level.isClientSide()) {
            XBullet.LOGGER.debug("ServerLevel [{}] loaded. Creating and initializing PhysicsWorld.", level.dimension().location());
            PhysicsWorld.getOrCreate(level);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {

        if (event.getLevel() instanceof ServerLevel level && !level.isClientSide()) {
            XBullet.LOGGER.debug("ServerLevel [{}] unloaded. Shutting down PhysicsWorld.", level.dimension().location());
            PhysicsWorld.shutdown(level.dimension());
        }
    }
}