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
import net.xmx.vortex.physics.object.physicsobject.packet.batch.RemovePhysicsObjectBatchPacket;
import net.xmx.vortex.physics.object.physicsobject.packet.batch.SpawnPhysicsObjectBatchPacket;
import net.xmx.vortex.physics.object.physicsobject.packet.batch.SyncAllPhysicsObjectsPacket;
import net.xmx.vortex.physics.object.physicsobject.packet.SyncPhysicsObjectDataPacket;
import net.xmx.vortex.physics.object.physicsobject.state.PhysicsObjectState;
import net.xmx.vortex.physics.object.physicsobject.state.PhysicsObjectStatePool;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class VxObjectNetworkDispatcher {

    private final ServerLevel level;
    private final VxObjectManager manager;
    private static final int MAX_PACKET_PAYLOAD_SIZE = 128 * 1024;

    private final Queue<IPhysicsObject> spawnQueue = new ConcurrentLinkedQueue<>();
    private final Queue<UUID> removalQueue = new ConcurrentLinkedQueue<>();

    public VxObjectNetworkDispatcher(ServerLevel level, VxObjectManager manager) {
        this.level = level;
        this.manager = manager;
    }

    public void tick() {
        processRemovals();
        processSpawns();
    }

    public void queueSpawn(IPhysicsObject obj) {
        this.spawnQueue.offer(obj);
    }

    public void queueRemoval(UUID id) {
        this.removalQueue.offer(id);
    }

    private void processRemovals() {
        if (removalQueue.isEmpty()) return;

        ObjectArrayList<UUID> removedIds = new ObjectArrayList<>();
        UUID id;
        while ((id = removalQueue.poll()) != null) {
            removedIds.add(id);
        }

        if (!removedIds.isEmpty()) {
            NetworkHandler.sendToDimension(new RemovePhysicsObjectBatchPacket(removedIds), level.dimension());
        }
    }

    private void processSpawns() {
        if (spawnQueue.isEmpty()) return;

        Long2ObjectMap<ObjectArrayList<SpawnPhysicsObjectBatchPacket.SpawnData>> spawnsByChunk = new Long2ObjectOpenHashMap<>();
        long timestamp = System.nanoTime();
        IPhysicsObject obj;

        while ((obj = spawnQueue.poll()) != null) {
            long chunkKey = VxObjectManager.getObjectChunkPos(obj).toLong();
            spawnsByChunk.computeIfAbsent(chunkKey, k -> new ObjectArrayList<>()).add(new SpawnPhysicsObjectBatchPacket.SpawnData(obj, timestamp));
        }

        if (spawnsByChunk.isEmpty()) return;

        spawnsByChunk.forEach((chunkKey, spawnDataList) -> {
            if (spawnDataList.isEmpty()) return;

            ChunkPos chunkPos = new ChunkPos(chunkKey);
            ObjectArrayList<SpawnPhysicsObjectBatchPacket.SpawnData> currentBatch = new ObjectArrayList<>();
            int currentBatchSizeBytes = 0;

            for (SpawnPhysicsObjectBatchPacket.SpawnData data : spawnDataList) {
                int dataSize = data.estimateSize();
                if (!currentBatch.isEmpty() && currentBatchSizeBytes + dataSize > MAX_PACKET_PAYLOAD_SIZE) {
                    sendSpawnBatch(chunkPos, currentBatch);
                    currentBatch = new ObjectArrayList<>();
                    currentBatchSizeBytes = 0;
                }
                currentBatch.add(data);
                currentBatchSizeBytes += dataSize;
            }

            if (!currentBatch.isEmpty()) {
                sendSpawnBatch(chunkPos, currentBatch);
            }
        });
    }

    private void sendSpawnBatch(ChunkPos chunkPos, List<SpawnPhysicsObjectBatchPacket.SpawnData> batch) {
        SpawnPhysicsObjectBatchPacket packet = new SpawnPhysicsObjectBatchPacket(new ObjectArrayList<>(batch));
        getPlayersInChunk(chunkPos).forEach(player -> NetworkHandler.sendToPlayer(packet, player));
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
        ObjectArrayList<SpawnPhysicsObjectBatchPacket.SpawnData> spawnDataList = new ObjectArrayList<>();

        for (IPhysicsObject obj : objects) {
            if (VxObjectManager.getObjectChunkPos(obj).equals(chunkPos)) {
                spawnDataList.add(new SpawnPhysicsObjectBatchPacket.SpawnData(obj, timestamp));
            }
        }

        if (spawnDataList.isEmpty()) return;

        ObjectArrayList<SpawnPhysicsObjectBatchPacket.SpawnData> currentBatch = new ObjectArrayList<>();
        int currentBatchSizeBytes = 0;

        for (SpawnPhysicsObjectBatchPacket.SpawnData data : spawnDataList) {
            int dataSize = data.estimateSize();
            if (!currentBatch.isEmpty() && currentBatchSizeBytes + dataSize > MAX_PACKET_PAYLOAD_SIZE) {
                NetworkHandler.sendToPlayer(new SpawnPhysicsObjectBatchPacket(currentBatch), player);
                currentBatch = new ObjectArrayList<>();
                currentBatchSizeBytes = 0;
            }
            currentBatch.add(data);
            currentBatchSizeBytes += dataSize;
        }

        if (!currentBatch.isEmpty()) {
            NetworkHandler.sendToPlayer(new SpawnPhysicsObjectBatchPacket(currentBatch), player);
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
                    sendUpdateBatch(chunkPos, currentBatch);
                    currentBatch = new ObjectArrayList<>();
                    currentBatchSizeBytes = 0;
                }
                currentBatch.add(state);
                currentBatchSizeBytes += stateSize;
            }

            if (!currentBatch.isEmpty()) {
                sendUpdateBatch(chunkPos, currentBatch);
            }
        });
    }

    private void sendUpdateBatch(ChunkPos chunkPos, List<PhysicsObjectState> batch) {
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