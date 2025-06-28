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

public class PhysicsObjectManagerEvents {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            PhysicsObjectManagerRegistry.getInstance().getAllManagers().forEach((dim, manager) -> {
                if (manager.isInitialized()) {
                    try {
                        manager.serverTick();
                    } catch (Exception e) {
                        XBullet.LOGGER.error("Error during PhysicsObjectManager tick for {}", dim.location(), e);
                    }
                }
            });
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PhysicsObjectManager manager = PhysicsObjectManagerRegistry.getInstance().getManagerForLevel(level);
            if (manager != null && manager.isInitialized()) {
                for (IPhysicsObject obj : manager.managedObjects.values()) {
                    manager.savedData.updateObjectData(obj);
                }
                manager.savedData.setDirty();
            }
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ChunkPos chunkPos = event.getChunk().getPos();
            PhysicsObjectManager manager = PhysicsObjectManagerRegistry.getInstance().getManagerForLevel(level);
            if (manager != null) {
                manager.loadPhysicsObjectsForChunk(chunkPos);
            }
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ChunkPos chunkPos = event.getChunk().getPos();
            PhysicsObjectManager manager = PhysicsObjectManagerRegistry.getInstance().getManagerForLevel(level);
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

        PhysicsObjectManager manager = PhysicsObjectManagerRegistry.getInstance().getManagerForLevel(level);
        if (manager != null) {
            manager.sendObjectsInChunkToPlayer(chunkPos, player);
        }
    }

    @SubscribeEvent
    public static void onChunkUnwatch(ChunkWatchEvent.UnWatch event) {
        ServerPlayer player = event.getPlayer();
        ServerLevel level = event.getLevel();
        ChunkPos chunkPos = event.getPos();

        PhysicsObjectManager manager = PhysicsObjectManagerRegistry.getInstance().getManagerForLevel(level);
        if (manager != null) {
            manager.removeObjectsInChunkFromPlayer(chunkPos, player);
        }
    }
}