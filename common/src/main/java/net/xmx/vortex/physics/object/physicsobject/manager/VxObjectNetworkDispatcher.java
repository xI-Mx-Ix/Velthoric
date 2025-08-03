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

import java.util.*;

public class VxObjectNetworkDispatcher {

    private final ServerLevel level;
    private final VxObjectManager manager;
    private static final int MAX_PACKET_PAYLOAD_SIZE = 128 * 1024;

    private final Map<UUID, Set<UUID>> playerTrackedObjects = new HashMap<>();
    private final Map<ServerPlayer, ObjectArrayList<SpawnPhysicsObjectBatchPacket.SpawnData>> pendingSpawns = new HashMap<>();
    private final Map<ServerPlayer, ObjectArrayList<UUID>> pendingRemovals = new HashMap<>();

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
        Set<UUID> previouslyTracked = this.playerTrackedObjects.computeIfAbsent(playerUUID, k -> new HashSet<>());
        Set<UUID> newlyVisibleIds = new HashSet<>(visibleObjects.size());

        for (IPhysicsObject obj : visibleObjects) {
            UUID objId = obj.getPhysicsId();
            newlyVisibleIds.add(objId);
            if (!previouslyTracked.contains(objId)) {
                pendingSpawns.computeIfAbsent(player, k -> new ObjectArrayList<>())
                        .add(new SpawnPhysicsObjectBatchPacket.SpawnData(obj, timestamp));
            }
        }

        if (previouslyTracked.size() > newlyVisibleIds.size()) {
            previouslyTracked.removeIf(trackedId -> {
                if (!newlyVisibleIds.contains(trackedId)) {
                    pendingRemovals.computeIfAbsent(player, k -> new ObjectArrayList<>()).add(trackedId);
                    return true;
                }
                return false;
            });
        } else {
            this.playerTrackedObjects.put(playerUUID, newlyVisibleIds);
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

        pendingRemovals.forEach((player, ids) -> {
            if (!ids.isEmpty()) {
                NetworkHandler.sendToPlayer(new RemovePhysicsObjectBatchPacket(new ObjectArrayList<>(ids)), player);
            }
        });
        pendingRemovals.clear();
    }

    private void processPendingSpawns() {
        if (pendingSpawns.isEmpty()) {
            return;
        }

        pendingSpawns.forEach((player, spawnDataList) -> {
            if (spawnDataList.isEmpty()) {
                return;
            }

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
        });
        pendingSpawns.clear();
    }

    public void dispatchStateUpdates(List<PhysicsObjectState> states) {
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
            getPlayersInChunk(VxObjectManager.getObjectChunkPos(obj)).forEach(player -> NetworkHandler.sendToPlayer(packet, player));
            obj.clearDataDirty();
        }
    }

    private void sendUpdatesBatchedByChunk(Long2ObjectMap<List<PhysicsObjectState>> statesByChunk) {
        statesByChunk.forEach((chunkKey, statesInChunk) -> {
            if (statesInChunk.isEmpty()) {
                return;
            }
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
        getPlayersInChunk(chunkPos).forEach(player -> NetworkHandler.sendToPlayer(packet, player));
    }

    private Iterable<ServerPlayer> getPlayersInChunk(ChunkPos pos) {
        ServerChunkCache chunkSource = level.getChunkSource();
        return chunkSource.chunkMap.getPlayers(pos, false);
    }
}