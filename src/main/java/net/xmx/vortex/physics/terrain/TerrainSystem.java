package net.xmx.vortex.physics.terrain;

import com.github.stephengold.joltjni.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.terrain.cache.TerrainShapeCache;
import net.xmx.vortex.physics.terrain.chunk.TerrainChunk;
import net.xmx.vortex.physics.terrain.job.VxTerrainJobSystem;
import net.xmx.vortex.physics.terrain.loader.TerrainGenerator;
import net.xmx.vortex.physics.terrain.model.VxSectionPos;
import net.xmx.vortex.physics.terrain.tracker.ObjectTerrainTracker;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class TerrainSystem implements Runnable {

    private final VxPhysicsWorld physicsWorld;
    private final ServerLevel level;
    private final TerrainGenerator terrainGenerator;
    private final TerrainShapeCache shapeCache;

    private final Map<VxSectionPos, TerrainChunk> managedChunks = new ConcurrentHashMap<>();
    private final Map<VxSectionPos, Integer> chunkReferenceCounts = new ConcurrentHashMap<>();
    private final Map<UUID, ObjectTerrainTracker> objectTrackers = new ConcurrentHashMap<>();
    private final Set<VxSectionPos> activationSet = ConcurrentHashMap.newKeySet();

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private Thread workerThread;

    public TerrainSystem(VxPhysicsWorld physicsWorld, ServerLevel level) {
        this.physicsWorld = physicsWorld;
        this.level = level;
        this.shapeCache = new TerrainShapeCache(1024);
        this.terrainGenerator = new TerrainGenerator(this.shapeCache);
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
            if (workerThread != null) {
                workerThread.interrupt();
                try {
                    workerThread.join(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            managedChunks.values().forEach(TerrainChunk::scheduleRemoval);
            managedChunks.clear();
            chunkReferenceCounts.clear();
            objectTrackers.clear();
            shapeCache.clear();
        }
    }

    @Override
    public void run() {
        while (isInitialized.get() && !Thread.currentThread().isInterrupted()) {
            try {
                updateTrackers();
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Error in TerrainSystem worker thread", e);
            }
        }
    }

    private void updateTrackers() {
        Set<UUID> currentObjectIds = physicsWorld.getObjectManager().getManagedObjects().keySet();

        objectTrackers.keySet().removeIf(id -> {
            if (!currentObjectIds.contains(id)) {
                ObjectTerrainTracker tracker = objectTrackers.get(id);
                if (tracker != null) {
                    tracker.releaseAll();
                }
                return true;
            }
            return false;
        });

        for (IPhysicsObject obj : physicsWorld.getObjectManager().getManagedObjects().values()) {
            if (obj.isPhysicsInitialized() && obj.getBodyId() != 0) {
                objectTrackers.computeIfAbsent(obj.getPhysicsId(), id -> new ObjectTerrainTracker(obj, this));
            } else {
                ObjectTerrainTracker tracker = objectTrackers.remove(obj.getPhysicsId());
                if (tracker != null) {
                    tracker.releaseAll();
                }
            }
        }

        if (objectTrackers.isEmpty()) {
            updateActivation(Collections.emptySet());
            return;
        }

        List<CompletableFuture<Set<VxSectionPos>>> futures = objectTrackers.values().stream()
                .map(tracker -> CompletableFuture.supplyAsync(tracker::update, VxTerrainJobSystem.getInstance().getExecutor()))
                .toList();

        Set<VxSectionPos> allRequiredActiveChunks = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        updateActivation(allRequiredActiveChunks);
    }

    public void requestChunk(VxSectionPos pos) {
        chunkReferenceCounts.compute(pos, (p, count) -> (count == null) ? 1 : count + 1);
        if (chunkReferenceCounts.get(pos) == 1) {
            loadChunkPhysics(pos);
        }
    }

    public void releaseChunk(VxSectionPos pos) {
        chunkReferenceCounts.compute(pos, (p, count) -> (count == null || count <= 1) ? null : count - 1);
        if (!chunkReferenceCounts.containsKey(pos)) {
            unloadChunkPhysics(pos);
        }
    }

    public void updateActivation(Set<VxSectionPos> requiredActiveSet) {
        Set<VxSectionPos> toDeactivate = new HashSet<>(activationSet);
        toDeactivate.removeAll(requiredActiveSet);

        Set<VxSectionPos> toActivate = new HashSet<>(requiredActiveSet);
        toActivate.removeAll(activationSet);

        toDeactivate.forEach(pos -> {
            Optional.ofNullable(managedChunks.get(pos)).ifPresent(TerrainChunk::deactivate);
            activationSet.remove(pos);
        });

        toActivate.forEach(pos -> {
            Optional.ofNullable(managedChunks.get(pos)).ifPresent(TerrainChunk::activate);
            activationSet.add(pos);
        });
    }

    public void onBlockUpdate(BlockPos worldPos, BlockState oldState, BlockState newState) {
        if (!isInitialized.get() || VxTerrainJobSystem.getInstance().isShuttingDown()) return;

        VxTerrainJobSystem.getInstance().submit(() -> {
            VxSectionPos vPos = VxSectionPos.fromBlockPos(worldPos.immutable());
            Optional.ofNullable(managedChunks.get(vPos)).ifPresent(TerrainChunk::scheduleRebuild);
        });

        physicsWorld.execute(() -> {
            BodyInterface bodyInterface = physicsWorld.getBodyInterface();
            if (bodyInterface == null) {
                return;
            }

            RVec3 min;
            RVec3 max;
            AaBox activationArea = null;
            BroadPhaseLayerFilter broadPhaseFilter = null;
            ObjectLayerFilter objectLayerFilter = null;

            try {
                min = new RVec3(worldPos.getX() - 2.0, worldPos.getY() - 2.0, worldPos.getZ() - 2.0);
                max = new RVec3(worldPos.getX() + 3.0, worldPos.getY() + 3.0, worldPos.getZ() + 3.0);
                activationArea = new AaBox(min, max);
                broadPhaseFilter = new BroadPhaseLayerFilter();
                objectLayerFilter = new ObjectLayerFilter();

                bodyInterface.activateBodiesInAaBox(activationArea, broadPhaseFilter, objectLayerFilter);
            } finally {
                if (activationArea != null) activationArea.close();
                if (objectLayerFilter != null) objectLayerFilter.close();
                if (broadPhaseFilter != null) broadPhaseFilter.close();
            }
        });
    }

    public void onChunkUnloaded(ChunkPos chunkPos) {
        if (!isInitialized.get() || VxTerrainJobSystem.getInstance().isShuttingDown()) return;
        VxTerrainJobSystem.getInstance().submit(() -> {
            for (int y = level.getMinSection(); y < level.getMaxSection(); y++) {
                VxSectionPos vPos = new VxSectionPos(chunkPos.x, y, chunkPos.z);
                if(chunkReferenceCounts.containsKey(vPos)) {
                    chunkReferenceCounts.remove(vPos);
                    unloadChunkPhysics(vPos);
                }
            }
        });
    }

    private void loadChunkPhysics(VxSectionPos pos) {
        if (!isInitialized.get()) return;
        managedChunks.computeIfAbsent(pos, p -> {
            TerrainChunk newChunk = new TerrainChunk(p, level, physicsWorld, terrainGenerator);
            newChunk.scheduleInitialBuild();
            return newChunk;
        });
    }

    private void unloadChunkPhysics(VxSectionPos pos) {
        TerrainChunk chunk = managedChunks.remove(pos);
        if (chunk != null) {
            chunk.scheduleRemoval();
            activationSet.remove(pos);
        }
    }

    public boolean isSectionReady(SectionPos sectionPos) {
        if (sectionPos == null) return false;
        VxSectionPos vPos = new VxSectionPos(sectionPos.x(), sectionPos.y(), sectionPos.z());
        TerrainChunk chunk = managedChunks.get(vPos);
        return chunk != null && chunk.isReady();
    }

    public boolean isTerrainBody(int bodyId) {
        if (bodyId < 0) return false;
        for (TerrainChunk chunk : managedChunks.values()) {
            if (chunk.getBodyId() == bodyId) {
                return true;
            }
        }
        return false;
    }

    public ServerLevel getLevel() {
        return level;
    }
}