package net.xmx.xbullet.physics.terrain.mesh;

import com.github.stephengold.joltjni.ShapeSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;
import net.xmx.xbullet.physics.terrain.chunk.TerrainSection;
import net.xmx.xbullet.physics.terrain.manager.TerrainChunkManager;
import net.xmx.xbullet.physics.terrain.manager.TerrainSystem;
import net.xmx.xbullet.physics.terrain.pcmd.SwapTerrainShapeCommand;

import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.*;

public class TerrainMesher {

    private final ExecutorService executor;
    private final Semaphore semaphore;
    private final Map<TerrainSection, CompletableFuture<ShapeSettings>> pendingMeshes = new ConcurrentHashMap<>();
    private final PhysicsWorld physicsWorld;
    private final ServerLevel level;
    private final TerrainChunkManager chunkManager;
    private final TerrainSystem terrainSystem;

    public TerrainMesher(TerrainSystem terrainSystem, PhysicsWorld physicsWorld, ServerLevel level, TerrainChunkManager chunkManager) {
        this.terrainSystem = terrainSystem;
        this.physicsWorld = physicsWorld;
        this.level = level;
        this.chunkManager = chunkManager;
        int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        this.executor = Executors.newFixedThreadPool(numThreads, r -> new Thread(r, "XBullet-Terrain-Mesher-" + level.dimension().location().getPath()));
        this.semaphore = new Semaphore(numThreads);
    }

    public void submitNewTasks(PriorityQueue<TerrainSection> queue) {
        if (terrainSystem.isShutdown()) {
            return;
        }

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

            SectionPos sectionPos = section.getPos();
            ChunkAccess chunk = level.getChunkSource().getChunk(
                    sectionPos.x(),
                    sectionPos.z(),
                    ChunkStatus.FULL,
                    false
            );

            if (chunk == null) {
                queue.add(section);
                semaphore.release();
                continue;
            }

            BlockState[] blockData = new BlockState[4096];
            BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
            int sectionY = sectionPos.y();

            net.minecraft.world.level.chunk.LevelChunkSection chunkSection = chunk.getSection(chunk.getSectionIndex(sectionPos.minBlockY()));
            if(chunkSection.hasOnlyAir()){

            }

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        mutablePos.set(sectionPos.minBlockX() + x, sectionPos.minBlockY() + y, sectionPos.minBlockZ() + z);
                        blockData[(y * 16 + z) * 16 + x] = chunk.getBlockState(mutablePos);
                    }
                }
            }

            section.setState(TerrainSection.State.MESHING);

            MeshGenerationTask task = new MeshGenerationTask(blockData);

            CompletableFuture<ShapeSettings> future = CompletableFuture
                    .supplyAsync(task, executor)
                    .whenComplete((settings, ex) -> semaphore.release());

            pendingMeshes.put(section, future);
        }
    }

    public void processCompletedMeshes() {
        Iterator<Map.Entry<TerrainSection, CompletableFuture<ShapeSettings>>> it = pendingMeshes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<TerrainSection, CompletableFuture<ShapeSettings>> entry = it.next();
            if (entry.getValue().isDone()) {
                TerrainSection section = entry.getKey();
                try {
                    if (terrainSystem.isShutdown()) {
                        ShapeSettings settings = entry.getValue().getNow(null);
                        if (settings != null) {
                            settings.close();
                        }
                        continue;
                    }

                    ShapeSettings newShapeSettings = entry.getValue().get();
                    if (newShapeSettings != null && chunkManager.getSection(section.getPos()) != null) {
                        physicsWorld.queueCommand(new SwapTerrainShapeCommand(section, newShapeSettings, terrainSystem));
                    } else if (newShapeSettings != null) {
                        newShapeSettings.close();
                    }
                } catch (Exception e) {
                    if (!terrainSystem.isShutdown()) {
                        section.setState(TerrainSection.State.PLACEHOLDER);
                    }
                } finally {
                    it.remove();
                }
            }
        }
    }

    public void shutdown() {
        executor.shutdownNow();
        pendingMeshes.values().forEach(f -> f.cancel(true));
        pendingMeshes.clear();
    }
}