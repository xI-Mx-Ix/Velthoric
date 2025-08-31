package net.xmx.velthoric.physics.terrain;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.operator.Op;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.terrain.cache.TerrainShapeCache;
import net.xmx.velthoric.physics.terrain.cache.TerrainStorage;
import net.xmx.velthoric.physics.terrain.job.VxTaskPriority;
import net.xmx.velthoric.physics.terrain.job.VxTerrainJobSystem;
import net.xmx.velthoric.physics.terrain.chunk.ChunkSnapshot;
import net.xmx.velthoric.physics.terrain.chunk.TerrainGenerator;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
    private final TerrainShapeCache shapeCache;
    private final TerrainStorage terrainStorage;
    private final VxTerrainJobSystem jobSystem;
    private final Thread workerThread;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    private final ConcurrentHashMap<VxSectionPos, AtomicInteger> chunkStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<VxSectionPos, Integer> chunkBodyIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<VxSectionPos, ShapeRefC> chunkShapes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<VxSectionPos, Boolean> chunkIsPlaceholder = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<VxSectionPos, AtomicInteger> chunkRebuildVersions = new ConcurrentHashMap<>();
    private final Map<VxSectionPos, Integer> chunkReferenceCounts = new ConcurrentHashMap<>();
    private final Set<VxSectionPos> chunksToRebuild = ConcurrentHashMap.newKeySet();

    private final Map<UUID, Set<VxSectionPos>> objectTrackedChunks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> objectUpdateCooldowns = new ConcurrentHashMap<>();

    private static final int UPDATE_INTERVAL_TICKS = 10;
    private static final float MAX_SPEED_FOR_COOLDOWN_SQR = 100f * 100f;
    private static final int PRELOAD_RADIUS_CHUNKS = 3;
    private static final int ACTIVATION_RADIUS_CHUNKS = 1;
    private static final float PREDICTION_SECONDS = 0.5f;

    public VxTerrainSystem(VxPhysicsWorld physicsWorld, ServerLevel level) {
        this.physicsWorld = physicsWorld;
        this.level = level;
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
            try {
                workerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            this.terrainStorage.shutdown();

            physicsWorld.execute(() -> {
                new HashSet<>(chunkStates.keySet()).forEach(this::unloadChunkPhysicsInternal);
                chunkReferenceCounts.clear();
                objectTrackedChunks.clear();
                objectUpdateCooldowns.clear();
                shapeCache.clear();
                chunksToRebuild.clear();
            });
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
        AtomicInteger state = chunkStates.get(pos);

        if (state != null) {
            int currentState = state.get();
            if (currentState == STATE_READY_ACTIVE || currentState == STATE_READY_INACTIVE || currentState == STATE_AIR_CHUNK) {
                chunksToRebuild.add(pos);
            }
        }

        physicsWorld.execute(() -> {
            BodyInterface bi = physicsWorld.getBodyInterface();
            if (bi == null) return;

            RVec3 min = new RVec3(worldPos.getX() - 2.0, worldPos.getY() - 2.0, worldPos.getZ() - 2.0);
            RVec3 max = new RVec3(worldPos.getX() + 3.0, worldPos.getY() + 3.0, worldPos.getZ() + 3.0);

            try (
                    AaBox aabox = new AaBox(min, max);
                    BroadPhaseLayerFilter bplFilter = new BroadPhaseLayerFilter();
                    ObjectLayerFilter olFilter = new ObjectLayerFilter()
            ) {
                bi.activateBodiesInAaBox(aabox, bplFilter, olFilter);
            }
        });
    }

    public void onChunkLoadedFromVanilla(@NotNull LevelChunk chunk) {
        if (!isInitialized.get() || jobSystem.isShutdown()) return;

        jobSystem.submit(() -> {
            ChunkPos chunkPos = chunk.getPos();
            for (int y = level.getMinSection(); y < level.getMaxSection(); ++y) {
                VxSectionPos vPos = new VxSectionPos(chunkPos.x, y, chunkPos.z);
                if (chunkReferenceCounts.getOrDefault(vPos, 0) > 0) {
                    scheduleShapeGeneration(vPos, true, VxTaskPriority.HIGH);
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
            scheduleShapeGeneration(pos, false, VxTaskPriority.MEDIUM);
        }
    }

    private void scheduleShapeGeneration(VxSectionPos pos, boolean isInitialBuild, VxTaskPriority priority) {
        if (jobSystem.isShutdown()) {
            return;
        }

        AtomicInteger state = getState(pos);
        int currentState = state.get();

        if (currentState == STATE_REMOVING || currentState == STATE_LOADING_SCHEDULED || currentState == STATE_GENERATING_SHAPE) {
            return;
        }

        if (state.compareAndSet(currentState, STATE_LOADING_SCHEDULED)) {
            final int version = getRebuildVersion(pos).incrementAndGet();

            level.getServer().execute(() -> {
                if (version < getRebuildVersion(pos).get()) {
                    state.compareAndSet(STATE_LOADING_SCHEDULED, currentState);
                    return;
                }

                LevelChunk chunk = level.getChunkSource().getChunk(pos.x(), pos.z(), false);
                if (chunk == null) {
                    state.set(STATE_UNLOADED);
                    return;
                }

                ChunkSnapshot snapshot = ChunkSnapshot.snapshotFromChunk(level, chunk, pos);

                if (!state.compareAndSet(STATE_LOADING_SCHEDULED, STATE_GENERATING_SHAPE)) {
                    return;
                }

                jobSystem.submit(() -> processShapeGenerationOnWorker(pos, version, snapshot, isInitialBuild, currentState));
            });
        }
    }

    private void processShapeGenerationOnWorker(VxSectionPos pos, int version, ChunkSnapshot snapshot, boolean isInitialBuild, int previousState) {
        if (version < getRebuildVersion(pos).get()) {
            getState(pos).compareAndSet(STATE_GENERATING_SHAPE, previousState);
            return;
        }

        try {
            ShapeRefC generatedShape = terrainGenerator.generateShape(level, snapshot);
            physicsWorld.execute(() -> applyGeneratedShape(pos, version, generatedShape, isInitialBuild));
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Exception during terrain shape generation for {}", pos, e);
            getState(pos).set(STATE_UNLOADED);
        }
    }

    private void applyGeneratedShape(VxSectionPos pos, int version, ShapeRefC shape, boolean isInitialBuild) {
        AtomicInteger state = getState(pos);
        boolean wasActive = state.get() == STATE_READY_ACTIVE;

        if (version < getRebuildVersion(pos).get() || state.get() == STATE_REMOVING) {
            if (shape != null) {
                shape.close();
            }
            if (state.get() != STATE_REMOVING) {
                state.set(STATE_UNLOADED);
            }
            return;
        }

        BodyInterface bodyInterface = physicsWorld.getBodyInterface();
        if (bodyInterface == null) {
            if (shape != null) {
                shape.close();
            }
            state.set(STATE_UNLOADED);
            return;
        }

        Integer bodyId = chunkBodyIds.get(pos);

        if (bodyId != null) {
            if (shape != null) {
                bodyInterface.setShape(bodyId, shape, true, EActivation.DontActivate);
                ShapeRefC oldShape = chunkShapes.put(pos, shape);
                if (oldShape != null && !oldShape.equals(shape)) {
                    oldShape.close();
                }
                state.set(wasActive ? STATE_READY_ACTIVE : STATE_READY_INACTIVE);
            } else {
                removeBodyAndShape(pos, bodyInterface);
                state.set(STATE_AIR_CHUNK);
            }
        } else if (shape != null) {
            RVec3 position = new RVec3(pos.getOrigin().getX(), pos.getOrigin().getY(), pos.getOrigin().getZ());
            try (BodyCreationSettings bcs = new BodyCreationSettings(shape, position, Quat.sIdentity(), EMotionType.Static, VxLayers.STATIC)) {

                Body body = bodyInterface.createBody(bcs);
                if (body != null) {
                    chunkBodyIds.put(pos, body.getId());
                    chunkShapes.put(pos, shape);

                    state.set(wasActive ? STATE_READY_ACTIVE : STATE_READY_INACTIVE);
                } else {
                    VxMainClass.LOGGER.error("Failed to create terrain body for chunk {}", pos);
                    shape.close();
                    state.set(STATE_UNLOADED);
                }
            }
        } else {
            state.set(STATE_AIR_CHUNK);
        }

        chunkIsPlaceholder.put(pos, isInitialBuild);

        if (wasActive) {
            activateChunk(pos);
        }
    }

    private void activateChunk(VxSectionPos pos) {
        AtomicInteger state = getState(pos);
        if (state.get() == STATE_AIR_CHUNK) {
            return;
        }

        if (chunkBodyIds.containsKey(pos) && state.compareAndSet(STATE_READY_INACTIVE, STATE_READY_ACTIVE)) {
            physicsWorld.execute(() -> {
                BodyInterface bodyInterface = physicsWorld.getBodyInterface();
                Integer bodyId = chunkBodyIds.get(pos);
                if (bodyInterface != null && bodyId != null && !bodyInterface.isAdded(bodyId)) {
                    bodyInterface.addBody(bodyId, EActivation.Activate);
                }
            });
        }

        if (isPlaceholder(pos) && chunkBodyIds.containsKey(pos)) {
            scheduleShapeGeneration(pos, false, VxTaskPriority.CRITICAL);
        }
    }

    private void deactivateChunk(VxSectionPos pos) {
        if (chunkBodyIds.containsKey(pos) && getState(pos).compareAndSet(STATE_READY_ACTIVE, STATE_READY_INACTIVE)) {
            physicsWorld.execute(() -> {
                BodyInterface bodyInterface = physicsWorld.getBodyInterface();
                Integer bodyId = chunkBodyIds.get(pos);
                if (bodyInterface != null && bodyId != null && bodyInterface.isAdded(bodyId)) {
                    bodyInterface.removeBody(bodyId);
                }
            });
        }
    }

    private void unloadChunkPhysicsInternal(VxSectionPos pos) {
        AtomicInteger state = getState(pos);
        int oldState = state.getAndSet(STATE_REMOVING);
        if (oldState == STATE_REMOVING) return;

        getRebuildVersion(pos).incrementAndGet();

        physicsWorld.execute(() -> {
            removeBodyAndShape(pos, physicsWorld.getBodyInterface());
            chunkStates.remove(pos);
            chunkIsPlaceholder.remove(pos);
            chunkRebuildVersions.remove(pos);
            chunksToRebuild.remove(pos);
        });
    }

    private void removeBodyAndShape(VxSectionPos pos, BodyInterface bodyInterface) {
        Integer bodyId = chunkBodyIds.remove(pos);
        if (bodyId != null && bodyInterface != null) {
            if (bodyInterface.isAdded(bodyId)) {
                bodyInterface.removeBody(bodyId);
            }
            bodyInterface.destroyBody(bodyId);
        }
        ShapeRefC shape = chunkShapes.remove(pos);
        if (shape != null) {
            shape.close();
        }
    }

    public void requestChunk(VxSectionPos pos) {
        if (chunkReferenceCounts.compute(pos, (p, count) -> (count == null) ? 1 : count + 1) == 1) {
            getState(pos);
            getRebuildVersion(pos);
            scheduleShapeGeneration(pos, true, VxTaskPriority.HIGH);
        }
    }

    public void releaseChunk(VxSectionPos pos) {
        Integer newCount = chunkReferenceCounts.computeIfPresent(pos, (p, count) -> count > 1 ? count - 1 : null);
        if (newCount == null) {
            unloadChunkPhysicsInternal(pos);
        }
    }

    public void prioritizeChunk(VxSectionPos pos, VxTaskPriority priority) {
        if (isPlaceholder(pos) || !isReady(pos)) {
            scheduleShapeGeneration(pos, false, priority);
        }
    }

    private void updateTrackers() {
        Collection<VxAbstractBody> currentObjects = physicsWorld.getObjectManager().getObjectContainer().getAllObjects();
        Set<UUID> currentObjectIds = currentObjects.stream()
                .map(VxAbstractBody::getPhysicsId)
                .collect(Collectors.toSet());

        objectTrackedChunks.keySet().removeIf(id -> {
            if (!currentObjectIds.contains(id)) {
                Set<VxSectionPos> chunksToRelease = objectTrackedChunks.get(id);
                if (chunksToRelease != null) {
                    chunksToRelease.forEach(this::releaseChunk);
                }
                objectUpdateCooldowns.remove(id);
                return true;
            }
            return false;
        });

        if (currentObjects.isEmpty()) {
            chunkStates.entrySet().stream()
                    .filter(e -> e.getValue().get() == STATE_READY_ACTIVE)
                    .map(Map.Entry::getKey)
                    .forEach(this::deactivateChunk);
            return;
        }

        List<CompletableFuture<Map<UUID, Set<VxSectionPos>>>> futures = new ArrayList<>();
        List<VxAbstractBody> batch = new ArrayList<>();
        for (VxAbstractBody obj : currentObjects) {
            if (obj.getBodyId() != 0) {
                batch.add(obj);
                if (batch.size() >= 100) {
                    List<VxAbstractBody> finalBatch = new ArrayList<>(batch);
                    futures.add(CompletableFuture.supplyAsync(() -> updateTrackerBatch(finalBatch), jobSystem.getExecutor()));
                    batch.clear();
                }
            } else {
                removeObjectTracking(obj.getPhysicsId());
            }
        }
        if (!batch.isEmpty()) {
            List<VxAbstractBody> finalBatch = new ArrayList<>(batch);
            futures.add(CompletableFuture.supplyAsync(() -> updateTrackerBatch(finalBatch), jobSystem.getExecutor()));
        }

        Map<VxSectionPos, VxTaskPriority> allRequiredChunks = new HashMap<>();

        futures.stream()
                .map(CompletableFuture::join)
                .forEach(resultMap -> {
                    resultMap.forEach((id, required) -> {
                        Set<VxSectionPos> previouslyTracked = objectTrackedChunks.computeIfAbsent(id, k -> new HashSet<>());

                        Set<VxSectionPos> toRelease = new HashSet<>(previouslyTracked);
                        toRelease.removeAll(required);
                        toRelease.forEach(this::releaseChunk);

                        Set<VxSectionPos> toRequest = new HashSet<>(required);
                        toRequest.removeAll(previouslyTracked);
                        toRequest.forEach(this::requestChunk);

                        previouslyTracked.clear();
                        previouslyTracked.addAll(required);
                    });
                });

        Map<VxSectionPos, VxTaskPriority> criticalChunks = new HashMap<>();
        for (VxAbstractBody obj : currentObjects) {
            var body = obj.getBody();
            if (body != null) {
                calculatePrioritiesForObject(body.getWorldSpaceBounds(), body.getLinearVelocity(), criticalChunks);
            }
        }

        criticalChunks.forEach((pos, priority) -> prioritizeChunk(pos, priority));

        Set<VxSectionPos> activeSet = criticalChunks.keySet();

        chunkStates.forEach((pos, state) -> {
            if (state.get() == STATE_READY_ACTIVE && !activeSet.contains(pos)) {
                deactivateChunk(pos);
            }
        });
        activeSet.forEach(this::activateChunk);
    }

    private Map<UUID, Set<VxSectionPos>> updateTrackerBatch(List<VxAbstractBody> objects) {
        Map<UUID, Set<VxSectionPos>> results = new HashMap<>();
        for(VxAbstractBody obj : objects) {
            UUID id = obj.getPhysicsId();
            int cooldown = objectUpdateCooldowns.getOrDefault(id, 0);
            var body = obj.getBody();
            if (body == null) {
                removeObjectTracking(id);
                continue;
            }

            Vec3 vel = body.getLinearVelocity();
            if (cooldown > 0 && vel.lengthSq() < MAX_SPEED_FOR_COOLDOWN_SQR) {
                objectUpdateCooldowns.put(id, cooldown - 1);
                results.put(id, objectTrackedChunks.getOrDefault(id, Collections.emptySet()));
                continue;
            }
            objectUpdateCooldowns.put(id, UPDATE_INTERVAL_TICKS);

            Set<VxSectionPos> required = new HashSet<>();
            calculateRequiredChunksForObject(body.getWorldSpaceBounds(), body.getLinearVelocity(), required);
            results.put(id, required);
        }
        return results;
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

    private void calculatePrioritiesForObject(ConstAaBox currentAabb, Vec3 velocity, Map<VxSectionPos, VxTaskPriority> outPriorities) {
        addChunksForAabbWithPriority(currentAabb, ACTIVATION_RADIUS_CHUNKS, VxTaskPriority.CRITICAL, outPriorities);

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

            addChunksForAabbWithPriority(predictedAabb, ACTIVATION_RADIUS_CHUNKS, VxTaskPriority.CRITICAL, outPriorities);
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

    private void addChunksForAabbWithPriority(ConstAaBox aabb, int radiusInChunks, VxTaskPriority priority, Map<VxSectionPos, VxTaskPriority> outPriorities) {
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
                    VxSectionPos pos = new VxSectionPos(x, y, z);
                    outPriorities.merge(pos, priority, (oldP, newP) -> newP.ordinal() > oldP.ordinal() ? newP : oldP);
                }
            }
        }
    }

    private AtomicInteger getState(VxSectionPos pos) {
        return chunkStates.computeIfAbsent(pos, p -> new AtomicInteger(STATE_UNLOADED));
    }

    private AtomicInteger getRebuildVersion(VxSectionPos pos) {
        return chunkRebuildVersions.computeIfAbsent(pos, p -> new AtomicInteger(0));
    }

    public boolean isReady(VxSectionPos pos) {
        int state = getState(pos).get();
        return state == STATE_READY_ACTIVE || state == STATE_READY_INACTIVE || state == STATE_AIR_CHUNK;
    }

    public boolean isPlaceholder(VxSectionPos pos) {
        return chunkIsPlaceholder.getOrDefault(pos, true);
    }

    public boolean isSectionReady(SectionPos sectionPos) {
        if (sectionPos == null) {
            return false;
        }
        return isReady(new VxSectionPos(sectionPos.x(), sectionPos.y(), sectionPos.z()));
    }

    public boolean isTerrainBody(int bodyId) {
        return bodyId >= 0 && chunkBodyIds.containsValue(bodyId);
    }

    public ServerLevel getLevel() {
        return level;
    }
}