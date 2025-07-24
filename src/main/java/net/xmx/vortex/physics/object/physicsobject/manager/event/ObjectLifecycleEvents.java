package net.xmx.vortex.physics.object.physicsobject.manager.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;
import net.xmx.vortex.physics.object.physicsobject.manager.ObjectLifecycleManager;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

public class ObjectLifecycleEvents {

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && !level.isClientSide()) {
            getLifecycleManager(level).ifPresent(manager -> manager.handleChunkLoad(event.getChunk().getPos()));
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && !level.isClientSide()) {
            getLifecycleManager(level).ifPresent(manager -> manager.handleChunkUnload(event.getChunk().getPos()));
        }
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        getLifecycleManager(event.getLevel()).ifPresent(manager ->
                manager.handlePlayerWatchChunk(event.getPlayer(), event.getPos()));
    }

    @SubscribeEvent
    public static void onChunkUnwatch(ChunkWatchEvent.UnWatch event) {
        getLifecycleManager(event.getLevel()).ifPresent(manager ->
                manager.handlePlayerUnwatchChunk(event.getPlayer(), event.getPos()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level) {
            getLifecycleManager(level).ifPresent(ObjectLifecycleManager::handleLevelSave);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        VxPhysicsWorld.getAll().forEach(world -> {
            if (world.isRunning() && world.getObjectManager() != null && world.getObjectManager().getLifecycleManager() != null) {
                world.getObjectManager().getLifecycleManager().handleServerTick(world);
            }
        });
    }

    private static java.util.Optional<ObjectLifecycleManager> getLifecycleManager(Level level) {
        VxPhysicsWorld world = VxPhysicsWorld.get(level.dimension());
        if (world != null && world.isRunning()) {
            VxObjectManager objectManager = world.getObjectManager();
            if (objectManager != null && objectManager.isInitialized()) {
                return java.util.Optional.ofNullable(objectManager.getLifecycleManager());
            }
        }
        return java.util.Optional.empty();
    }
}