package net.xmx.xbullet.physics.terrain.mesh;

import com.github.stephengold.joltjni.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.terrain.cache.TerrainShapeCache;
import net.xmx.xbullet.physics.terrain.chunk.TerrainSection;
import net.xmx.xbullet.physics.terrain.manager.TerrainChunkManager;
import net.xmx.xbullet.physics.terrain.manager.TerrainSystem;
import net.xmx.xbullet.physics.terrain.pcmd.SwapTerrainShapeCommand;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class TerrainMesher {

    private final ExecutorService executor;
    private final Semaphore semaphore;
    private final Map<TerrainSection, CompletableFuture<ShapeRefC>> pendingMeshes = new ConcurrentHashMap<>();
    private final PhysicsWorld physicsWorld;
    private final ServerLevel level;
    private final TerrainChunkManager chunkManager;
    private final TerrainSystem terrainSystem;
    private final TerrainShapeCache shapeCache;

    private static final int MESHING_RESOLUTION = 2;

    public TerrainMesher(TerrainSystem terrainSystem, PhysicsWorld physicsWorld, ServerLevel level, TerrainChunkManager chunkManager) {
        this.terrainSystem = terrainSystem;
        this.physicsWorld = physicsWorld;
        this.level = level;
        this.chunkManager = chunkManager;
        int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        this.executor = Executors.newFixedThreadPool(numThreads, r -> new Thread(r, "XBullet-Terrain-Mesher-" + level.dimension().location().getPath()));
        this.semaphore = new Semaphore(numThreads * 2);
        this.shapeCache = new TerrainShapeCache(512);
    }

    public void submitNewTasks(PriorityQueue<TerrainSection> queue) {
        if (terrainSystem.isShutdown()) return;

        while (semaphore.tryAcquire()) {
            TerrainSection section = queue.poll();
            if (section == null) {
                semaphore.release();
                break;
            }

            if (section.getState() != TerrainSection.State.PLACEHOLDER || pendingMeshes.containsKey(section)) {
                semaphore.release();
                continue;
            }

            ChunkAccess chunk = level.getChunkSource().getChunk(section.getPos().x(), section.getPos().z(), ChunkStatus.FULL, false);
            if (chunk == null) {
                queue.add(section);
                semaphore.release();
                continue;
            }

            BlockState[] blockStates = new BlockState[4096];
            boolean hasOnlyAir = true;
            int minBlockX = section.getPos().x() << 4;
            int minBlockY = section.getPos().y() << 4;
            int minBlockZ = section.getPos().z() << 4;

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int index = (y * 16 + z) * 16 + x;
                        BlockPos currentPos = new BlockPos(minBlockX + x, minBlockY + y, minBlockZ + z);
                        BlockState state = chunk.getBlockState(currentPos);
                        blockStates[index] = state;
                        if (!state.isAir()) {
                            hasOnlyAir = false;
                        }
                    }
                }
            }

            if (hasOnlyAir) {
                try (ShapeSettings emptySettings = new EmptyShapeSettings();
                     ShapeResult result = emptySettings.create()) {
                    if (result.isValid()) {
                        physicsWorld.queueCommand(new SwapTerrainShapeCommand(section, result.get(), terrainSystem));
                    }
                }
                semaphore.release();
                continue;
            }

            int contentHash = Arrays.deepHashCode(new Object[]{blockStates, MESHING_RESOLUTION});
            ShapeRefC cachedShape = shapeCache.get(contentHash);

            if (cachedShape != null) {
                physicsWorld.queueCommand(new SwapTerrainShapeCommand(section, cachedShape, terrainSystem));
                semaphore.release();
                continue;
            }

            VoxelShape[] voxelShapes = new VoxelShape[4096];
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int index = (y * 16 + z) * 16 + x;
                        BlockState state = blockStates[index];
                        if (!state.isAir()) {
                            BlockPos currentPos = new BlockPos(minBlockX + x, minBlockY + y, minBlockZ + z);
                            voxelShapes[index] = state.getCollisionShape(level, currentPos);
                        } else {
                            voxelShapes[index] = Shapes.empty();
                        }
                    }
                }
            }

            section.setState(TerrainSection.State.MESHING);

            CompletableFuture<ShapeRefC> future = CompletableFuture.supplyAsync(() -> {
                try (MutableCompoundShapeSettings settings = new DirectMeshingTask(voxelShapes).get()) {
                    try (ShapeResult result = settings.create()) {
                        if (result.isValid()) {
                            ShapeRefC newShapeRef = result.get();

                            shapeCache.put(contentHash, newShapeRef.getPtr().toRefC());
                            return newShapeRef;
                        } else {
                            XBullet.LOGGER.error("Failed to create shape for section {}: {}", section.getPos(), result.getError());
                            return null;
                        }
                    }
                }
            }, executor).whenComplete((ref, ex) -> semaphore.release());

            pendingMeshes.put(section, future);
        }
    }

    public void processCompletedMeshes() {
        Iterator<Map.Entry<TerrainSection, CompletableFuture<ShapeRefC>>> it = pendingMeshes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<TerrainSection, CompletableFuture<ShapeRefC>> entry = it.next();
            if (entry.getValue().isDone()) {
                TerrainSection section = entry.getKey();
                try {
                    ShapeRefC newShapeRef = entry.getValue().get();
                    if (terrainSystem.isShutdown()) {
                        if (newShapeRef != null) newShapeRef.close();
                        continue;
                    }

                    if (newShapeRef != null && chunkManager.getSection(section.getPos()) != null) {

                        physicsWorld.queueCommand(new SwapTerrainShapeCommand(section, newShapeRef, terrainSystem));
                    } else if (newShapeRef != null) {

                        newShapeRef.close();
                    } else {

                        section.setState(TerrainSection.State.PLACEHOLDER);
                    }
                } catch (Exception e) {
                    if (!terrainSystem.isShutdown()) {
                        section.setState(TerrainSection.State.PLACEHOLDER);
                        XBullet.LOGGER.error("Exception processing completed mesh for section {}", section.getPos(), e);
                    }
                } finally {
                    it.remove();
                }
            }
        }
    }

    public void shutdown() {
        executor.shutdownNow();
        pendingMeshes.values().forEach(f -> {
            if (f.isDone()) {
                try {
                    ShapeRefC ref = f.get();
                    if (ref != null) ref.close();
                } catch (Exception ignored) {}
            }
            f.cancel(true);
        });
        pendingMeshes.clear();
        shapeCache.clear();
    }
}