package net.xmx.vortex.physics.terrain;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.object.physicsobject.VxAbstractBody;
import net.xmx.vortex.physics.terrain.cache.TerrainShapeCache;
import net.xmx.vortex.physics.terrain.job.VxTaskPriority;
import net.xmx.vortex.physics.terrain.job.VxTerrainJobSystem;
import net.xmx.vortex.physics.terrain.loader.ChunkSnapshot;
import net.xmx.vortex.physics.terrain.loader.TerrainGenerator;
import net.xmx.vortex.physics.terrain.model.VxSectionPos;
import net.xmx.vortex.physics.terrain.tracker.ObjectTerrainTracker;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class TerrainSystem implements Runnable {

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
    private final VxTerrainJobSystem jobSystem;
    private final Thread workerThread;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    private final ConcurrentHashMap<VxSectionPos, AtomicInteger> chunkStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<VxSectionPos, Integer> chunkBodyIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<VxSectionPos, ShapeRefC> chunkShapes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<VxSectionPos, Boolean> chunkIsPlaceholder = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<VxSectionPos, AtomicInteger> chunkRebuildVersions = new ConcurrentHashMap<>();
    private final Map<VxSectionPos, Integer> chunkReferenceCounts = new ConcurrentHashMap<>();
    private final Map<UUID, ObjectTerrainTracker> objectTrackers = new ConcurrentHashMap<>();
    private final Set<VxSectionPos> chunksToRebuild = ConcurrentHashMap.newKeySet();

    public TerrainSystem(VxPhysicsWorld physicsWorld, ServerLevel level) {
        this.physicsWorld = physicsWorld;
        this.level = level;
        this.shapeCache = new TerrainShapeCache(1024);
        this.terrainGenerator = new TerrainGenerator(this.shapeCache);
        this.jobSystem = new VxTerrainJobSystem();
        this.workerThread = new Thread(this, "Vortex-Terrain-Tracker-" + level.dimension().location().getPath());
        this.workerThread.setDaemon(true);
    }

    public void initialize() {
        if (isInitialized.compareAndSet(false, true)) {
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
            physicsWorld.execute(() -> {
                new HashSet<>(chunkStates.keySet()).forEach(this::unloadChunkPhysicsInternal);
                chunkReferenceCounts.clear();
                objectTrackers.clear();
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

    public void onBlockUpdate(BlockPos worldPos, BlockState oldState, BlockState newState) {
        if (!isInitialized.get() || jobSystem.isShutdown()) {
            return;
        }

        chunksToRebuild.add(VxSectionPos.fromBlockPos(worldPos.immutable()));

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
        AtomicInteger state = getState(pos);
        int currentState = state.get();

        if (currentState == STATE_REMOVING || currentState == STATE_LOADING_SCHEDULED || currentState == STATE_GENERATING_SHAPE) {
            return;
        }

        if (state.compareAndSet(currentState, STATE_LOADING_SCHEDULED)) {
            final int version = getRebuildVersion(pos).incrementAndGet();
            jobSystem.submit(() -> processChunkAndGenerateShape(pos, version, isInitialBuild, currentState));
        }
    }

    private void processChunkAndGenerateShape(VxSectionPos pos, int version, boolean isInitialBuild, int previousState) {
        if (version < getRebuildVersion(pos).get()) {
            getState(pos).compareAndSet(STATE_LOADING_SCHEDULED, previousState);
            return;
        }

        LevelChunk chunk = level.getChunkSource().getChunk(pos.x(), pos.z(), false);
        if (chunk == null) {
            getState(pos).compareAndSet(STATE_LOADING_SCHEDULED, STATE_UNLOADED);
            return;
        }

        if (!getState(pos).compareAndSet(STATE_LOADING_SCHEDULED, STATE_GENERATING_SHAPE)) {
            return;
        }

        try {
            ChunkSnapshot snapshot = ChunkSnapshot.snapshotFromChunk(level, chunk, pos);
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

        if (shape == null) {
            removeBodyAndShape(pos, bodyInterface);
            state.set(STATE_AIR_CHUNK);
            return;
        }

        Integer bodyId = chunkBodyIds.get(pos);
        if (bodyId != null) {
            bodyInterface.setShape(bodyId, shape, true, EActivation.Activate);
            ShapeRefC oldShape = chunkShapes.put(pos, shape);
            if (oldShape != null && !oldShape.equals(shape)) {
                oldShape.close();
            }
        } else {
            RVec3 position = new RVec3(pos.getOrigin().getX(), pos.getOrigin().getY(), pos.getOrigin().getZ());
            try (BodyCreationSettings bcs = new BodyCreationSettings(shape, position, Quat.sIdentity(), EMotionType.Static, VxPhysicsWorld.Layers.STATIC)) {
                Body body = bodyInterface.createBody(bcs);
                if (body != null) {
                    chunkBodyIds.put(pos, body.getId());
                    chunkShapes.put(pos, shape);
                } else {
                    VxMainClass.LOGGER.error("Failed to create terrain body for chunk {}", pos);
                    shape.close();
                }
            }
        }

        chunkIsPlaceholder.put(pos, isInitialBuild);
        state.set(wasActive ? STATE_READY_ACTIVE : STATE_READY_INACTIVE);

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
            chunkReferenceCounts.remove(pos);
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
        Set<UUID> currentObjectIds = physicsWorld.getObjectManager().getObjectContainer().getAllObjects().stream()
                .map(VxAbstractBody::getPhysicsId)
                .collect(Collectors.toSet());

        objectTrackers.keySet().removeIf(id -> {
            if (!currentObjectIds.contains(id)) {
                Optional.ofNullable(objectTrackers.get(id)).ifPresent(ObjectTerrainTracker::releaseAll);
                return true;
            }
            return false;
        });

        for (VxAbstractBody obj : physicsWorld.getObjectManager().getObjectContainer().getAllObjects()) {
            if (obj.getBodyId() != 0) {
                objectTrackers.computeIfAbsent(obj.getPhysicsId(), id -> new ObjectTerrainTracker(obj, this));
            } else {
                Optional.ofNullable(objectTrackers.remove(obj.getPhysicsId())).ifPresent(ObjectTerrainTracker::releaseAll);
            }
        }

        if (objectTrackers.isEmpty()) {
            Set<VxSectionPos> currentActive = chunkStates.entrySet().stream()
                    .filter(e -> e.getValue().get() == STATE_READY_ACTIVE)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            currentActive.forEach(this::deactivateChunk);
            return;
        }

        List<CompletableFuture<Set<VxSectionPos>>> futures = objectTrackers.values().stream()
                .map(tracker -> CompletableFuture.supplyAsync(tracker::update, jobSystem.getExecutor()))
                .toList();

        Set<VxSectionPos> allRequiredActiveChunks = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        chunkStates.forEach((pos, state) -> {
            if (state.get() == STATE_READY_ACTIVE && !allRequiredActiveChunks.contains(pos)) {
                deactivateChunk(pos);
            }
        });

        allRequiredActiveChunks.forEach(this::activateChunk);
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

    public boolean isKnownSection(VxSectionPos pos) {
        return chunkStates.containsKey(pos);
    }
}