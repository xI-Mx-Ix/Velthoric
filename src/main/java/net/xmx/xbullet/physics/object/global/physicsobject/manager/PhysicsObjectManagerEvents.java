package net.xmx.xbullet.physics.object.global.physicsobject.manager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.world.PhysicsWorld;

public class PhysicsObjectManagerEvents {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        PhysicsWorld.getAll().forEach(world -> {

            if (!world.isRunning()) {
                return;
            }

            try {

                world.getObjectManager().serverTick();
            } catch (Exception e) {

                XBullet.LOGGER.error("Error during PhysicsObjectManager tick for {}", world.getDimensionKey().location(), e);
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level) {

            PhysicsWorld world = PhysicsWorld.get(level.dimension());

            if (world != null && world.isRunning()) {
                PhysicsObjectManager manager = world.getObjectManager();

                for (IPhysicsObject obj : manager.getManagedObjects().values()) {
                    manager.getSavedData().updateObjectData(obj);
                }
                manager.getSavedData().setDirty();
            }
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {

            PhysicsWorld world = PhysicsWorld.get(level.dimension());

            if (world != null && world.isRunning()) {
                ChunkPos chunkPos = event.getChunk().getPos();

                world.getObjectManager().loadPhysicsObjectsForChunk(chunkPos);
            }
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ChunkPos chunkPos = event.getChunk().getPos();
            PhysicsObjectManager manager = PhysicsWorld.getObjectManager(level.dimension());
            if (manager != null) {
                manager.unloadPhysicsObjectsForChunk(chunkPos);
            }
        }
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        ServerPlayer player = event.getPlayer();
        ServerLevel level = event.getLevel();
        ChunkPos chunkPos = event.getPos();

        PhysicsObjectManager manager = PhysicsWorld.getObjectManager(level.dimension());
        if (manager != null) {
            PhysicsObjectClientSynchronizer.sendObjectsInChunkToPlayer(manager, chunkPos, player);
        }
    }

    @SubscribeEvent
    public static void onChunkUnwatch(ChunkWatchEvent.UnWatch event) {
        ServerPlayer player = event.getPlayer();
        ServerLevel level = event.getLevel();
        ChunkPos chunkPos = event.getPos();

        PhysicsObjectManager manager = PhysicsWorld.getObjectManager(level.dimension());
        if (manager != null) {
            PhysicsObjectClientSynchronizer.removeObjectsInChunkFromPlayer(manager, chunkPos, player);
        }
    }
}