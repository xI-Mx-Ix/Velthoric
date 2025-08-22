package net.xmx.velthoric.physics.object.physicsobject.manager;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.NetworkHandler;
import net.xmx.velthoric.physics.object.physicsobject.VxAbstractBody;
import net.xmx.velthoric.physics.object.physicsobject.packet.batch.RemovePhysicsObjectBatchPacket;
import net.xmx.velthoric.physics.object.physicsobject.packet.batch.SpawnPhysicsObjectBatchPacket;
import net.xmx.velthoric.physics.object.physicsobject.packet.batch.SyncAllPhysicsObjectsPacket;
import net.xmx.velthoric.physics.object.physicsobject.packet.SyncPhysicsObjectDataPacket;
import net.xmx.velthoric.physics.object.physicsobject.state.PhysicsObjectState;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

public class VxObjectNetworkDispatcher {

    private final ServerLevel level;
    private final VxObjectManager manager;
    private static final int MAX_PACKET_PAYLOAD_SIZE = 128 * 1024;
    private static final int NETWORK_THREAD_TICK_RATE_MS = 10;

    private final Object2ObjectMap<UUID, ObjectSet<UUID>> playerTrackedObjects = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<ServerPlayer, ObjectArrayList<SpawnPhysicsObjectBatchPacket.SpawnData>> pendingSpawns = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<ServerPlayer, ObjectArrayList<UUID>> pendingRemovals = new Object2ObjectOpenHashMap<>();
    private final ObjectArrayList<SpawnPhysicsObjectBatchPacket.SpawnData> spawnBatch = new ObjectArrayList<>();
    private final ObjectArrayList<UUID> removalBatch = new ObjectArrayList<>();

    private final ConcurrentLinkedQueue<PhysicsObjectState> stateUpdateQueue = new ConcurrentLinkedQueue<>();
    private ExecutorService networkSyncExecutor;

    public VxObjectNetworkDispatcher(ServerLevel level, VxObjectManager manager) {
        this.level = level;
        this.manager = manager;
    }

    public void start() {
        this.networkSyncExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Velthoric Network Sync Thread"));
        this.networkSyncExecutor.submit(this::runSyncLoop);
    }

    public void stop() {
        if (this.networkSyncExecutor != null) {
            this.networkSyncExecutor.shutdownNow();
        }
    }

    public void tick() {
        processPendingRemovals();
        processPendingSpawns();
    }

    private void runSyncLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                long cycleStartTime = System.nanoTime();
                processStateUpdates();
                long cycleEndTime = System.nanoTime();
                long cycleDurationMs = (cycleEndTime - cycleStartTime) / 1_000_000;
                long sleepTime = Math.max(0, NETWORK_THREAD_TICK_RATE_MS - cycleDurationMs);
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Exception in Velthoric Network Sync Thread", e);
            }
        }
    }

    public void queueStateUpdates(List<PhysicsObjectState> states) {
        this.stateUpdateQueue.addAll(states);
    }

    public void updatePlayerTracking(ServerPlayer player, Set<VxAbstractBody> visibleObjects) {
        long timestamp = System.nanoTime();
        UUID playerUUID = player.getUUID();
        ObjectSet<UUID> previouslyTracked = this.playerTrackedObjects.computeIfAbsent(playerUUID, k -> new ObjectOpenHashSet<>());
        ObjectSet<UUID> currentlyVisibleIds = new ObjectOpenHashSet<>(visibleObjects.size());
        for (VxAbstractBody obj : visibleObjects) {
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
        for (VxAbstractBody obj : visibleObjects) {
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

    private void processStateUpdates() {
        if (stateUpdateQueue.isEmpty()) {
            return;
        }

        ObjectArrayList<PhysicsObjectState> statesToProcess = new ObjectArrayList<>();
        PhysicsObjectState state;
        while ((state = stateUpdateQueue.poll()) != null) {
            statesToProcess.add(state);
        }

        if (statesToProcess.isEmpty()) {
            return;
        }

        Long2ObjectMap<ObjectArrayList<PhysicsObjectState>> statesByChunk = new Long2ObjectOpenHashMap<>();
        for (PhysicsObjectState s : statesToProcess) {
            var transform = s.getTransform();
            long chunkKey = ChunkPos.asLong(SectionPos.posToSectionCoord(transform.getTranslation().x()), SectionPos.posToSectionCoord(transform.getTranslation().z()));
            statesByChunk.computeIfAbsent(chunkKey, k -> new ObjectArrayList<>()).add(s);
        }
        statesToProcess.clear();

        statesByChunk.forEach((chunkKey, statesInChunk) -> {
            if (statesInChunk.isEmpty()) {
                return;
            }
            ChunkPos chunkPos = new ChunkPos(chunkKey);
            ObjectArrayList<PhysicsObjectState> dispatchChunkBatch = new ObjectArrayList<>();
            int currentBatchSizeBytes = 0;

            for (PhysicsObjectState stateInChunk : statesInChunk) {
                int stateSize = stateInChunk.estimateEncodedSize();
                if (!dispatchChunkBatch.isEmpty() && currentBatchSizeBytes + stateSize > MAX_PACKET_PAYLOAD_SIZE) {
                    sendUpdateBatch(chunkPos, dispatchChunkBatch);
                    dispatchChunkBatch.clear();
                    currentBatchSizeBytes = 0;
                }
                dispatchChunkBatch.add(stateInChunk);
                currentBatchSizeBytes += stateSize;
            }
            if (!dispatchChunkBatch.isEmpty()) {
                sendUpdateBatch(chunkPos, dispatchChunkBatch);
            }
        });
    }

    private void sendUpdateBatch(ChunkPos chunkPos, ObjectArrayList<PhysicsObjectState> batch) {
        final SyncAllPhysicsObjectsPacket packet = new SyncAllPhysicsObjectsPacket(batch);
        level.getServer().execute(() -> {
            ServerChunkCache chunkSource = level.getChunkSource();
            for (ServerPlayer player : chunkSource.chunkMap.getPlayers(chunkPos, false)) {
                NetworkHandler.sendToPlayer(packet, player);
            }
        });
    }

    public void dispatchDataUpdate(VxAbstractBody obj) {
        if (obj.isDataDirty()) {
            SyncPhysicsObjectDataPacket packet = new SyncPhysicsObjectDataPacket(obj);
            var transform = obj.getGameTransform();
            ChunkPos chunkPos = new ChunkPos(SectionPos.posToSectionCoord(transform.getTranslation().x()), SectionPos.posToSectionCoord(transform.getTranslation().z()));
            level.getServer().execute(() -> {
                ServerChunkCache chunkSource = level.getChunkSource();
                for (ServerPlayer player : chunkSource.chunkMap.getPlayers(chunkPos, false)) {
                    NetworkHandler.sendToPlayer(packet, player);
                }
            });
            obj.clearDataDirty();
        }
    }
}