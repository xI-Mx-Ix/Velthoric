/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
import com.github.stephengold.joltjni.readonly.ConstBody;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.terrain.cache.VxTerrainShapeCache;
import net.xmx.velthoric.physics.terrain.chunk.VxChunkSnapshot;
import net.xmx.velthoric.physics.terrain.job.VxTaskPriority;
import net.xmx.velthoric.physics.terrain.job.VxTerrainJobSystem;
import net.xmx.velthoric.natives.VxLayers;
import net.xmx.velthoric.physics.terrain.persistence.VxTerrainStorage;
import net.xmx.velthoric.physics.terrain.shape.VxTerrainGenerator;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Manages the lifecycle and interaction of terrain physics bodies in the world.
 * <p>
 * This system is responsible for tracking dynamic physics objects, determining which
 * terrain chunks need to be loaded, activating/deactivating them, and handling
 * chunk updates due to block changes. It operates largely on a dedicated worker
 * thread to minimize impact on the main server thread.
 *
 * @author xI-Mx-Ix
 */
public class VxTerrainSystem implements Runnable {

    // --- State Machine Constants for Chunks ---
    private static final int STATE_UNLOADED = 0;
    private static final int STATE_LOADING_SCHEDULED = 1;
    private static final int STATE_GENERATING_SHAPE = 2;
    private static final int STATE_READY_INACTIVE = 3;
    private static final int STATE_READY_ACTIVE = 4;
    private static final int STATE_REMOVING = 5;
    private static final int STATE_AIR_CHUNK = 6;

    // --- System Dependencies ---
    private final VxPhysicsWorld physicsWorld;
    private final ServerLevel level;
    private final VxTerrainGenerator terrainGenerator;
    private final VxTerrainShapeCache shapeCache;
    private final VxTerrainStorage terrainStorage;
    private final VxTerrainJobSystem jobSystem;
    private final Thread workerThread;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    // --- State Management ---
    private final VxChunkDataStore chunkDataStore = new VxChunkDataStore();
    private final Set<VxSectionPos> chunksToRebuild = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<VxSectionPos>> objectTrackedChunks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> objectUpdateCooldowns = new ConcurrentHashMap<>();

    private static final ThreadLocal<VxUpdateContext> updateContext = ThreadLocal.withInitial(VxUpdateContext::new);

    // --- Configuration ---
    private static final int UPDATE_INTERVAL_TICKS = 10;
    private static final int PRELOAD_RADIUS_CHUNKS = 3;
    private static final int ACTIVATION_RADIUS_CHUNKS = 1;
    private static final float PREDICTION_SECONDS = 0.5f;

    private int objectUpdateIndex = 0;
    private static final int OBJECT_PRELOAD_UPDATE_STRIDE = 250;
    private static final int OBJECT_ACTIVATION_BATCH_SIZE = 100;

    public VxTerrainSystem(VxPhysicsWorld physicsWorld, ServerLevel level) {
        this.physicsWorld = physicsWorld;
        this.level = level;
        this.shapeCache = new VxTerrainShapeCache(1024);
        this.terrainStorage = new VxTerrainStorage(this.level);
        this.terrainGenerator = new VxTerrainGenerator(this.shapeCache, this.terrainStorage);
        this.jobSystem = new VxTerrainJobSystem();
        this.workerThread = new Thread(this, "Velthoric Terrain Tracker - " + level.dimension().location().getPath());
        this.workerThread.setDaemon(true);
    }

    /**
     * Initializes the terrain system and starts its worker thread.
     */
    public void initialize() {
        if (isInitialized.compareAndSet(false, true)) {
            this.terrainStorage.initialize();
            this.workerThread.start();
        }
    }

    /**
     * Shuts down the terrain system, its worker thread, and cleans up resources.
     */
    public void shutdown() {
        if (isInitialized.compareAndSet(true, false)) {
            jobSystem.shutdown();
            workerThread.interrupt();

            VxMainClass.LOGGER.debug("Shutting down Terrain Tracker for '{}'. Waiting up to 30 seconds for worker thread to exit...", level.dimension().location());
            try {
                workerThread.join(30000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                VxMainClass.LOGGER.warn("Interrupted while waiting for terrain tracker thread to stop.");
            }

            if (workerThread.isAlive()) {
                StringBuilder stackTraceBuilder = new StringBuilder();
                stackTraceBuilder.append("Stack trace of non-terminating thread '").append(workerThread.getName()).append("':\n");
                for (StackTraceElement ste : workerThread.getStackTrace()) {
                    stackTraceBuilder.append("\tat ").append(ste).append("\n");
                }
                VxMainClass.LOGGER.error("Terrain tracker thread did not terminate gracefully.\n{}", stackTraceBuilder.toString());
            }

            // Final cleanup on the calling thread
            BodyInterface bi = physicsWorld.getBodyInterface();
            if (bi != null) {
                chunkDataStore.getActiveIndices().forEach(index -> removeBodyAndShape(index, bi));
            }
            chunkDataStore.clear();
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

    /**
     * Saves all terrain region files that have been modified.
     */
    public void saveDirtyRegions() {
        if (terrainStorage != null) {
            terrainStorage.saveDirtyRegions();
        }
    }

    /**
     * Called when a block changes in the world. Schedules a rebuild if the chunk is active.
     *
     * @param worldPos The position of the changed block.
     * @param oldState The old block state.
     * @param newState The new block state.
     */
    public void onBlockUpdate(BlockPos worldPos, BlockState oldState, BlockState newState) {
        if (!isInitialized.get()) return;

        VxSectionPos pos = VxSectionPos.fromBlockPos(worldPos.immutable());
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index == null) return;

        int currentState = chunkDataStore.states[index];
        if (currentState == STATE_READY_ACTIVE || currentState == STATE_READY_INACTIVE || currentState == STATE_AIR_CHUNK) {
            chunksToRebuild.add(pos);
        }

        // Wake up any physics bodies sleeping near the block change.
        physicsWorld.execute(() -> {
            BodyInterface bi = physicsWorld.getBodyInterface();
            if (bi == null) return;
            VxUpdateContext ctx = updateContext.get();
            ctx.aabox_1.setMin(new Vec3(worldPos.getX() - 2f, worldPos.getY() - 2f, worldPos.getZ() - 2f));
            ctx.aabox_1.setMax(new Vec3(worldPos.getX() + 3f, worldPos.getY() + 3f, worldPos.getZ() + 3f));
            bi.activateBodiesInAaBox(ctx.aabox_1, ctx.bplFilter, ctx.olFilter);
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
        if (!isInitialized.get() || jobSystem.isShutdown()) {
            return;
        }

        int currentState = chunkDataStore.states[index];
        if (currentState == STATE_REMOVING || currentState == STATE_LOADING_SCHEDULED || currentState == STATE_GENERATING_SHAPE) {
            return;
        }

        // Atomically set state to prevent race conditions
        if (chunkDataStore.states[index] == currentState) {
            chunkDataStore.states[index] = STATE_LOADING_SCHEDULED;
            final int version = ++chunkDataStore.rebuildVersions[index];

            // Snapshotting must be done on the main thread
            level.getServer().execute(() -> {
                if (version < chunkDataStore.rebuildVersions[index] || chunkDataStore.states[index] != STATE_LOADING_SCHEDULED) {
                    if (chunkDataStore.states[index] == STATE_LOADING_SCHEDULED) chunkDataStore.states[index] = currentState;
                    return;
                }

                LevelChunk chunk = level.getChunkSource().getChunk(pos.x(), pos.z(), false);
                if (chunk == null) {
                    chunkDataStore.states[index] = STATE_UNLOADED;
                    return;
                }

                VxChunkSnapshot snapshot = VxChunkSnapshot.snapshotFromChunk(level, chunk, pos);
                if (chunkDataStore.states[index] == STATE_LOADING_SCHEDULED) {
                    chunkDataStore.states[index] = STATE_GENERATING_SHAPE;
                    jobSystem.submit(() -> processShapeGenerationOnWorker(pos, index, version, snapshot, isInitialBuild, currentState));
                }
            });
        }
    }

    private void processShapeGenerationOnWorker(VxSectionPos pos, int index, int version, VxChunkSnapshot snapshot, boolean isInitialBuild, int previousState) {
        if (version < chunkDataStore.rebuildVersions[index]) {
            if (chunkDataStore.states[index] == STATE_GENERATING_SHAPE) chunkDataStore.states[index] = previousState;
            return;
        }

        try {
            if (!isInitialized.get()) return;
            // The generated shape is a new resource that this thread now owns.
            ShapeRefC generatedShape = terrainGenerator.generateShape(level, snapshot);
            // Schedule the application of the shape on the physics thread.
            // Ownership of 'generatedShape' is transferred to the 'applyGeneratedShape' lambda.
            physicsWorld.execute(() -> applyGeneratedShape(pos, index, version, generatedShape, isInitialBuild));
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Exception during terrain shape generation for {}", pos, e);
            chunkDataStore.states[index] = STATE_UNLOADED;
        }
    }

    private void applyGeneratedShape(VxSectionPos pos, int index, int version, @Nullable ShapeRefC shape, boolean isInitialBuild) {
        // This method takes ownership of the 'shape' parameter and MUST close it before returning.
        try {
            boolean wasActive = chunkDataStore.states[index] == STATE_READY_ACTIVE;

            if (version < chunkDataStore.rebuildVersions[index] || chunkDataStore.states[index] == STATE_REMOVING) {
                if (chunkDataStore.states[index] != STATE_REMOVING) chunkDataStore.states[index] = STATE_UNLOADED;
                return;
            }

            BodyInterface bodyInterface = physicsWorld.getBodyInterface();
            if (bodyInterface == null) {
                chunkDataStore.states[index] = STATE_UNLOADED;
                return;
            }

            int bodyId = chunkDataStore.bodyIds[index];

            if (shape != null) {
                if (bodyId != VxChunkDataStore.UNUSED_BODY_ID) { // Body exists, update its shape
                    bodyInterface.setShape(bodyId, shape, true, EActivation.DontActivate);
                } else { // Body does not exist, create it
                    RVec3 position = new RVec3(pos.getOrigin().getX(), pos.getOrigin().getY(), pos.getOrigin().getZ());
                    try (BodyCreationSettings bcs = new BodyCreationSettings(shape, position, Quat.sIdentity(), EMotionType.Static, VxLayers.TERRAIN)) {
                        bcs.setEnhancedInternalEdgeRemoval(true);
                        // createBody transfers ownership of the shape's native object to the new body.
                        Body body = bodyInterface.createBody(bcs);
                        if (body != null) {
                            chunkDataStore.bodyIds[index] = body.getId();
                        } else {
                            VxMainClass.LOGGER.error("Failed to create terrain body for chunk {}", pos);
                            chunkDataStore.states[index] = STATE_UNLOADED;
                        }
                    }
                }
                // The shape's native object is now owned by the body. Store our own independent reference.
                chunkDataStore.setShape(index, shape.getPtr().toRefC());
                chunkDataStore.states[index] = wasActive ? STATE_READY_ACTIVE : STATE_READY_INACTIVE;
            } else { // No shape generated
                if (bodyId != VxChunkDataStore.UNUSED_BODY_ID) {
                    removeBodyAndShape(index, bodyInterface);
                }
                chunkDataStore.setShape(index, null);
                chunkDataStore.states[index] = STATE_AIR_CHUNK;
            }
            chunkDataStore.isPlaceholder[index] = isInitialBuild;
            if (wasActive) {
                activateChunk(pos, index);
            }
        } finally {
            if (shape != null) {
                shape.close(); // Close the temporary reference passed to this method.
            }
        }
    }

    private void activateChunk(VxSectionPos pos, int index) {
        if (chunkDataStore.states[index] == STATE_AIR_CHUNK) return;

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

    /**
     * Called when an object tracker needs a chunk. Increments the reference count.
     */
    public void requestChunk(VxSectionPos pos) {
        int index = chunkDataStore.addChunk(pos);
        if (++chunkDataStore.referenceCounts[index] == 1) {
            scheduleShapeGeneration(pos, index, true, VxTaskPriority.HIGH);
        }
    }

    /**
     * Called when an object tracker no longer needs a chunk. Decrements the reference count.
     * If the count reaches zero, the chunk is unloaded.
     */
    public void releaseChunk(VxSectionPos pos) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index != null && --chunkDataStore.referenceCounts[index] == 0) {
            unloadChunkPhysicsInternal(pos);
        }
    }

    /**
     * Increases the priority of a chunk's generation task.
     */
    public void prioritizeChunk(VxSectionPos pos, VxTaskPriority priority) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index != null && (isPlaceholder(pos, index) || !isReady(pos, index))) {
            scheduleShapeGeneration(pos, index, false, priority);
        }
    }

    /**
     * The main loop of the worker thread. Updates which chunks are loaded and active based on object positions.
     */
    private void updateTrackers() {
        if (!isInitialized.get() || jobSystem.isShutdown()) {
            return;
        }

        List<VxBody> currentObjects = new ArrayList<>(physicsWorld.getObjectManager().getAllObjects());
        Set<UUID> currentObjectIds = currentObjects.stream().map(VxBody::getPhysicsId).collect(Collectors.toSet());

        // Clean up trackers for objects that no longer exist
        objectTrackedChunks.keySet().removeIf(id -> {
            if (!currentObjectIds.contains(id)) {
                removeObjectTracking(id);
                return true;
            }
            return false;
        });

        // If no objects, deactivate all chunks
        if (currentObjects.isEmpty()) {
            for (int index : chunkDataStore.getActiveIndices()) {
                if (chunkDataStore.states[index] == STATE_READY_ACTIVE) {
                    deactivateChunk(index);
                }
            }
            return;
        }

        // Update preloading for a subset of objects each tick
        int objectsToUpdate = Math.min(currentObjects.size(), OBJECT_PRELOAD_UPDATE_STRIDE);
        Map<Integer, VxBody> objectsToPreload = new HashMap<>();

        for (int i = 0; i < objectsToUpdate; ++i) {
            if (objectUpdateIndex >= currentObjects.size()) {
                objectUpdateIndex = 0;
            }
            VxBody obj = currentObjects.get(objectUpdateIndex++);
            if (obj.getDataStoreIndex() == -1 || obj.getBodyId() == 0) continue;

            int cooldown = objectUpdateCooldowns.getOrDefault(obj.getPhysicsId(), 0);
            if (cooldown > 0) { // Simple cooldown to reduce frequent updates for static objects
                objectUpdateCooldowns.put(obj.getPhysicsId(), cooldown - 1);
            } else {
                objectsToPreload.put(obj.getBodyId(), obj);
            }
        }

        if (!objectsToPreload.isEmpty()) {
            int[] preloadBodyIds = objectsToPreload.keySet().stream().mapToInt(Integer::intValue).toArray();
            try (BodyLockMultiRead lock = new BodyLockMultiRead(physicsWorld.getPhysicsSystem().getBodyLockInterfaceNoLock(), preloadBodyIds)) {
                for (int i = 0; i < preloadBodyIds.length; i++) {
                    VxBody obj = objectsToPreload.get(preloadBodyIds[i]);
                    objectUpdateCooldowns.put(obj.getPhysicsId(), UPDATE_INTERVAL_TICKS);
                    processPreloadForLockedBody(obj, lock.getBody(i));
                }
            }
        }

        // Determine the complete set of chunks that need to be active for ALL objects
        List<List<VxBody>> batches = partitionList(currentObjects, OBJECT_ACTIVATION_BATCH_SIZE);
        List<CompletableFuture<Set<VxSectionPos>>> futures = new ArrayList<>();

        for (List<VxBody> batch : batches) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                Set<VxSectionPos> required = new HashSet<>();
                if (batch.isEmpty()) return required;
                int[] batchBodyIds = batch.stream().mapToInt(VxBody::getBodyId).filter(id -> id != 0).toArray();
                if (batchBodyIds.length == 0) return required;

                try (BodyLockMultiRead batchLock = new BodyLockMultiRead(physicsWorld.getPhysicsSystem().getBodyLockInterfaceNoLock(), batchBodyIds)) {
                    for (int i = 0; i < batchBodyIds.length; i++) {
                        ConstBody body = batchLock.getBody(i);
                        if (body != null) {
                            ConstAaBox bounds = body.getWorldSpaceBounds();
                            calculateRequiredChunks(bounds.getMin(), bounds.getMax(), body.getLinearVelocity(), ACTIVATION_RADIUS_CHUNKS, required);
                        }
                    }
                }
                return required;
            }, jobSystem.getExecutor()));
        }

        Set<VxSectionPos> activeSet = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        // Activate required chunks and deactivate unneeded ones
        activeSet.forEach(pos -> prioritizeChunk(pos, VxTaskPriority.CRITICAL));
        chunkDataStore.getActiveIndices().forEach(index -> {
            VxSectionPos pos = chunkDataStore.getPosForIndex(index);
            if (pos != null) {
                if (chunkDataStore.states[index] == STATE_READY_ACTIVE && !activeSet.contains(pos)) {
                    deactivateChunk(index);
                }
            }
        });
        activeSet.forEach(pos -> {
            Integer index = chunkDataStore.getIndexForPos(pos);
            if (index != null) activateChunk(pos, index);
        });
    }

    private void processPreloadForLockedBody(VxBody obj, ConstBody body) {
        UUID id = obj.getPhysicsId();
        if (body == null) {
            removeObjectTracking(id);
            return;
        }

        Set<VxSectionPos> required = new HashSet<>();
        ConstAaBox bounds = body.getWorldSpaceBounds();
        calculateRequiredChunks(bounds.getMin(), bounds.getMax(), body.getLinearVelocity(), PRELOAD_RADIUS_CHUNKS, required);

        Set<VxSectionPos> previouslyTracked = objectTrackedChunks.computeIfAbsent(id, k -> new HashSet<>());
        previouslyTracked.removeIf(pos -> {
            if (!required.contains(pos)) {
                releaseChunk(pos);
                return true;
            }
            return false;
        });
        required.forEach(pos -> {
            if (previouslyTracked.add(pos)) {
                requestChunk(pos);
            }
        });
    }

    private void removeObjectTracking(UUID id) {
        Set<VxSectionPos> chunksToRelease = objectTrackedChunks.remove(id);
        if (chunksToRelease != null) {
            chunksToRelease.forEach(this::releaseChunk);
        }
        objectUpdateCooldowns.remove(id);
    }

    private void calculateRequiredChunks(Vec3 min, Vec3 max, Vec3 velocity, int radius, Set<VxSectionPos> outChunks) {
        addChunksForBounds(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ(), radius, outChunks);

        double predMinX = min.getX() + velocity.getX() * PREDICTION_SECONDS;
        double predMinY = min.getY() + velocity.getY() * PREDICTION_SECONDS;
        double predMinZ = min.getZ() + velocity.getZ() * PREDICTION_SECONDS;
        double predMaxX = max.getX() + velocity.getX() * PREDICTION_SECONDS;
        double predMaxY = max.getY() + velocity.getY() * PREDICTION_SECONDS;
        double predMaxZ = max.getZ() + velocity.getZ() * PREDICTION_SECONDS;

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
        if (list.isEmpty()) return Collections.emptyList();
        int numBatches = (list.size() + batchSize - 1) / batchSize;
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
        if (sectionPos == null) return false;
        return isReady(new VxSectionPos(sectionPos.x(), sectionPos.y(), sectionPos.z()));
    }

    public ServerLevel getLevel() {
        return level;
    }
}