package net.xmx.xbullet.physics.object.global.physicsobject.manager;

import com.github.stephengold.joltjni.RVec3;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.PacketDistributor;
import net.xmx.xbullet.network.NetworkHandler;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.packet.RemovePhysicsObjectPacket;
import net.xmx.xbullet.physics.object.global.physicsobject.packet.SpawnPhysicsObjectPacket;

public class PhysicsObjectClientSynchronizer {

    public static void sendObjectsInChunkToPlayer(PhysicsObjectManager manager, ChunkPos chunkPos, ServerPlayer player) {
        if (!manager.isInitialized()) {
            return;
        }
        long timestamp = System.nanoTime();

        for (IPhysicsObject obj : manager.managedObjects.values()) {
            RVec3 pos = obj.getCurrentTransform().getTranslation();
            int cx = (int) Math.floor(pos.xx() / 16.0);
            int cz = (int) Math.floor(pos.zz() / 16.0);

            if (cx == chunkPos.x && cz == chunkPos.z) {
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new SpawnPhysicsObjectPacket(obj, timestamp)
                );
            }
        }
    }

    public static void removeObjectsInChunkFromPlayer(PhysicsObjectManager manager, ChunkPos chunkPos, ServerPlayer player) {
        if (!manager.isInitialized()) {
            return;
        }

        for (IPhysicsObject obj : manager.managedObjects.values()) {
            RVec3 pos = obj.getCurrentTransform().getTranslation();
            int cx = (int) Math.floor(pos.xx() / 16.0);
            int cz = (int) Math.floor(pos.zz() / 16.0);

            if (cx == chunkPos.x && cz == chunkPos.z) {
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new RemovePhysicsObjectPacket(obj.getPhysicsId())
                );
            }
        }
    }
}