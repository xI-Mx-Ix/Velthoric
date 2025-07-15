package net.xmx.xbullet.physics.object.physicsobject.manager;

import com.github.stephengold.joltjni.RVec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.PacketDistributor;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.network.NetworkHandler;
import net.xmx.xbullet.physics.object.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.physicsobject.packet.RemovePhysicsObjectPacket;
import net.xmx.xbullet.physics.object.physicsobject.packet.SpawnPhysicsObjectPacket;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import java.util.Collection;

public class ObjectLifecycleManager {

    private final ObjectManager objectManager;
    private final ObjectDataSystem dataSystem;

    public ObjectLifecycleManager(ObjectManager objectManager) {
        this.objectManager = objectManager;
        this.dataSystem = objectManager.getDataSystem();
    }

    public void handleChunkLoad(ChunkPos chunkPos) {
        dataSystem.loadObjectsInChunk(chunkPos);
    }

    public void handleChunkUnload(ChunkPos chunkPos) {
        dataSystem.unloadObjectsInChunk(chunkPos);
    }

    public void handlePlayerWatchChunk(ServerPlayer player, ChunkPos chunkPos) {
        Collection<IPhysicsObject> allObjects = objectManager.getManagedObjects().values();
        long timestamp = System.nanoTime();
        for (IPhysicsObject obj : allObjects) {
            RVec3 pos = obj.getCurrentTransform().getTranslation();
            if (pos.xx() >= chunkPos.getMinBlockX() && pos.xx() < chunkPos.getMaxBlockX() &&
                pos.zz() >= chunkPos.getMinBlockZ() && pos.zz() < chunkPos.getMaxBlockZ()) {
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SpawnPhysicsObjectPacket(obj, timestamp));
            }
        }
    }

    public void handlePlayerUnwatchChunk(ServerPlayer player, ChunkPos chunkPos) {
        for (IPhysicsObject obj : objectManager.getManagedObjects().values()) {
            RVec3 pos = obj.getCurrentTransform().getTranslation();
            if (pos.xx() >= chunkPos.getMinBlockX() && pos.xx() < chunkPos.getMaxBlockX() &&
                pos.zz() >= chunkPos.getMinBlockZ() && pos.zz() < chunkPos.getMaxBlockZ()) {
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new RemovePhysicsObjectPacket(obj.getPhysicsId()));
            }
        }
    }

    public void handleLevelSave() {
        dataSystem.saveAll(objectManager.getManagedObjects().values());
    }

    public void handleServerTick(PhysicsWorld world) {
        ServerLevel level = world.getLevel();
        try {

            objectManager.getManagedObjects().values().forEach(obj -> {
                if (!obj.isRemoved()) {
                    obj.fixedGameTick(level);
                    obj.gameTick(level);
                }
            });
        } catch (Exception e) {
            XBullet.LOGGER.error("Error during object gameTick for world {}", world.getDimensionKey().location(), e);
        }
    }
}