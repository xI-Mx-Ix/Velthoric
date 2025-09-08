package net.xmx.velthoric.physics.object.manager;

import com.github.stephengold.joltjni.Vec3;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.network.NetworkHandler;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.object.packet.SpawnData;
import net.xmx.velthoric.physics.object.packet.batch.RemovePhysicsObjectBatchPacket;
import net.xmx.velthoric.physics.object.packet.batch.SpawnPhysicsObjectBatchPacket;
import net.xmx.velthoric.physics.object.packet.batch.SyncAllPhysicsObjectsPacket;
import net.xmx.velthoric.physics.object.packet.SyncPhysicsObjectDataPacket;
import net.xmx.velthoric.physics.object.state.PhysicsObjectState;
import net.xmx.velthoric.physics.object.state.PhysicsObjectStatePool;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VxObjectNetworkDispatcher {

    private final ServerLevel level;
    private final VxObjectManager manager;
    private final VxObjectDataStore dataStore;
    private static final int MAX_PACKET_PAYLOAD_SIZE = 128 * 1024;
    private static final int NETWORK_THREAD_TICK_RATE_MS = 10;

    private final Map<UUID, Set<UUID>> playerTrackedObjects = new ConcurrentHashMap<>();
    private final Map<UUID, ChunkPos> playerChunkPositions = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerViewDistances = new ConcurrentHashMap<>();
    private final Object2ObjectMap<ServerPlayer, ObjectArrayList<SpawnData>> pendingSpawns = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<ServerPlayer, ObjectArrayList<UUID>> pendingRemovals = new Object2ObjectOpenHashMap<>();
    private final ObjectArrayList<SpawnData> spawnBatch = new ObjectArrayList<>();
    private final ObjectArrayList<UUID> removalBatch = new ObjectArrayList<>();

    private ExecutorService networkSyncExecutor;
    private final ThreadLocal<VxTransform> tempTransform = ThreadLocal.withInitial(VxTransform::new);
    private final ThreadLocal<Vec3> tempLinVel = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<Vec3> tempAngVel = ThreadLocal.withInitial(Vec3::new);

    public VxObjectNetworkDispatcher(ServerLevel level, VxObjectManager manager) {
        this.level = level;
        this.manager = manager;
        this.dataStore = manager.getDataStore();
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
            pendingSpawns.computeIfAbsent(player, k -> new ObjectArrayList<>())
                    .add(new SpawnData(body, System.nanoTime()));
        }
    }

    private void stopTracking(ServerPlayer player, UUID bodyId) {
        Set<UUID> tracked = playerTrackedObjects.get(player.getUUID());
        if (tracked != null && tracked.remove(bodyId)) {
            pendingRemovals.computeIfAbsent(player, k -> new ObjectArrayList<>())
                    .add(bodyId);
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
            for (SpawnData data : spawnDataList) {
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
        ObjectArrayList<PhysicsObjectState> statesToProcess = new ObjectArrayList<>();

        for (int i = 0; i < dataStore.getCapacity(); i++) {
            if (dataStore.isDirty[i]) {
                UUID id = dataStore.getIdForIndex(i);
                if (id == null) {
                    dataStore.isDirty[i] = false;
                    continue;
                }

                PhysicsObjectState state = PhysicsObjectStatePool.acquire();

                VxTransform transform = tempTransform.get();
                transform.getTranslation().set(dataStore.posX[i], dataStore.posY[i], dataStore.posZ[i]);
                transform.getRotation().set(dataStore.rotX[i], dataStore.rotY[i], dataStore.rotZ[i], dataStore.rotW[i]);

                Vec3 linVel = tempLinVel.get();
                linVel.set(dataStore.velX[i], dataStore.velY[i], dataStore.velZ[i]);

                Vec3 angVel = tempAngVel.get();
                angVel.set(dataStore.angVelX[i], dataStore.angVelY[i], dataStore.angVelZ[i]);

                state.from(id, dataStore.bodyType[i], transform, linVel, angVel, dataStore.vertexData[i], dataStore.lastUpdateTimestamp[i], dataStore.isActive[i]);
                statesToProcess.add(state);

                dataStore.isDirty[i] = false;
            }
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

        statesToProcess.forEach(PhysicsObjectStatePool::release);
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