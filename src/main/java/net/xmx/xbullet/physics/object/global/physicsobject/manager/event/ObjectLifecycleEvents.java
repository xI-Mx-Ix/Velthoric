package net.xmx.xbullet.physics.object.global.physicsobject.manager.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.ObjectManager;
import net.xmx.xbullet.physics.world.PhysicsWorld;

public class ObjectLifecycleEvents {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        PhysicsWorld.getAll().forEach(world -> {
            if (world.isRunning()) {
                try {
                    world.getObjectManager().serverTick();
                } catch (Exception e) {
                    XBullet.LOGGER.error("Error during ObjectManager tick for {}", world.getDimensionKey().location(), e);
                }
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PhysicsWorld world = PhysicsWorld.get(level.dimension());
            if (world != null && world.isRunning()) {
                ObjectManager manager = world.getObjectManager();
                if (manager != null && manager.isInitialized()) {
                    manager.getDataSystem().saveAll(manager.getManagedObjects().values());
                }
            }
        }
    }
}