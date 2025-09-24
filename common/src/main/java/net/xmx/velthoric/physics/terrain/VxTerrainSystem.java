/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.operator.Op;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
import com.github.stephengold.joltjni.readonly.ConstBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.object.manager.VxObjectDataStore;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.terrain.cache.TerrainShapeCache;
import net.xmx.velthoric.physics.terrain.cache.TerrainStorage;
import net.xmx.velthoric.physics.terrain.chunk.ChunkSnapshot;
import net.xmx.velthoric.physics.terrain.chunk.TerrainGenerator;
import net.xmx.velthoric.physics.terrain.job.VxTaskPriority;
import net.xmx.velthoric.physics.terrain.job.VxTerrainJobSystem;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author xI-Mx-Ix
 */
public class VxTerrainSystem implements Runnable {

    private static final int STATE_UNLOADED = 0;
    private static final int STATE_LOADING_SCHEDULED = 1;
    private static final int STATE_GENERATING_SHAPE = 2;
    private static final int STATE_READY_INACTIVE = 3;
    private static final int STATE_READY_ACTIVE = 4;
    private static final int STATE_REMOVING = 5;
    private static final int STATE_AIR_CHUNK = 6;

    private final VxPhysicsWorld physicsWorld;
    private final ServerLevel level;
    private final TerrainGenerator terrainGenerator;
    private final VxObjectDataStore objectDataStore;
    private final TerrainShapeCache shapeCache;
    private final TerrainStorage terrainStorage;
    private final VxTerrainJobSystem jobSystem;
    private final Thread workerThread;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    private final VxChunkDataStore chunkDataStore = new VxChunkDataStore();

    private final Set<VxSectionPos> chunksToRebuild = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<VxSectionPos>> objectTrackedChunks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> objectUpdateCooldowns = new ConcurrentHashMap<>();

    private static final ThreadLocal<UpdateContext> updateContext = ThreadLocal.withInitial(UpdateContext::new);

    private static final int UPDATE_INTERVAL_TICKS = 10;
    private static final float MAX_SPEED_FOR_COOLDOWN_SQR = 100f * 100f;
    private static final int PRELOAD_RADIUS_CHUNKS = 3;
    private static final int ACTIVATION_RADIUS_CHUNKS = 1;
    private static final float PREDICTION_SECONDS = 0.5f;

    private int objectUpdateIndex = 0;

    private static final int OBJECT_PRELOAD_UPDATE_STRIDE = 250;
    private static final int OBJECT_ACTIVATION_BATCH_SIZE = 100;

    public VxTerrainSystem(VxPhysicsWorld physicsWorld, ServerLevel level) {
        this.physicsWorld = physicsWorld;
        this.level = level;
        this.objectDataStore = physicsWorld.getObjectManager().getDataStore();
        this.shapeCache = new TerrainShapeCache(1024);
        this.terrainStorage = new TerrainStorage(this.level);
        this.terrainGenerator = new TerrainGenerator(this.shapeCache, this.terrainStorage);
        this.jobSystem = new VxTerrainJobSystem();
        this.workerThread = new Thread(this, "Velthoric Terrain Tracker - " + level.dimension().location().getPath());
        this.workerThread.setDaemon(true);
    }

    public void initialize() {
        if (isInitialized.compareAndSet(false, true)) {
            this.terrainStorage.initialize();
            this.workerThread.start();
        }
    }

    public void shutdown() {
        if (isInitialized.compareAndSet(true, false)) {
            jobSystem.shutdown();
            workerThread.interrupt();

            VxMainClass.LOGGER.debug("Shutting down Terrain Tracker for '{}'. Waiting up to 30 seconds for worker thread to exit...", level.dimension().location());

            try {
                workerThread.join(30000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                VxMainClass.LOGGER.warn("Interrupted while waiting for terrain tracker thread to stop. Proceeding with forced shutdown.");
            }

            if (workerThread.isAlive()) {

                StringBuilder stackTraceBuilder = new StringBuilder();
                stackTraceBuilder.append("Stack trace of deadlocked thread '").append(workerThread.getName()).append("':\n");

                StackTraceElement[] stackTrace = workerThread.getStackTrace();
                for (StackTraceElement ste : stackTrace) {

                    stackTraceBuilder.append("\tat ").append(ste).append("\n");
                }

                VxMainClass.LOGGER.fatal(

                        "Terrain tracker thread for '{}' did not terminate in 30 seconds and is likely deadlocked. " +
                                "Forcing shutdown to prevent a server hang. Some terrain data might not have been handled correctly.\n{}",
                        level.dimension().location(),
                        stackTraceBuilder.toString()
                );
            } else {
                VxMainClass.LOGGER.debug("Terrain tracker for '{}' shut down gracefully.", level.dimension().location());
            }

            VxMainClass.LOGGER.debug("Cleaning up terrain physics bodies for '{}'...", level.dimension().location());
            BodyInterface bi = physicsWorld.getBodyInterface();
            if (bi != null) {
                chunkDataStore.getManagedPositions().forEach(pos -> {
                    Integer index = chunkDataStore.getIndexForPos(pos);
                    if (index != null) {
                        removeBodyAndShape(index, bi);
                    }
                });
            }

            chunkDataStore.clear();
            chunksToRebuild.clear();
            objectTrackedChunks.clear();
            objectUpdateCooldowns.clear();
            shapeCache.clear();
            this.terrainStorage.shutdown();

            VxMainClass.LOGGER.debug("Terrain system for '{}' has been fully shut down.", level.dimension().location());
        }
    }

    @Override
    public void run() {
        while (isInitialized.get() && !Thread.currentThread().isInterrupted()) {
            try {
                updateTrackers();
                processRebuildQueue();
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Error in TerrainSystem worker thread", e);
            }
        }
    }

    public void saveDirtyRegions() {
        if (terrainStorage != null) {
            terrainStorage.saveDirtyRegions();
        }
    }

    public void onBlockUpdate(BlockPos worldPos, BlockState oldState, BlockState newState) {
        if (!isInitialized.get()) {
            return;
        }

        VxSectionPos pos = VxSectionPos.fromBlockPos(worldPos.immutable());
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index == null) return;

        int currentState = chunkDataStore.states[index];
        if (currentState == STATE_READY_ACTIVE || currentState == STATE_READY_INACTIVE || currentState == STATE_AIR_CHUNK) {
            chunksToRebuild.add(pos);
        }

        physicsWorld.execute(() -> {
            BodyInterface bi = physicsWorld.getBodyInterface();
            if (bi == null) return;

            UpdateContext ctx = updateContext.get();
            ctx.vec3_1.set(worldPos.getX() - 2.0f, worldPos.getY() - 2.0f, worldPos.getZ() - 2.0f);
            ctx.vec3_2.set(worldPos.getX() + 3.0f, worldPos.getY() + 3.0f, worldPos.getZ() + 3.0f);

            ctx.aabox_1.setMin(ctx.vec3_1);
            ctx.aabox_1.setMax(ctx.vec3_2);

            bi.activateBodiesInAaBox(ctx.aabox_1, ctx.bplFilter, ctx.olFilter);
        });
    }

    public void onChunkLoadedFromVanilla(@NotNull LevelChunk chunk) {
        if (!isInitialized.get() || jobSystem.isShutdown()) return;

        jobSystem.submit(() -> {
            ChunkPos chunkPos = chunk.getPos();
            for (int y = level.getMinSection(); y < level.getMaxSection(); ++y) {
                VxSectionPos vPos = new VxSectionPos(chunkPos.x, y, chunkPos.z);
                Integer index = chunkDataStore.getIndexForPos(vPos);
                if (index != null && chunkDataStore.referenceCounts[index] > 0) {
                    scheduleShapeGeneration(vPos, index, true, VxTaskPriority.HIGH);
                }
            }
        });
    }

    public void onChunkUnloaded(@NotNull ChunkPos chunkPos) {
        if (!isInitialized.get() || jobSystem.isShutdown()) return;
        jobSystem.submit(() -> {
            for (int y = level.getMinSection(); y < level.getMaxSection(); y++) {
                unloadChunkPhysicsInternal(new VxSectionPos(chunkPos.x, y, chunkPos.z));
            }
        });
    }

    private void processRebuildQueue() {
        if (chunksToRebuild.isEmpty()) return;

        Set<VxSectionPos> batch = new HashSet<>(chunksToRebuild);
        chunksToRebuild.removeAll(batch);

        for (VxSectionPos pos : batch) {
            Integer index = chunkDataStore.getIndexForPos(pos);
            if (index != null) {
                scheduleShapeGeneration(pos, index, false, VxTaskPriority.MEDIUM);
            }
        }
    }

    private void scheduleShapeGeneration(VxSectionPos pos, int index, boolean isInitialBuild, VxTaskPriority priority) {
        if (jobSystem.isShutdown()) {
            return;
        }

        if (!isInitialized.get() || jobSystem.isShutdown()) {
            return;
        }

        int currentState = chunkDataStore.states[index];
        if (currentState == STATE_REMOVING || currentState == STATE_LOADING_SCHEDULED || currentState == STATE_GENERATING_SHAPE) {
            return;
        }

        if (chunkDataStore.states[index] == currentState) {
            chunkDataStore.states[index] = STATE_LOADING_SCHEDULED;
            final int version = ++chunkDataStore.rebuildVersions[index];

            level.getServer().execute(() -> {
                if (version < chunkDataStore.rebuildVersions[index]) {
                    if (chunkDataStore.states[index] == STATE_LOADING_SCHEDULED) chunkDataStore.states[index] = currentState;
                    return;
                }

                LevelChunk chunk = level.getChunkSource().getChunk(pos.x(), pos.z(), false);
                if (chunk == null) {
                    chunkDataStore.states[index] = STATE_UNLOADED;
                    return;
                }

                ChunkSnapshot snapshot = ChunkSnapshot.snapshotFromChunk(level, chunk, pos);
                if (chunkDataStore.states[index] == STATE_LOADING_SCHEDULED) {
                    chunkDataStore.states[index] = STATE_GENERATING_SHAPE;
                    jobSystem.submit(() -> processShapeGenerationOnWorker(pos, index, version, snapshot, isInitialBuild, currentState));
                }
            });
        }
    }

    private void processShapeGenerationOnWorker(VxSectionPos pos, int index, int version, ChunkSnapshot snapshot, boolean isInitialBuild, int previousState) {
        if (version < chunkDataStore.rebuildVersions[index]) {
            if (chunkDataStore.states[index] == STATE_GENERATING_SHAPE) chunkDataStore.states[index] = previousState;
            return;
        }

        try {
            if (!isInitialized.get()) {
                return;
            }
            ShapeRefC generatedShape = terrainGenerator.generateShape(level, snapshot);
            physicsWorld.execute(() -> applyGeneratedShape(pos, index, version, generatedShape, isInitialBuild));
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Exception during terrain shape generation for {}", pos, e);
            chunkDataStore.states[index] = STATE_UNLOADED;
        }
    }

    private void applyGeneratedShape(VxSectionPos pos, int index, int version, ShapeRefC shape, boolean isInitialBuild) {
        boolean wasActive = chunkDataStore.states[index] == STATE_READY_ACTIVE;

        if (version < chunkDataStore.rebuildVersions[index] || chunkDataStore.states[index] == STATE_REMOVING) {
            if (shape != null) shape.close();
            if (chunkDataStore.states[index] != STATE_REMOVING) chunkDataStore.states[index] = STATE_UNLOADED;
            return;
        }

        BodyInterface bodyInterface = physicsWorld.getBodyInterface();
        if (bodyInterface == null) {
            if (shape != null) shape.close();
            chunkDataStore.states[index] = STATE_UNLOADED;
            return;
        }

        int bodyId = chunkDataStore.bodyIds[index];
        if (bodyId != VxChunkDataStore.UNUSED_BODY_ID) {
            if (shape != null) {
                bodyInterface.setShape(bodyId, shape, true, EActivation.DontActivate);
                chunkDataStore.setShape(index, shape);
                chunkDataStore.states[index] = wasActive ? STATE_READY_ACTIVE : STATE_READY_INACTIVE;
            } else {
                removeBodyAndShape(index, bodyInterface);
                chunkDataStore.states[index] = STATE_AIR_CHUNK;
            }
        } else if (shape != null) {
            RVec3 position = new RVec3(pos.getOrigin().getX(), pos.getOrigin().getY(), pos.getOrigin().getZ());
            try (BodyCreationSettings bcs = new BodyCreationSettings(shape, position, Quat.sIdentity(), EMotionType.Static, VxLayers.TERRAIN)) {
                Body body = bodyInterface.createBody(bcs);
                if (body != null) {
                    chunkDataStore.bodyIds[index] = body.getId();
                    chunkDataStore.setShape(index, shape);
                    chunkDataStore.states[index] = wasActive ? STATE_READY_ACTIVE : STATE_READY_INACTIVE;
                } else {
                    VxMainClass.LOGGER.error("Failed to create terrain body for chunk {}", pos);
                    shape.close();
                    chunkDataStore.states[index] = STATE_UNLOADED;
                }
            }
        } else {
            chunkDataStore.states[index] = STATE_AIR_CHUNK;
        }

        chunkDataStore.isPlaceholder[index] = isInitialBuild;
        if (wasActive) {
            activateChunk(pos, index);
        }
    }

    private void activateChunk(VxSectionPos pos, int index) {
        if (chunkDataStore.states[index] == STATE_AIR_CHUNK) {
            return;
        }

        if (chunkDataStore.bodyIds[index] != VxChunkDataStore.UNUSED_BODY_ID && chunkDataStore.states[index] == STATE_READY_INACTIVE) {
            chunkDataStore.states[index] = STATE_READY_ACTIVE;
            physicsWorld.execute(() -> {
                BodyInterface bodyInterface = physicsWorld.getBodyInterface();
                int bodyId = chunkDataStore.bodyIds[index];
                if (bodyInterface != null && bodyId != VxChunkDataStore.UNUSED_BODY_ID && !bodyInterface.isAdded(bodyId)) {
                    bodyInterface.addBody(bodyId, EActivation.Activate);
                }
            });
        }

        if (isPlaceholder(pos, index) && chunkDataStore.bodyIds[index] != VxChunkDataStore.UNUSED_BODY_ID) {
            scheduleShapeGeneration(pos, index, false, VxTaskPriority.CRITICAL);
        }
    }

    private void deactivateChunk(int index) {
        if (chunkDataStore.bodyIds[index] != VxChunkDataStore.UNUSED_BODY_ID && chunkDataStore.states[index] == STATE_READY_ACTIVE) {
            chunkDataStore.states[index] = STATE_READY_INACTIVE;
            physicsWorld.execute(() -> {
                BodyInterface bodyInterface = physicsWorld.getBodyInterface();
                int bodyId = chunkDataStore.bodyIds[index];
                if (bodyInterface != null && bodyId != VxChunkDataStore.UNUSED_BODY_ID && bodyInterface.isAdded(bodyId)) {
                    bodyInterface.removeBody(bodyId);
                }
            });
        }
    }

    private void unloadChunkPhysicsInternal(VxSectionPos pos) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index == null) return;

        chunkDataStore.states[index] = STATE_REMOVING;
        chunkDataStore.rebuildVersions[index]++;
        chunksToRebuild.remove(pos);

        physicsWorld.execute(() -> {
            removeBodyAndShape(index, physicsWorld.getBodyInterface());
            chunkDataStore.removeChunk(pos);
        });
    }

    private void removeBodyAndShape(int index, BodyInterface bodyInterface) {
        int bodyId = chunkDataStore.bodyIds[index];
        if (bodyId != VxChunkDataStore.UNUSED_BODY_ID && bodyInterface != null) {
            if (bodyInterface.isAdded(bodyId)) {
                bodyInterface.removeBody(bodyId);
            }
            bodyInterface.destroyBody(bodyId);
        }
        chunkDataStore.bodyIds[index] = VxChunkDataStore.UNUSED_BODY_ID;
        chunkDataStore.setShape(index, null);
    }

    public void requestChunk(VxSectionPos pos) {
        int index = chunkDataStore.addChunk(pos);
        if (++chunkDataStore.referenceCounts[index] == 1) {
            scheduleShapeGeneration(pos, index, true, VxTaskPriority.HIGH);
        }
    }

    public void releaseChunk(VxSectionPos pos) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index != null && --chunkDataStore.referenceCounts[index] == 0) {
            unloadChunkPhysicsInternal(pos);
        }
    }

    public void prioritizeChunk(VxSectionPos pos, VxTaskPriority priority) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index != null && (isPlaceholder(pos, index) || !isReady(pos, index))) {
            scheduleShapeGeneration(pos, index, false, priority);
        }
    }

    private void updateTrackers() {
        if (!isInitialized.get()) {
            return;
        }

        List<VxBody> currentObjects = new ArrayList<>(physicsWorld.getObjectManager().getAllObjects());
        Set<UUID> currentObjectIds = new HashSet<>(currentObjects.size());
        for (VxBody obj : currentObjects) {
            currentObjectIds.add(obj.getPhysicsId());
        }

        objectTrackedChunks.keySet().removeIf(id -> {
            if (!currentObjectIds.contains(id)) {
                removeObjectTracking(id);
                return true;
            }
            return false;
        });

        if (currentObjects.isEmpty()) {
            for (int index : chunkDataStore.getActiveIndices()) {
                if (chunkDataStore.states[index] == STATE_READY_ACTIVE) {
                    deactivateChunk(index);
                }
            }
            return;
        }

        int objectsToUpdate = Math.min(currentObjects.size(), OBJECT_PRELOAD_UPDATE_STRIDE);
        for (int i = 0; i < objectsToUpdate; ++i) {
            if (objectUpdateIndex >= currentObjects.size()) {
                objectUpdateIndex = 0;
            }
            VxBody obj = currentObjects.get(objectUpdateIndex++);
            if (obj.getBodyId() != 0) {
                updatePreloadForObject(obj);
            } else {
                removeObjectTracking(obj.getPhysicsId());
            }
        }

        List<List<VxBody>> batches = partitionList(currentObjects, OBJECT_ACTIVATION_BATCH_SIZE);
        List<CompletableFuture<Set<VxSectionPos>>> futures = new ArrayList<>();

        if (jobSystem.isShutdown()) {
            return;
        }

        for (List<VxBody> batch : batches) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                Set<VxSectionPos> required = new HashSet<>();
                for (VxBody obj : batch) {
                    ConstBody body = obj.getBody();
                    if (body != null) {
                        ConstAaBox bounds = body.getWorldSpaceBounds();
                        calculateRequiredChunks(
                                bounds.getMin(), bounds.getMax(),
                                body.getLinearVelocity(),
                                ACTIVATION_RADIUS_CHUNKS,
                                required
                        );
                    }
                }
                return required;
            }, jobSystem.getExecutor()));
        }

        Set<VxSectionPos> activeSet = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        activeSet.forEach(pos -> prioritizeChunk(pos, VxTaskPriority.CRITICAL));

        for (int index : chunkDataStore.getActiveIndices()) {
            VxSectionPos pos = chunkDataStore.getPosForIndex(index);
            if (pos == null) continue;

            if (chunkDataStore.states[index] == STATE_READY_ACTIVE && !activeSet.contains(pos)) {
                deactivateChunk(index);
            }
        }

        for (VxSectionPos pos : activeSet) {
            Integer index = chunkDataStore.getIndexForPos(pos);
            if (index != null) {
                activateChunk(pos, index);
            }
        }
    }

    private void updatePreloadForObject(VxBody obj) {
        UUID id = obj.getPhysicsId();
        int dataIndex = obj.getDataStoreIndex();
        if (dataIndex == -1) {
            removeObjectTracking(id);
            return;
        }

        int cooldown = objectUpdateCooldowns.getOrDefault(id, 0);
        float velX = objectDataStore.velX[dataIndex];
        float velY = objectDataStore.velY[dataIndex];
        float velZ = objectDataStore.velZ[dataIndex];
        float velSq = velX * velX + velY * velY + velZ * velZ;

        if (cooldown > 0 && velSq < MAX_SPEED_FOR_COOLDOWN_SQR) {
            objectUpdateCooldowns.put(id, cooldown - 1);
            return;
        }
        objectUpdateCooldowns.put(id, UPDATE_INTERVAL_TICKS);

        Set<VxSectionPos> required = new HashSet<>();
        ConstBody body = obj.getBody();
        if (body != null) {
            ConstAaBox bounds = body.getWorldSpaceBounds();
            calculateRequiredChunks(bounds.getMin(), bounds.getMax(), velX, velY, velZ, PRELOAD_RADIUS_CHUNKS, required);
        } else {
            removeObjectTracking(id);
            return;
        }

        Set<VxSectionPos> previouslyTracked = objectTrackedChunks.computeIfAbsent(id, k -> new HashSet<>());

        previouslyTracked.removeIf(pos -> {
            if (!required.contains(pos)) {
                releaseChunk(pos);
                return true;
            }
            return false;
        });

        for (VxSectionPos pos : required) {
            if (previouslyTracked.add(pos)) {
                requestChunk(pos);
            }
        }
    }

    private void removeObjectTracking(UUID id) {
        Set<VxSectionPos> chunksToRelease = objectTrackedChunks.remove(id);
        if (chunksToRelease != null) {
            chunksToRelease.forEach(this::releaseChunk);
        }
        objectUpdateCooldowns.remove(id);
    }

    private void calculateRequiredChunksForObject(ConstAaBox currentAabb, Vec3 velocity, Set<VxSectionPos> outRequiredChunks) {
        addChunksForAabb(currentAabb, PRELOAD_RADIUS_CHUNKS, outRequiredChunks);

        Vec3 tempDisplacement = new Vec3(velocity);
        RVec3 tempRVec3Min = new RVec3(currentAabb.getMin());
        RVec3 tempRVec3Max = new RVec3(currentAabb.getMax());
        Vec3 tempVec3Min = new Vec3();
        Vec3 tempVec3Max = new Vec3();
        try (AaBox predictedAabb = new AaBox()) {
            Op.starEquals(tempDisplacement, PREDICTION_SECONDS);
            Op.plusEquals(tempRVec3Min, tempDisplacement);
            Op.plusEquals(tempRVec3Max, tempDisplacement);

            tempVec3Min.set(tempRVec3Min);
            tempVec3Max.set(tempRVec3Max);
            predictedAabb.setMin(tempVec3Min);
            predictedAabb.setMax(tempVec3Max);

            addChunksForAabb(predictedAabb, PRELOAD_RADIUS_CHUNKS, outRequiredChunks);
        }
    }

    private void calculateActivationChunksForObject(ConstAaBox currentAabb, Vec3 velocity, Set<VxSectionPos> outChunks) {
        addChunksForAabb(currentAabb, ACTIVATION_RADIUS_CHUNKS, outChunks);

        Vec3 tempDisplacement = new Vec3(velocity);
        RVec3 tempRVec3Min = new RVec3(currentAabb.getMin());
        RVec3 tempRVec3Max = new RVec3(currentAabb.getMax());
        Vec3 tempVec3Min = new Vec3();
        Vec3 tempVec3Max = new Vec3();
        try (AaBox predictedAabb = new AaBox()) {
            Op.starEquals(tempDisplacement, PREDICTION_SECONDS);
            Op.plusEquals(tempRVec3Min, tempDisplacement);
            Op.plusEquals(tempRVec3Max, tempDisplacement);

            tempVec3Min.set(tempRVec3Min);
            tempVec3Max.set(tempRVec3Max);
            predictedAabb.setMin(tempVec3Min);
            predictedAabb.setMax(tempVec3Max);

            addChunksForAabb(predictedAabb, ACTIVATION_RADIUS_CHUNKS, outChunks);
        }
    }

    private void addChunksForAabb(ConstAaBox aabb, int radiusInChunks, Set<VxSectionPos> outChunks) {
        RVec3 tempRVec3Min = new RVec3(aabb.getMin());
        RVec3 tempRVec3Max = new RVec3(aabb.getMax());

        VxSectionPos minSection = VxSectionPos.fromWorldSpace(tempRVec3Min.xx(), tempRVec3Min.yy(), tempRVec3Min.zz());
        VxSectionPos maxSection = VxSectionPos.fromWorldSpace(tempRVec3Max.xx(), tempRVec3Max.yy(), tempRVec3Max.zz());

        final int worldMinY = level.getMinBuildHeight() >> 4;
        final int worldMaxY = level.getMaxBuildHeight() >> 4;

        for (int y = minSection.y() - radiusInChunks; y <= maxSection.y() + radiusInChunks; ++y) {
            if (y < worldMinY || y >= worldMaxY) continue;
            for (int z = minSection.z() - radiusInChunks; z <= maxSection.z() + radiusInChunks; ++z) {
                for (int x = minSection.x() - radiusInChunks; x <= maxSection.x() + radiusInChunks; ++x) {
                    outChunks.add(new VxSectionPos(x, y, z));
                }
            }
        }
    }

    private void calculateRequiredChunks(Vec3 min, Vec3 max, Vec3 velocity, int radius, Set<VxSectionPos> outChunks) {
        calculateRequiredChunks(min, max, velocity.getX(), velocity.getY(), velocity.getZ(), radius, outChunks);
    }

    private void calculateRequiredChunks(Vec3 min, Vec3 max, float velX, float velY, float velZ, int radius, Set<VxSectionPos> outChunks) {
        addChunksForBounds(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ(), radius, outChunks);

        double predMinX = min.getX() + velX * PREDICTION_SECONDS;
        double predMinY = min.getY() + velY * PREDICTION_SECONDS;
        double predMinZ = min.getZ() + velZ * PREDICTION_SECONDS;
        double predMaxX = max.getX() + velX * PREDICTION_SECONDS;
        double predMaxY = max.getY() + velY * PREDICTION_SECONDS;
        double predMaxZ = max.getZ() + velZ * PREDICTION_SECONDS;

        addChunksForBounds(predMinX, predMinY, predMinZ, predMaxX, predMaxY, predMaxZ, radius, outChunks);
    }

    private void addChunksForBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int radiusInChunks, Set<VxSectionPos> outChunks) {
        int minSectionX = ((int) Math.floor(minX) >> 4) - radiusInChunks;
        int minSectionY = ((int) Math.floor(minY) >> 4) - radiusInChunks;
        int minSectionZ = ((int) Math.floor(minZ) >> 4) - radiusInChunks;
        int maxSectionX = ((int) Math.floor(maxX) >> 4) + radiusInChunks;
        int maxSectionY = ((int) Math.floor(maxY) >> 4) + radiusInChunks;
        int maxSectionZ = ((int) Math.floor(maxZ) >> 4) + radiusInChunks;

        final int worldMinY = level.getMinBuildHeight() >> 4;
        final int worldMaxY = level.getMaxBuildHeight() >> 4;

        for (int y = minSectionY; y <= maxSectionY; ++y) {
            if (y < worldMinY || y >= worldMaxY) continue;
            for (int z = minSectionZ; z <= maxSectionZ; ++z) {
                for (int x = minSectionX; x <= maxSectionX; ++x) {
                    outChunks.add(new VxSectionPos(x, y, z));
                }
            }
        }
    }

    private static <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        int numBatches = (int) Math.ceil((double) list.size() / (double) batchSize);
        return IntStream.range(0, numBatches)
                .mapToObj(i -> list.subList(i * batchSize, Math.min((i + 1) * batchSize, list.size())))
                .collect(Collectors.toList());
    }

    public boolean isReady(VxSectionPos pos) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        return index != null && isReady(pos, index);
    }

    private boolean isReady(VxSectionPos pos, int index) {
        int state = chunkDataStore.states[index];
        return state == STATE_READY_ACTIVE || state == STATE_READY_INACTIVE || state == STATE_AIR_CHUNK;
    }

    public boolean isPlaceholder(VxSectionPos pos) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        return index == null || isPlaceholder(pos, index);
    }

    private boolean isPlaceholder(VxSectionPos pos, int index) {
        return chunkDataStore.isPlaceholder[index];
    }

    public boolean isSectionReady(SectionPos sectionPos) {
        if (sectionPos == null) {
            return false;
        }
        return isReady(new VxSectionPos(sectionPos.x(), sectionPos.y(), sectionPos.z()));
    }

    public boolean isTerrainBody(int bodyId) {
        if (bodyId <= 0) return false;
        for (int id : chunkDataStore.bodyIds) {
            if (id == bodyId) return true;
        }
        return false;
    }

    public ServerLevel getLevel() {
        return level;
    }
}