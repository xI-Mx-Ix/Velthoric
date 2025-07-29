package net.xmx.vortex.physics.object.physicsobject.manager;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.xmx.vortex.network.NetworkHandler;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.packet.RemovePhysicsObjectPacket;
import net.xmx.vortex.physics.object.physicsobject.packet.SpawnPhysicsObjectPacket;
import net.xmx.vortex.physics.object.physicsobject.packet.SyncAllPhysicsObjectsPacket;
import net.xmx.vortex.physics.object.physicsobject.packet.SyncPhysicsObjectDataPacket;
import net.xmx.vortex.physics.object.physicsobject.state.PhysicsObjectState;
import net.xmx.vortex.physics.object.physicsobject.state.PhysicsObjectStatePool;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class VxObjectNetworkDispatcher {

    private final ServerLevel level;
    private final VxObjectManager manager;
    private static final int MAX_PACKET_PAYLOAD_SIZE = 128 * 1024;

    public VxObjectNetworkDispatcher(ServerLevel level, VxObjectManager manager) {
        this.level = level;
        this.manager = manager;
    }

    public void dispatchSpawn(IPhysicsObject obj) {
        ChunkPos chunkPos = VxObjectManager.getObjectChunkPos(obj);
        getPlayersInChunk(chunkPos).forEach(player -> NetworkHandler.sendToPlayer(new SpawnPhysicsObjectPacket(obj, System.nanoTime()), player));
    }

    public void dispatchRemoval(UUID id) {
        NetworkHandler.sendToDimension(new RemovePhysicsObjectPacket(id), level.dimension());
    }

    public void dispatchStateUpdates(List<PhysicsObjectState> states) {
        if (level.getServer() == null) {
            states.forEach(PhysicsObjectStatePool::release);
            return;
        }
        level.getServer().execute(() -> {
            Long2ObjectMap<List<PhysicsObjectState>> statesByChunk = new Long2ObjectOpenHashMap<>();
            for (PhysicsObjectState state : states) {
                this.manager.getObject(state.getId()).ifPresent(obj -> {
                    long chunkKey = VxObjectManager.getObjectChunkPos(obj).toLong();
                    statesByChunk.computeIfAbsent(chunkKey, k -> new ObjectArrayList<>()).add(state);
                });
            }
            sendUpdatesBatchedByChunk(statesByChunk);
        });
    }

    public void dispatchDataUpdate(IPhysicsObject obj) {
        if (obj.isDataDirty()) {
            SyncPhysicsObjectDataPacket packet = new SyncPhysicsObjectDataPacket(obj);
            getPlayersInChunk(VxObjectManager.getObjectChunkPos(obj)).forEach(player -> {
                NetworkHandler.sendToPlayer(packet, player);
            });
            obj.clearDataDirty();
        }
    }

    public void sendExistingObjectsToPlayer(ServerPlayer player, ChunkPos chunkPos, Collection<IPhysicsObject> objects) {
        long timestamp = System.nanoTime();
        for (IPhysicsObject obj : objects) {
            if (VxObjectManager.getObjectChunkPos(obj).equals(chunkPos)) {
                NetworkHandler.sendToPlayer(new SpawnPhysicsObjectPacket(obj, timestamp), player);
            }
        }
    }

    private void sendUpdatesBatchedByChunk(Long2ObjectMap<List<PhysicsObjectState>> statesByChunk) {
        statesByChunk.forEach((chunkKey, statesInChunk) -> {
            if (statesInChunk.isEmpty()) return;
            ChunkPos chunkPos = new ChunkPos(chunkKey);
            ObjectArrayList<PhysicsObjectState> currentBatch = new ObjectArrayList<>();
            int currentBatchSizeBytes = 0;

            for (PhysicsObjectState state : statesInChunk) {
                int stateSize = state.estimateEncodedSize();
                if (!currentBatch.isEmpty() && currentBatchSizeBytes + stateSize > MAX_PACKET_PAYLOAD_SIZE) {
                    sendBatch(chunkPos, currentBatch);
                    currentBatch = new ObjectArrayList<>();
                    currentBatchSizeBytes = 0;
                }
                currentBatch.add(state);
                currentBatchSizeBytes += stateSize;
            }

            if (!currentBatch.isEmpty()) {
                sendBatch(chunkPos, currentBatch);
            }
        });
    }

    private void sendBatch(ChunkPos chunkPos, List<PhysicsObjectState> batch) {
        SyncAllPhysicsObjectsPacket packet = new SyncAllPhysicsObjectsPacket(new ObjectArrayList<>(batch));
        getPlayersInChunk(chunkPos).forEach(player -> {
            NetworkHandler.sendToPlayer(packet, player);
        });
    }

    private Iterable<ServerPlayer> getPlayersInChunk(ChunkPos pos) {
        ServerChunkCache chunkSource = level.getChunkSource();
        return (chunkSource != null && chunkSource.chunkMap != null) ? chunkSource.chunkMap.getPlayers(pos, false) : List.of();
    }
}