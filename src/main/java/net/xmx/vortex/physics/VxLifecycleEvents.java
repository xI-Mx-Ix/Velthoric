package net.xmx.vortex.physics;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.vortex.physics.terrain.job.VxTerrainJobSystem;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

public final class VxLifecycleEvents {

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        VxPhysicsWorld.shutdownAll();
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && !level.isClientSide()) {
            VxPhysicsWorld.getOrCreate(level);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && !level.isClientSide()) {
            VxPhysicsWorld.shutdown(level.dimension());
        }
    }
}