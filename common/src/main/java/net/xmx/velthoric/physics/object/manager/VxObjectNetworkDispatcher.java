package net.xmx.velthoric.physics.object.manager;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.NetworkHandler;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.object.packet.SpawnData;
import net.xmx.velthoric.physics.object.packet.batch.RemovePhysicsObjectBatchPacket;
import net.xmx.velthoric.physics.object.packet.batch.SpawnPhysicsObjectBatchPacket;
import net.xmx.velthoric.physics.object.packet.batch.SyncAllPhysicsObjectsPacket;
import net.xmx.velthoric.physics.object.packet.SyncPhysicsObjectDataPacket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VxObjectNetworkDispatcher {

    private final ServerLevel level;
    private final VxObjectManager manager;
    private final VxObjectDataStore dataStore;
    private final ConcurrentLinkedQueue<Integer> dirtyIndicesQueue;
    private static final int MAX_PACKET_PAYLOAD_SIZE = 128 * 1024;
    private static final int NETWORK_THREAD_TICK_RATE_MS = 10;
    private static final int MAX_UPDATES_PER_PACKET = 256;

    private final Map<UUID, Set<UUID>> playerTrackedObjects = new ConcurrentHashMap<>();
    private final Map<UUID, ChunkPos> playerChunkPositions = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerViewDistances = new ConcurrentHashMap<>();
    private final Object2ObjectOpenHashMap<ServerPlayer, ObjectArrayList<SpawnData>> pendingSpawns = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<ServerPlayer, ObjectArrayList<UUID>> pendingRemovals = new Object2ObjectOpenHashMap<>();

    private ExecutorService networkSyncExecutor;

    public VxObjectNetworkDispatcher(ServerLevel level, VxObjectManager manager, ConcurrentLinkedQueue<Integer> dirtyIndicesQueue) {
        this.level = level;
        this.manager = manager;
        this.dataStore = manager.getDataStore();
        this.dirtyIndicesQueue = dirtyIndicesQueue;
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

    public void onGameTick() {
        processPendingRemovals();
        processPendingSpawns();
    }

    private void runSyncLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                long cycleStartTime = System.nanoTime();
                sendStateUpdates();
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

    public void onObjectAdded(VxAbstractBody body) {
        ChunkPos bodyChunk = VxObjectManager.getObjectChunkPos(body);
        for (ServerPlayer player : level.players()) {
            if (isChunkVisible(player, bodyChunk)) {
                startTracking(player, body);
            }
        }
    }

    public void onObjectRemoved(VxAbstractBody body) {
        for (ServerPlayer player : level.players()) {
            stopTracking(player, body.getPhysicsId());
        }
    }

    public void onObjectMoved(VxAbstractBody body, ChunkPos from, ChunkPos to) {
        for (ServerPlayer player : level.players()) {
            boolean wasVisible = isChunkVisible(player, from);
            boolean isVisible = isChunkVisible(player, to);
            if (wasVisible && !isVisible) {
                stopTracking(player, body.getPhysicsId());
            } else if (!wasVisible && isVisible) {
                startTracking(player, body);
            }
        }
    }

    public void updatePlayerTracking(ServerPlayer player) {
        playerChunkPositions.put(player.getUUID(), player.chunkPosition());
        playerViewDistances.put(player.getUUID(), player.server.getPlayerList().getViewDistance());

        Set<UUID> previouslyTracked = playerTrackedObjects.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
        Set<UUID> newlyVisible = new HashSet<>();

        int viewDistance = playerViewDistances.getOrDefault(player.getUUID(), 0);
        ChunkPos playerChunkPos = playerChunkPositions.getOrDefault(player.getUUID(), new ChunkPos(0, 0));

        for (int cz = playerChunkPos.z - viewDistance; cz <= playerChunkPos.z + viewDistance; ++cz) {
            for (int cx = playerChunkPos.x - viewDistance; cx <= playerChunkPos.x + viewDistance; ++cx) {
                for (VxAbstractBody body : manager.getObjectsInChunk(new ChunkPos(cx, cz))) {
                    newlyVisible.add(body.getPhysicsId());
                }
            }
        }

        for (UUID trackedId : new ArrayList<>(previouslyTracked)) {
            if (!newlyVisible.contains(trackedId)) {
                stopTracking(player, trackedId);
            }
        }

        for (UUID visibleId : newlyVisible) {
            if (!previouslyTracked.contains(visibleId)) {
                manager.getObject(visibleId).ifPresent(body -> startTracking(player, body));
            }
        }
    }

    private void startTracking(ServerPlayer player, VxAbstractBody body) {
        Set<UUID> tracked = playerTrackedObjects.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
        if (tracked.add(body.getPhysicsId())) {
            synchronized (pendingSpawns) {
                pendingSpawns.computeIfAbsent(player, k -> new ObjectArrayList<>())
                        .add(new SpawnData(body, System.nanoTime()));
            }
        }
    }

    private void stopTracking(ServerPlayer player, UUID bodyId) {
        Set<UUID> tracked = playerTrackedObjects.get(player.getUUID());
        if (tracked != null && tracked.remove(bodyId)) {
            synchronized (pendingRemovals) {
                pendingRemovals.computeIfAbsent(player, k -> new ObjectArrayList<>())
                        .add(bodyId);
            }
        }
    }

    private boolean isChunkVisible(ServerPlayer player, ChunkPos chunkPos) {
        Integer viewDistance = playerViewDistances.get(player.getUUID());
        ChunkPos playerChunkPos = playerChunkPositions.get(player.getUUID());
        if (viewDistance == null || playerChunkPos == null) {
            return false;
        }
        return Math.abs(chunkPos.x - playerChunkPos.x) <= viewDistance &&
                Math.abs(chunkPos.z - playerChunkPos.z) <= viewDistance;
    }

    public void onPlayerJoin(ServerPlayer player) {
        updatePlayerTracking(player);
    }

    public void onPlayerDisconnect(ServerPlayer player) {
        this.playerTrackedObjects.remove(player.getUUID());
        this.playerChunkPositions.remove(player.getUUID());
        this.playerViewDistances.remove(player.getUUID());
        synchronized (pendingSpawns) {
            this.pendingSpawns.remove(player);
        }
        synchronized (pendingRemovals) {
            this.pendingRemovals.remove(player);
        }
    }

    private void processPendingRemovals() {
        if (pendingRemovals.isEmpty()) return;
        synchronized (pendingRemovals) {
            pendingRemovals.forEach((player, removalList) -> {
                if (!removalList.isEmpty()) {
                    ObjectArrayList<UUID> batch = new ObjectArrayList<>();
                    for (UUID id : removalList) {
                        batch.add(id);
                        if (batch.size() >= 512) {
                            NetworkHandler.sendToPlayer(new RemovePhysicsObjectBatchPacket(batch), player);
                            batch.clear();
                        }
                    }
                    if (!batch.isEmpty()) {
                        NetworkHandler.sendToPlayer(new RemovePhysicsObjectBatchPacket(batch), player);
                    }
                }
            });
            pendingRemovals.clear();
        }
    }

    private void processPendingSpawns() {
        if (pendingSpawns.isEmpty()) return;
        synchronized (pendingSpawns) {
            pendingSpawns.forEach((player, spawnDataList) -> {
                if (!spawnDataList.isEmpty()) {
                    ObjectArrayList<SpawnData> batch = new ObjectArrayList<>();
                    int currentBatchSizeBytes = 0;
                    for (SpawnData data : spawnDataList) {
                        int dataSize = data.estimateSize();
                        if (!batch.isEmpty() && currentBatchSizeBytes + dataSize > MAX_PACKET_PAYLOAD_SIZE) {
                            NetworkHandler.sendToPlayer(new SpawnPhysicsObjectBatchPacket(batch), player);
                            batch.clear();
                            currentBatchSizeBytes = 0;
                        }
                        batch.add(data);
                        currentBatchSizeBytes += dataSize;
                    }
                    if (!batch.isEmpty()) {
                        NetworkHandler.sendToPlayer(new SpawnPhysicsObjectBatchPacket(batch), player);
                    }
                }
            });
            pendingSpawns.clear();
        }
    }

    private void sendStateUpdates() {
        ObjectArrayList<Integer> dirtyIndices = new ObjectArrayList<>();
        Integer index;
        while ((index = dirtyIndicesQueue.poll()) != null) {
            if (dataStore.getIdForIndex(index) != null) {
                dirtyIndices.add(index);
                dataStore.isDirty[index] = false;
            }
        }

        if (dirtyIndices.isEmpty()) {
            return;
        }

        Map<ServerPlayer, ObjectArrayList<Integer>> playerUpdateMap = new HashMap<>();
        List<ServerPlayer> players = level.players();

        for (int dirtyIndex : dirtyIndices) {
            ChunkPos chunkPos = new ChunkPos(
                    (int) Math.floor(dataStore.posX[dirtyIndex] / 16.0),
                    (int) Math.floor(dataStore.posZ[dirtyIndex] / 16.0)
            );
            for (ServerPlayer player : players) {
                if (isChunkVisible(player, chunkPos)) {
                    playerUpdateMap.computeIfAbsent(player, k -> new ObjectArrayList<>()).add(dirtyIndex);
                }
            }
        }

        if (playerUpdateMap.isEmpty()) {
            return;
        }

        level.getServer().execute(() -> {
            for (Map.Entry<ServerPlayer, ObjectArrayList<Integer>> entry : playerUpdateMap.entrySet()) {
                ServerPlayer player = entry.getKey();
                ObjectArrayList<Integer> indices = entry.getValue();

                for (int i = 0; i < indices.size(); i += MAX_UPDATES_PER_PACKET) {
                    int end = Math.min(i + MAX_UPDATES_PER_PACKET, indices.size());
                    List<Integer> sublist = indices.subList(i, end);
                    NetworkHandler.sendToPlayer(new SyncAllPhysicsObjectsPacket(sublist, dataStore), player);
                }
            }
        });
    }

    public void dispatchDataUpdate(VxAbstractBody obj) {
        if (obj.isDataDirty()) {
            SyncPhysicsObjectDataPacket packet = new SyncPhysicsObjectDataPacket(obj);
            ChunkPos chunkPos = VxObjectManager.getObjectChunkPos(obj);
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