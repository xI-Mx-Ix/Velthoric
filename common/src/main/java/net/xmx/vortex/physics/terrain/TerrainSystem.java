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
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
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
import java.util.concurrent.ConcurrentLinkedQueue;
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

    private final VxPhysicsWorld physicsWorld;
    private final ServerLevel level;
    private final TerrainGenerator terrainGenerator;
    private final TerrainShapeCache shapeCache;
    private final VxTerrainJobSystem jobSystem;

    private final ConcurrentHashMap<VxSectionPos, AtomicInteger> chunkStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<VxSectionPos, Integer> chunkBodyIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<VxSectionPos, ShapeRefC> chunkShapes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<VxSectionPos, Boolean> chunkIsPlaceholder = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<VxSectionPos, AtomicInteger> chunkRebuildVersions = new ConcurrentHashMap<>();

    private final Map<VxSectionPos, Integer> chunkReferenceCounts = new ConcurrentHashMap<>();
    private final Map<UUID, ObjectTerrainTracker> objectTrackers = new ConcurrentHashMap<>();
    private final Set<VxSectionPos> chunksToRebuild = ConcurrentHashMap.newKeySet();

    private final ConcurrentHashMap<VxSectionPos, SnapshotRequest> pendingSnapshotRequests = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();

    private static final int MAX_SNAPSHOTS_PER_TICK = 256;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private Thread workerThread;

    private record SnapshotRequest(int version, boolean isInitialBuild, VxTaskPriority priority, VxSectionPos pos) {}

    public TerrainSystem(VxPhysicsWorld physicsWorld, ServerLevel level) {
        this.physicsWorld = physicsWorld;
        this.level = level;
        this.shapeCache = new TerrainShapeCache(1024);
        this.terrainGenerator = new TerrainGenerator(this.shapeCache);
        this.jobSystem = new VxTerrainJobSystem();
    }

    public void initialize() {
        if (isInitialized.compareAndSet(false, true)) {
            this.workerThread = new Thread(this, "Vortex-Terrain-Tracker-" + level.dimension().location().getPath());
            this.workerThread.setDaemon(true);
            this.workerThread.start();
        }
    }

    public void shutdown() {
        if (isInitialized.compareAndSet(true, false)) {

            this.jobSystem.shutdown();

            if (workerThread != null) {
                workerThread.interrupt();
                try {
                    workerThread.join(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            mainThreadTasks.clear();
            physicsWorld.execute(() -> {
                new HashSet<>(chunkStates.keySet()).forEach(this::unloadChunkPhysics);
                chunkReferenceCounts.clear();
                objectTrackers.clear();
                shapeCache.clear();
                pendingSnapshotRequests.clear();
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
                schedulePendingSnapshotsForMainThreadProcessing();
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
        if (!isInitialized.get() || this.jobSystem.isShutdown()) return;
        chunksToRebuild.add(VxSectionPos.fromBlockPos(worldPos));
        physicsWorld.execute(() -> {
            BodyInterface bi = physicsWorld.getBodyInterface();
            if (bi == null) return;
            RVec3 min = new RVec3(worldPos.getX() - 2.0, worldPos.getY() - 2.0, worldPos.getZ() - 2.0);
            RVec3 max = new RVec3(worldPos.getX() + 3.0, worldPos.getY() + 3.0, worldPos.getZ() + 3.0);
            try (AaBox a = new AaBox(min, max); BroadPhaseLayerFilter bf = new BroadPhaseLayerFilter(); ObjectLayerFilter of = new ObjectLayerFilter()) {
                bi.activateBodiesInAaBox(a, bf, of);
            }
        });
    }

    private void processRebuildQueue() {
        if (chunksToRebuild.isEmpty()) return;
        Set<VxSectionPos> batch = new HashSet<>(chunksToRebuild);
        chunksToRebuild.clear();
        batch.forEach(pos -> scheduleRebuild(pos, VxTaskPriority.HIGH));
    }

    private void scheduleInitialBuild(VxSectionPos pos) {
        AtomicInteger state = getState(pos);
        if (state.get() == STATE_UNLOADED) {
            chunkIsPlaceholder.put(pos, true);
            if (state.compareAndSet(STATE_UNLOADED, STATE_LOADING_SCHEDULED)) {
                int version = getRebuildVersion(pos).incrementAndGet();
                requestSnapshot(new SnapshotRequest(version, true, VxTaskPriority.CRITICAL, pos));
            }
        }
    }

    private void scheduleRebuild(VxSectionPos pos, VxTaskPriority priority) {
        AtomicInteger state = getState(pos);
        int currentState = state.get();
        if (currentState == STATE_REMOVING || currentState == STATE_LOADING_SCHEDULED || currentState == STATE_GENERATING_SHAPE) {
            return;
        }
        if (state.compareAndSet(currentState, STATE_LOADING_SCHEDULED)) {
            int version = getRebuildVersion(pos).incrementAndGet();
            requestSnapshot(new SnapshotRequest(version, false, priority, pos));
        }
    }

    private void processSnapshot(@NotNull ChunkSnapshot snapshot, int snapshotVersion, boolean isInitialBuild) {
        VxSectionPos pos = snapshot.pos();
        AtomicInteger state = getState(pos);

        if (snapshotVersion < getRebuildVersion(pos).get()) return;

        int previousState = state.get();
        if (!state.compareAndSet(STATE_LOADING_SCHEDULED, STATE_GENERATING_SHAPE)) {
            if (state.get() != STATE_GENERATING_SHAPE) {
                state.set(previousState);
            }
            return;
        }

        final boolean wasActive = (previousState == STATE_READY_ACTIVE);

        CompletableFuture.supplyAsync(() -> isInitialBuild ? terrainGenerator.generatePlaceholderShape(snapshot) : terrainGenerator.generateShape(level, snapshot), this.jobSystem.getExecutor())
                .thenAcceptAsync(generatedShape -> {
                    if (snapshotVersion < getRebuildVersion(pos).get() || getState(pos).get() == STATE_REMOVING) {
                        if (generatedShape != null) generatedShape.close();
                        if (getState(pos).get() != STATE_REMOVING) state.set(previousState);
                        return;
                    }

                    BodyInterface bodyInterface = physicsWorld.getBodyInterface();
                    if (bodyInterface == null) {
                        if (generatedShape != null) generatedShape.close();
                        state.set(STATE_UNLOADED);
                        return;
                    }

                    if (generatedShape == null) {
                        if (chunkBodyIds.containsKey(pos)) {
                            removeBodyAndShape(pos, bodyInterface);
                        }
                        state.set(previousState);
                        return;
                    }

                    Integer bodyId = chunkBodyIds.get(pos);
                    if (bodyId != null) {
                        bodyInterface.setShape(bodyId, generatedShape, true, EActivation.Activate);
                        ShapeRefC oldShape = chunkShapes.put(pos, generatedShape);
                        if (oldShape != null && !oldShape.equals(generatedShape)) oldShape.close();
                    } else {
                        RVec3 position = new RVec3(pos.getOrigin().getX(), pos.getOrigin().getY(), pos.getOrigin().getZ());
                        try (BodyCreationSettings bcs = new BodyCreationSettings(generatedShape, position, Quat.sIdentity(), EMotionType.Static, VxPhysicsWorld.Layers.STATIC)) {
                            Body body = bodyInterface.createBody(bcs);
                            if (body != null) {
                                chunkBodyIds.put(pos, body.getId());
                                chunkShapes.put(pos, generatedShape);
                                body.close();
                            } else {
                                VxMainClass.LOGGER.error("Failed to create terrain body for chunk {}", pos);
                                generatedShape.close();
                            }
                        }
                    }

                    chunkIsPlaceholder.put(pos, isInitialBuild);
                    state.set(wasActive ? STATE_READY_ACTIVE : STATE_READY_INACTIVE);

                    if (wasActive) activateChunk(pos);

                }, physicsWorld)
                .exceptionally(ex -> {
                    VxMainClass.LOGGER.error("Exception during terrain shape generation for {}", pos, ex);
                    state.set(previousState);
                    return null;
                });
    }

    private void activateChunk(VxSectionPos pos) {
        if(isReady(pos) && !chunkBodyIds.containsKey(pos)) {
            VxMainClass.LOGGER.warn("Detected stuck terrain chunk {} in ready state with no body. Forcing high-prio rebuild.", pos);
            scheduleRebuild(pos, VxTaskPriority.HIGH);
            return;
        }

        if (chunkBodyIds.containsKey(pos) && getState(pos).compareAndSet(STATE_READY_INACTIVE, STATE_READY_ACTIVE)) {
            physicsWorld.execute(() -> {
                BodyInterface bodyInterface = physicsWorld.getBodyInterface();
                Integer bodyId = chunkBodyIds.get(pos);
                if (bodyInterface != null && bodyId != null && !bodyInterface.isAdded(bodyId)) {
                    bodyInterface.addBody(bodyId, EActivation.Activate);
                }
            });
        }

        if (isPlaceholder(pos) && chunkBodyIds.containsKey(pos)) {
            scheduleRebuild(pos, VxTaskPriority.CRITICAL);
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

    private void removeBodyAndShape(VxSectionPos pos, BodyInterface bodyInterface) {
        Integer bodyId = chunkBodyIds.remove(pos);
        if (bodyId != null) {
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

    private void resetStateAfterFailedSnapshot(VxSectionPos pos) {
        AtomicInteger state = getState(pos);
        state.compareAndSet(STATE_LOADING_SCHEDULED, STATE_READY_INACTIVE);
    }

    public void loadChunkFromVanilla(@NotNull LevelChunk chunk) {
        if (!isInitialized.get() || this.jobSystem.isShutdown()) return;
        this.jobSystem.submit(() -> {
            ChunkPos chunkPos = chunk.getPos();
            for (int y = level.getMinSection(); y < level.getMaxSection(); ++y) {
                VxSectionPos vPos = new VxSectionPos(chunkPos.x, y, chunkPos.z);
                if (vPos.isWithinWorldHeight(this.level)) {
                    getState(vPos);
                    getRebuildVersion(vPos);
                    chunkReferenceCounts.putIfAbsent(vPos, 0);
                    scheduleInitialBuild(vPos);
                }
            }
        });
    }

    public void onChunkUnloaded(@NotNull ChunkPos chunkPos) {
        if (!isInitialized.get() || this.jobSystem.isShutdown()) return;
        this.jobSystem.submit(() -> {
            for (int y = level.getMinSection(); y < level.getMaxSection(); y++) {
                unloadChunkPhysics(new VxSectionPos(chunkPos.x, y, chunkPos.z));
            }
        });
    }

    private void loadChunkPhysics(VxSectionPos pos) {
        if (!isInitialized.get()) return;
        getState(pos);
        getRebuildVersion(pos);
        scheduleInitialBuild(pos);
    }

    private void unloadChunkPhysics(VxSectionPos pos) {
        AtomicInteger state = getState(pos);
        int oldState = state.getAndSet(STATE_REMOVING);
        if (oldState == STATE_REMOVING) return;

        getRebuildVersion(pos).incrementAndGet();

        physicsWorld.execute(() -> {
            BodyInterface bi = physicsWorld.getBodyInterface();
            if(bi != null) {
                removeBodyAndShape(pos, bi);
            }

            chunkStates.remove(pos);
            chunkIsPlaceholder.remove(pos);
            chunkRebuildVersions.remove(pos);
            chunkReferenceCounts.remove(pos);
            chunksToRebuild.remove(pos);
            pendingSnapshotRequests.remove(pos);
        });
    }

    public void requestChunk(VxSectionPos pos) {
        if (chunkReferenceCounts.compute(pos, (p, count) -> (count == null) ? 1 : count + 1) == 1) {
            loadChunkPhysics(pos);
        }
    }

    public void releaseChunk(VxSectionPos pos) {
        Integer newCount = chunkReferenceCounts.computeIfPresent(pos, (p, count) -> count > 1 ? count - 1 : null);
        if (newCount == null && chunkReferenceCounts.containsKey(pos)) {
            unloadChunkPhysics(pos);
        }
    }

    public void prioritizeChunk(VxSectionPos pos, VxTaskPriority priority) {
        if (isPlaceholder(pos) || !isReady(pos)) {
            scheduleRebuild(pos, priority);
        }
    }

    private void updateTrackers() {
        Set<UUID> currentObjectIds = physicsWorld.getObjectManager().getManagedObjects().keySet();
        objectTrackers.keySet().removeIf(id -> {
            if (!currentObjectIds.contains(id)) {
                Optional.ofNullable(objectTrackers.get(id)).ifPresent(ObjectTerrainTracker::releaseAll);
                return true;
            }
            return false;
        });

        for (IPhysicsObject obj : physicsWorld.getObjectManager().getManagedObjects().values()) {
            if (obj.isPhysicsInitialized() && obj.getBodyId() != 0) {
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
                .map(tracker -> CompletableFuture.supplyAsync(tracker::update, this.jobSystem.getExecutor()))
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

    private void requestSnapshot(@NotNull SnapshotRequest request) {
        pendingSnapshotRequests.compute(request.pos(), (p, existing) -> {
            if (existing == null || request.priority().ordinal() > existing.priority().ordinal() || (request.priority() == existing.priority() && request.version() > existing.version())) {
                return request;
            }
            return existing;
        });
    }

    private void schedulePendingSnapshotsForMainThreadProcessing() {
        if (pendingSnapshotRequests.isEmpty()) return;

        List<SnapshotRequest> sortedRequests = new ArrayList<>(pendingSnapshotRequests.values());
        sortedRequests.sort(Comparator.comparing(SnapshotRequest::priority).reversed().thenComparing(Comparator.comparing(SnapshotRequest::version).reversed()));

        int batchSize = Math.min(sortedRequests.size(), MAX_SNAPSHOTS_PER_TICK);
        Map<VxSectionPos, SnapshotRequest> currentBatch = new LinkedHashMap<>();

        for(int i = 0; i < batchSize; i++) {
            SnapshotRequest request = sortedRequests.get(i);
            if(pendingSnapshotRequests.remove(request.pos(), request)) {
                currentBatch.put(request.pos(), request);
            }
        }
        if (currentBatch.isEmpty()) return;

        mainThreadTasks.offer(() -> {
            Map<VxSectionPos, ChunkSnapshot> snapshots = new HashMap<>();
            for (SnapshotRequest request : currentBatch.values()) {
                try {
                    snapshots.put(request.pos(), ChunkSnapshot.snapshot(level, request.pos()));
                } catch (Exception e) {
                    VxMainClass.LOGGER.error("Failed to create snapshot for chunk {} on main thread. Skipping.", request.pos(), e);
                    resetStateAfterFailedSnapshot(request.pos());
                }
            }
            if (snapshots.isEmpty()) return;
            this.jobSystem.submit(() -> {
                for (Map.Entry<VxSectionPos, ChunkSnapshot> entry : snapshots.entrySet()) {
                    SnapshotRequest request = currentBatch.get(entry.getKey());
                    if (request != null) {
                        processSnapshot(entry.getValue(), request.version(), request.isInitialBuild());
                    }
                }
            });
        });
    }

    public void processPendingSnapshotsOnMainThread() {
        Runnable task;
        while ((task = mainThreadTasks.poll()) != null) task.run();
    }

    private AtomicInteger getState(VxSectionPos pos) {
        return chunkStates.computeIfAbsent(pos, p -> new AtomicInteger(STATE_UNLOADED));
    }

    private AtomicInteger getRebuildVersion(VxSectionPos pos) {
        return chunkRebuildVersions.computeIfAbsent(pos, p -> new AtomicInteger(0));
    }

    public boolean isReady(VxSectionPos pos) {
        int state = getState(pos).get();
        return state == STATE_READY_ACTIVE || state == STATE_READY_INACTIVE;
    }

    public boolean isPlaceholder(VxSectionPos pos) {
        return chunkIsPlaceholder.getOrDefault(pos, true);
    }

    public boolean isSectionReady(SectionPos sectionPos) {
        if (sectionPos == null) return false;
        return isReady(new VxSectionPos(sectionPos.x(), sectionPos.y(), sectionPos.z()));
    }

    public boolean hasBody(VxSectionPos pos) {
        return chunkBodyIds.containsKey(pos);
    }

    public boolean isTerrainBody(int bodyId) {
        if (bodyId < 0) return false;
        return chunkBodyIds.containsValue(bodyId);
    }

    public ServerLevel getLevel() {
        return level;
    }

    public boolean isKnownSection(VxSectionPos pos) {
        return chunkStates.containsKey(pos);
    }
}