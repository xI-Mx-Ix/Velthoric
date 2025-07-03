package net.xmx.xbullet.physics.object.global.physicsobject.manager.chunk;

import com.github.stephengold.joltjni.RVec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.xmx.xbullet.network.NetworkHandler;
import net.xmx.xbullet.physics.XBulletSavedData;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.ObjectManager;
import net.xmx.xbullet.physics.object.global.physicsobject.packet.RemovePhysicsObjectPacket;
import net.xmx.xbullet.physics.object.global.physicsobject.packet.SpawnPhysicsObjectPacket;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ObjectChunkLoader {

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.isClientSide()) {
            return;
        }

        PhysicsWorld world = PhysicsWorld.get(level.dimension());
        if (world == null || !world.isRunning()) {
            return;
        }

        ChunkPos chunkPos = event.getChunk().getPos();

        loadAndSendObjectsInChunk(level, world, chunkPos, null);
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        ServerPlayer player = event.getPlayer();
        ServerLevel level = event.getLevel();
        ChunkPos chunkPos = event.getPos();

        PhysicsWorld world = PhysicsWorld.get(level.dimension());
        if (world != null && world.isRunning()) {
            loadAndSendObjectsInChunk(level, world, chunkPos, player);
        }
    }

    @SubscribeEvent
    public static void onChunkUnwatch(ChunkWatchEvent.UnWatch event) {
        ServerPlayer player = event.getPlayer();
        ServerLevel level = event.getLevel();
        ChunkPos chunkPos = event.getPos();

        ObjectManager manager = PhysicsWorld.getObjectManager(level.dimension());
        if (manager != null && manager.isInitialized()) {
            for (IPhysicsObject obj : manager.getManagedObjects().values()) {
                RVec3 pos = obj.getCurrentTransform().getTranslation();
                if (pos.xx() >= chunkPos.getMinBlockX() && pos.xx() < chunkPos.getMaxBlockX() &&
                        pos.zz() >= chunkPos.getMinBlockZ() && pos.zz() < chunkPos.getMaxBlockZ()) {
                    NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new RemovePhysicsObjectPacket(obj.getPhysicsId()));
                }
            }
        }
    }

    private static void loadAndSendObjectsInChunk(ServerLevel level, PhysicsWorld world, ChunkPos chunkPos, ServerPlayer specificPlayer) {
        ObjectManager objectManager = world.getObjectManager();
        ConstraintManager constraintManager = world.getConstraintManager();
        XBulletSavedData savedData = objectManager.getDataSystem().getSavedData();

        if (objectManager == null || constraintManager == null || savedData == null) {
            return;
        }

        List<UUID> objectsToLoad = savedData.getAllObjectEntries().stream()
                .filter(entry -> isObjectInChunk(entry.getValue(), chunkPos))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (objectsToLoad.isEmpty()) return;

        List<CompletableFuture<IPhysicsObject>> objectFutures = objectsToLoad.stream()
                .map(id -> objectManager.getDataSystem().getOrLoadObject(id, false))
                .collect(Collectors.toList());

        CompletableFuture<Void> allObjectsLoaded = CompletableFuture.allOf(objectFutures.toArray(new CompletableFuture[0]));

        allObjectsLoaded.thenRunAsync(() -> {
            long timestamp = System.nanoTime();
            ChunkMap chunkMap = level.getChunkSource().chunkMap;

            for (CompletableFuture<IPhysicsObject> future : objectFutures) {
                IPhysicsObject obj = future.getNow(null);
                if (obj == null) continue;

                SpawnPhysicsObjectPacket packet = new SpawnPhysicsObjectPacket(obj, timestamp);

                if (specificPlayer != null) {
                    NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> specificPlayer), packet);
                } else {
                    chunkMap.getPlayers(chunkPos, false).forEach(player -> {
                        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
                    });
                }
            }
        }, level.getServer());
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && !level.isClientSide()) {
            ObjectManager manager = PhysicsWorld.getObjectManager(level.dimension());
            if (manager != null && manager.isInitialized()) {
                manager.getDataSystem().unloadObjectsInChunk(event.getChunk().getPos());
            }
        }
    }

    private static boolean isObjectInChunk(CompoundTag objTag, ChunkPos chunkPos) {
        if (objTag.contains("transform", 10)) {
            CompoundTag transformTag = objTag.getCompound("transform");
            if (transformTag.contains("pos", 9)) {
                ListTag posList = transformTag.getList("pos", Tag.TAG_DOUBLE);
                if (posList.size() == 3) {
                    double x = posList.getDouble(0);
                    double z = posList.getDouble(2);
                    return ((int) Math.floor(x / 16.0)) == chunkPos.x &&
                            ((int) Math.floor(z / 16.0)) == chunkPos.z;
                }
            }
        }
        return false;
    }
}