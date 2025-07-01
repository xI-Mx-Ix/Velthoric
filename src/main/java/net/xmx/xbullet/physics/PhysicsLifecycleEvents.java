package net.xmx.xbullet.physics;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.xbullet.init.ModConfig;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManagerRegistry;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorldRegistry;
import net.xmx.xbullet.physics.terrain.manager.TerrainSystemRegistry;

public class PhysicsLifecycleEvents {

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        PhysicsWorldRegistry.getInstance();
        PhysicsObjectManagerRegistry.getInstance();
        TerrainSystemRegistry.getInstance();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TerrainSystemRegistry.getInstance().shutdownAll();
        PhysicsObjectManagerRegistry.getInstance().shutdownAll();
        PhysicsWorldRegistry.getInstance().shutdownAll();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (ModConfig.DISABLED_PHYSICS_DIMENSIONS.get().contains(serverLevel.dimension().location().toString())) {
            return;
        }

        PhysicsWorldRegistry.getInstance().initializeForDimension(serverLevel);
        PhysicsObjectManagerRegistry.getInstance().getManagerForLevel(serverLevel);
        TerrainSystemRegistry.getInstance().getSystemForLevel(serverLevel);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (ModConfig.DISABLED_PHYSICS_DIMENSIONS.get().contains(serverLevel.dimension().location().toString())) {
            return;
        }

        TerrainSystemRegistry.getInstance().removeSystem(serverLevel.dimension());
        PhysicsObjectManagerRegistry.getInstance().removeManager(serverLevel.dimension());
        PhysicsWorldRegistry.getInstance().shutdownForDimension(serverLevel.dimension());
    }
}