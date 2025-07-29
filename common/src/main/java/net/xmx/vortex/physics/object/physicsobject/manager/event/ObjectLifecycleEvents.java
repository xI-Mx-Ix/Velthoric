package net.xmx.vortex.physics.object.physicsobject.manager.event;

import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.xmx.vortex.event.api.VxChunkEvent;
import net.xmx.vortex.event.api.VxLevelEvent;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;
import net.xmx.vortex.physics.object.physicsobject.manager.VxRemovalReason;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

import java.util.Optional;

public class ObjectLifecycleEvents {

    public static void registerEvents() {
        VxChunkEvent.Load.EVENT.register(ObjectLifecycleEvents::onChunkLoad);
        VxChunkEvent.Unload.EVENT.register(ObjectLifecycleEvents::onChunkUnload);
        VxChunkEvent.Watch.EVENT.register(ObjectLifecycleEvents::onChunkWatch);
        VxLevelEvent.Save.EVENT.register(ObjectLifecycleEvents::onLevelSave);
        TickEvent.SERVER_POST.register(ObjectLifecycleEvents::onServerTick);
    }

    private static Optional<VxObjectManager> getObjectManager(Level level) {
        if (level.isClientSide()) {
            return Optional.empty();
        }
        VxPhysicsWorld world = VxPhysicsWorld.get(level.dimension());
        if (world != null && world.getObjectManager() != null) {
            return Optional.of(world.getObjectManager());
        }
        return Optional.empty();
    }

    private static void onChunkLoad(VxChunkEvent.Load event) {
        getObjectManager(event.getLevel()).ifPresent(manager ->
                manager.getObjectStorage().loadObjectsInChunk(event.getChunkPos())
        );
    }

    private static void onChunkUnload(VxChunkEvent.Unload event) {
        getObjectManager(event.getLevel()).ifPresent(manager -> {
            manager.getObjectContainer().getAllObjects().forEach(obj -> {
                if (VxObjectManager.getObjectChunkPos(obj).equals(event.getChunkPos())) {
                    manager.removeObject(obj.getPhysicsId(), VxRemovalReason.SAVE);
                }
            });
        });
    }

    private static void onChunkWatch(VxChunkEvent.Watch event) {
        getObjectManager(event.getLevel()).ifPresent(manager -> {
            ServerPlayer player = event.getPlayer();
            ChunkPos chunkPos = event.getChunkPos();
            manager.getNetworkDispatcher().sendExistingObjectsToPlayer(player, chunkPos, manager.getObjectContainer().getAllObjects());
        });
    }

    private static void onLevelSave(VxLevelEvent.Save event) {

        getObjectManager(event.getLevel()).ifPresent(manager ->
                manager.getObjectStorage().saveAll(manager.getObjectContainer().getAllObjects())
        );
    }

    private static void onServerTick(MinecraftServer server) {

        VxPhysicsWorld.getAll().forEach(world -> {
            VxObjectManager manager = world.getObjectManager();
            if (manager != null) {
                manager.getObjectContainer().getAllObjects().forEach(obj -> {
                    if (!obj.isRemoved()) {
                        obj.fixedGameTick(manager.getWorld().getLevel());
                        obj.gameTick(manager.getWorld().getLevel());
                        manager.getNetworkDispatcher().dispatchDataUpdate(obj);
                    }
                });
            }
        });
    }
}