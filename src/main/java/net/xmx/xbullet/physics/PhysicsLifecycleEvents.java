package net.xmx.xbullet.physics;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.xbullet.physics.world.PhysicsWorld;

public final class PhysicsLifecycleEvents {

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PhysicsWorld.shutdownAll();
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && !level.isClientSide()) {
            PhysicsWorld.getOrCreate(level);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {

        if (event.getLevel() instanceof ServerLevel level && !level.isClientSide()) {
            PhysicsWorld.shutdown(level.dimension());
        }
    }
}