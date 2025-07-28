package net.xmx.vortex.physics.object.physicsobject.manager.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.xmx.vortex.event.api.VxChunkEvent;
import net.xmx.vortex.event.api.VxLevelEvent;
import net.xmx.vortex.event.api.VxServerLifecycleEvent;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;
import net.xmx.vortex.physics.object.physicsobject.manager.ObjectLifecycleManager;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import dev.architectury.event.events.common.TickEvent;

import java.util.Optional;

public class ObjectLifecycleEvents {

    public static void registerEvents() {
        VxChunkEvent.Load.EVENT.register(ObjectLifecycleEvents::onChunkLoad);
        VxChunkEvent.Unload.EVENT.register(ObjectLifecycleEvents::onChunkUnload);
        VxChunkEvent.Watch.EVENT.register(ObjectLifecycleEvents::onChunkWatch);
        VxChunkEvent.Unwatch.EVENT.register(ObjectLifecycleEvents::onChunkUnwatch);
        VxLevelEvent.Save.EVENT.register(ObjectLifecycleEvents::onLevelSave);
        TickEvent.SERVER_POST.register(ObjectLifecycleEvents::onServerTick);
    }

    private static void onChunkLoad(VxChunkEvent.Load event) {
        Level chunkLevel = event.getLevel();
        if (chunkLevel instanceof ServerLevel level && !level.isClientSide()) {
            getLifecycleManager(level).ifPresent(manager -> manager.handleChunkLoad(event.getChunkPos()));
        }
    }

    private static void onChunkUnload(VxChunkEvent.Unload event) {
        Level chunkLevel = event.getLevel();
        if (chunkLevel instanceof ServerLevel level && !level.isClientSide()) {
            getLifecycleManager(level).ifPresent(manager -> manager.handleChunkUnload(event.getChunkPos()));
        }
    }

    private static void onChunkWatch(VxChunkEvent.Watch event) {
        getLifecycleManager(event.getLevel()).ifPresent(manager ->
                manager.handlePlayerWatchChunk(event.getPlayer(), event.getChunkPos()));
    }

    private static void onChunkUnwatch(VxChunkEvent.Unwatch event) {
        getLifecycleManager(event.getLevel()).ifPresent(manager ->
                manager.handlePlayerUnwatchChunk(event.getPlayer(), event.getChunkPos()));
    }

    private static void onLevelSave(VxLevelEvent.Save event) {
        ServerLevel level = event.getLevel();
        if (level != null) {
            getLifecycleManager(level).ifPresent(ObjectLifecycleManager::handleLevelSave);
        }
    }

    private static void onServerTick(MinecraftServer event) {

        VxPhysicsWorld.getAll().forEach(world -> {
            if (world.isRunning() && world.getObjectManager() != null && world.getObjectManager().getLifecycleManager() != null) {
                world.getObjectManager().getLifecycleManager().handleServerTick(world);
            }
        });
    }

    private static Optional<ObjectLifecycleManager> getLifecycleManager(Level level) {
        VxPhysicsWorld world = VxPhysicsWorld.get(level.dimension());
        if (world != null && world.isRunning()) {
            VxObjectManager objectManager = world.getObjectManager();
            if (objectManager != null && objectManager.isInitialized()) {
                return Optional.ofNullable(objectManager.getLifecycleManager());
            }
        }
        return Optional.empty();
    }
}