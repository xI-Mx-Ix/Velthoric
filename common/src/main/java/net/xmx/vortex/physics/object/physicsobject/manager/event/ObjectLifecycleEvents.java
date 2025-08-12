package net.xmx.vortex.physics.object.physicsobject.manager.event;

import com.github.stephengold.joltjni.Vec3;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.xmx.vortex.event.api.VxChunkEvent;
import net.xmx.vortex.event.api.VxLevelEvent;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;
import net.xmx.vortex.physics.object.physicsobject.manager.VxRemovalReason;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

import java.util.Optional;

public class ObjectLifecycleEvents {

    public static void registerEvents() {
        VxChunkEvent.Load.EVENT.register(ObjectLifecycleEvents::onChunkLoad);
        VxChunkEvent.Unload.EVENT.register(ObjectLifecycleEvents::onChunkUnload);
        VxLevelEvent.Save.EVENT.register(ObjectLifecycleEvents::onLevelSave);
        TickEvent.SERVER_POST.register(ObjectLifecycleEvents::onServerTick);
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

            manager.getObjectStorage().saveToFile();
        });
    }

    private static void onServerTick(MinecraftServer server) {
        VxPhysicsWorld.getAll().forEach(world -> {
            VxObjectManager manager = world.getObjectManager();
            if (manager != null) {
                manager.getNetworkDispatcher().tick();
                world.getRidingManager().tick();
                manager.getObjectContainer().getAllObjects().forEach(obj -> obj.gameTick(manager.getWorld().getLevel()));
            }
        });
    }
}