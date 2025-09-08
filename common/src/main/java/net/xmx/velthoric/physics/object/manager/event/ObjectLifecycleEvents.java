package net.xmx.velthoric.physics.object.manager.event;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.xmx.velthoric.event.api.VxChunkEvent;
import net.xmx.velthoric.event.api.VxLevelEvent;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.object.manager.VxRemovalReason;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.Optional;

public class ObjectLifecycleEvents {

    public static void registerEvents() {
        VxChunkEvent.Load.EVENT.register(ObjectLifecycleEvents::onChunkLoad);
        VxChunkEvent.Unload.EVENT.register(ObjectLifecycleEvents::onChunkUnload);
        VxLevelEvent.Save.EVENT.register(ObjectLifecycleEvents::onLevelSave);
        PlayerEvent.PLAYER_QUIT.register(ObjectLifecycleEvents::onPlayerQuit);
    }

    private static void onPlayerQuit(ServerPlayer player) {
        getObjectManager(player.level()).ifPresent(manager ->
                manager.getNetworkDispatcher().onPlayerDisconnect(player)
        );
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
                VxTransform gameTransform = obj.getGameTransform();
                var translation = gameTransform.getTranslation();
                ChunkPos currentChunkPos = new ChunkPos(
                        SectionPos.posToSectionCoord(translation.xx()),
                        SectionPos.posToSectionCoord(translation.zz())
                );
                if (currentChunkPos.equals(event.getChunkPos())) {
                    manager.removeObject(obj.getPhysicsId(), VxRemovalReason.SAVE);
                }
            });
        });
    }

    private static void onLevelSave(VxLevelEvent.Save event) {
        getObjectManager(event.getLevel()).ifPresent(manager -> {
            manager.getObjectContainer().getAllObjects()
                    .forEach(manager.getObjectStorage()::storeObject);
            manager.getObjectStorage().saveDirtyRegions();
        });
    }
}