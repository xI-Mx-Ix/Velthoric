package net.xmx.vortex.physics.object.physicsobject.manager;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
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

import java.util.*;

public class VxObjectNetworkDispatcher {

    private final ServerLevel level;
    private final VxObjectManager manager;
    private static final int MAX_PACKET_PAYLOAD_SIZE = 128 * 1024;

    private final Object2ObjectMap<UUID, ObjectSet<UUID>> playerTrackedObjects = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<ServerPlayer, ObjectArrayList<SpawnPhysicsObjectBatchPacket.SpawnData>> pendingSpawns = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<ServerPlayer, ObjectArrayList<UUID>> pendingRemovals = new Object2ObjectOpenHashMap<>();

    private final Long2ObjectMap<ObjectArrayList<PhysicsObjectState>> statesByChunk = new Long2ObjectOpenHashMap<>();
    private final ObjectArrayList<PhysicsObjectState> dispatchChunkBatch = new ObjectArrayList<>();
    private final ObjectArrayList<SpawnPhysicsObjectBatchPacket.SpawnData> spawnBatch = new ObjectArrayList<>();
    private final ObjectArrayList<UUID> removalBatch = new ObjectArrayList<>();

    public VxObjectNetworkDispatcher(ServerLevel level, VxObjectManager manager) {
        this.level = level;
        this.manager = manager;
    }

    public void tick() {
        processPendingRemovals();
        processPendingSpawns();
    }

    public void updatePlayerTracking(ServerPlayer player, Set<IPhysicsObject> visibleObjects) {
        long timestamp = System.nanoTime();
        UUID playerUUID = player.getUUID();
        ObjectSet<UUID> previouslyTracked = this.playerTrackedObjects.computeIfAbsent(playerUUID, k -> new ObjectOpenHashSet<>());
        ObjectSet<UUID> currentlyVisibleIds = new ObjectOpenHashSet<>(visibleObjects.size());
        for (IPhysicsObject obj : visibleObjects) {
            currentlyVisibleIds.add(obj.getPhysicsId());
        }

        ObjectArrayList<UUID> removalsForPlayer = pendingRemovals.computeIfAbsent(player, k -> new ObjectArrayList<>());
        ObjectIterator<UUID> iter = previouslyTracked.iterator();
        while (iter.hasNext()) {
            UUID trackedId = iter.next();
            if (!currentlyVisibleIds.contains(trackedId)) {
                removalsForPlayer.add(trackedId);
                iter.remove();
            }
        }

        ObjectArrayList<SpawnPhysicsObjectBatchPacket.SpawnData> spawnsForPlayer = pendingSpawns.computeIfAbsent(player, k -> new ObjectArrayList<>());
        for (IPhysicsObject obj : visibleObjects) {
            if (previouslyTracked.add(obj.getPhysicsId())) {
                spawnsForPlayer.add(new SpawnPhysicsObjectBatchPacket.SpawnData(obj, timestamp));
            }
        }
    }


    public void onPlayerDisconnect(ServerPlayer player) {
        this.playerTrackedObjects.remove(player.getUUID());
        this.pendingSpawns.remove(player);
        this.pendingRemovals.remove(player);
    }

    private void processPendingRemovals() {
        if (pendingRemovals.isEmpty()) {
            return;
        }

        pendingRemovals.forEach((player, removalList) -> {
            if (removalList.isEmpty()) {
                return;
            }

            for (int i = 0; i < removalList.size(); i += 512) {
                int end = Math.min(i + 512, removalList.size());
                removalBatch.clear();
                removalBatch.addAll(removalList.subList(i, end));
                NetworkHandler.sendToPlayer(new RemovePhysicsObjectBatchPacket(removalBatch), player);
            }
            removalList.clear();
        });
    }

    private void processPendingSpawns() {
        if (pendingSpawns.isEmpty()) {
            return;
        }

        pendingSpawns.forEach((player, spawnDataList) -> {
            if (spawnDataList.isEmpty()) {
                return;
            }

            spawnBatch.clear();
            int currentBatchSizeBytes = 0;

            for (SpawnPhysicsObjectBatchPacket.SpawnData data : spawnDataList) {
                int dataSize = data.estimateSize();
                if (!spawnBatch.isEmpty() && currentBatchSizeBytes + dataSize > MAX_PACKET_PAYLOAD_SIZE) {
                    NetworkHandler.sendToPlayer(new SpawnPhysicsObjectBatchPacket(spawnBatch), player);
                    spawnBatch.clear();
                    currentBatchSizeBytes = 0;
                }
                spawnBatch.add(data);
                currentBatchSizeBytes += dataSize;
            }

            if (!spawnBatch.isEmpty()) {
                NetworkHandler.sendToPlayer(new SpawnPhysicsObjectBatchPacket(spawnBatch), player);
            }
            spawnDataList.clear();
        });
    }

    public void dispatchStateUpdates(List<PhysicsObjectState> states) {
        level.getServer().execute(() -> {
            statesByChunk.values().forEach(ObjectArrayList::clear);

            for (PhysicsObjectState state : states) {
                manager.getObject(state.getId()).ifPresent(obj -> {
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
            ChunkPos chunkPos = VxObjectManager.getObjectChunkPos(obj);
            ServerChunkCache chunkSource = level.getChunkSource();
            for (ServerPlayer player : chunkSource.chunkMap.getPlayers(chunkPos, false)) {
                NetworkHandler.sendToPlayer(packet, player);
            }
            obj.clearDataDirty();
        }
    }

    private void sendUpdatesBatchedByChunk(Long2ObjectMap<ObjectArrayList<PhysicsObjectState>> chunkMap) {
        chunkMap.forEach((chunkKey, statesInChunk) -> {
            if (statesInChunk.isEmpty()) {
                return;
            }
            ChunkPos chunkPos = new ChunkPos(chunkKey);

            dispatchChunkBatch.clear();
            int currentBatchSizeBytes = 0;

            for (PhysicsObjectState state : statesInChunk) {
                int stateSize = state.estimateEncodedSize();
                if (!dispatchChunkBatch.isEmpty() && currentBatchSizeBytes + stateSize > MAX_PACKET_PAYLOAD_SIZE) {
                    sendUpdateBatch(chunkPos, dispatchChunkBatch);
                    dispatchChunkBatch.clear();
                    currentBatchSizeBytes = 0;
                }
                dispatchChunkBatch.add(state);
                currentBatchSizeBytes += stateSize;
            }

            if (!dispatchChunkBatch.isEmpty()) {
                sendUpdateBatch(chunkPos, dispatchChunkBatch);
            }
        });
    }

    private void sendUpdateBatch(ChunkPos chunkPos, List<PhysicsObjectState> batch) {
        SyncAllPhysicsObjectsPacket packet = new SyncAllPhysicsObjectsPacket((ObjectArrayList<PhysicsObjectState>) batch);
        ServerChunkCache chunkSource = level.getChunkSource();
        for (ServerPlayer player : chunkSource.chunkMap.getPlayers(chunkPos, false)) {
            NetworkHandler.sendToPlayer(packet, player);
        }
    }
}