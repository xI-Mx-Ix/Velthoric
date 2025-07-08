package net.xmx.xbullet.physics.terrain.manager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.object.physicsobject.manager.ObjectManager;
import net.xmx.xbullet.physics.terrain.pcmd.WakeUpBodiesNearCommand;
import net.xmx.xbullet.physics.world.PhysicsWorld;
import net.xmx.xbullet.physics.terrain.chunk.TerrainSection;
import net.xmx.xbullet.physics.terrain.mesh.TerrainMesher;

import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class TerrainSystem implements Runnable {

    private final PhysicsWorld physicsWorld;
    private final ServerLevel level;

    private final Map<Integer, Boolean> terrainBodyCache = new ConcurrentHashMap<>();

    private static final double WAKE_UP_RADIUS = 10.0;
    private static final double WAKE_UP_RADIUS_SQ = WAKE_UP_RADIUS * WAKE_UP_RADIUS;

    public static final long TERRAIN_USER_DATA_FLAG = 1L << 63;

    private TerrainChunkManager chunkManager;
    private TerrainPriorityUpdater priorityUpdater;
    private TerrainMesher mesher;

    private volatile boolean isRunning = false;
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private Thread workerThread;

    public TerrainSystem(PhysicsWorld physicsWorld, ServerLevel level) {
        this.physicsWorld = physicsWorld;
        this.level = level;
    }

    public void initialize(ObjectManager manager) {
        if (isRunning || isShutdown.get()) return;
        this.chunkManager = new TerrainChunkManager(this, physicsWorld, level);
        this.priorityUpdater = new TerrainPriorityUpdater(level, manager, this.physicsWorld);
        this.mesher = new TerrainMesher(this, physicsWorld, level, chunkManager);

        this.isRunning = true;
        this.workerThread = new Thread(this, "XBullet-Terrain-System-" + level.dimension().location().getPath());
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    @Override
    public void run() {
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                if (level.getServer().isSameThread()) {
                    XBullet.LOGGER.error("TerrainSystem-run() is executing on the main server thread! This should not happen.");
                    Thread.sleep(100);
                    continue;
                }

                PriorityQueue<TerrainSection> queue = priorityUpdater.updateAndCreateQueue(chunkManager);

                mesher.processCompletedMeshes();

                mesher.submitNewTasks(queue);

                Thread.sleep(50);

            } catch (InterruptedException e) {
                this.isRunning = false;
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                XBullet.LOGGER.error("An error occurred in the TerrainSystem thread for {}.", level.dimension().location(), e);
                try { Thread.sleep(5000); } catch (InterruptedException ie) { this.isRunning = false; }
            }
        }
    }

    public void tick() {

    }

    public void onChunkLoad(ChunkAccess chunk) {
        if (isRunning) {
            chunkManager.onChunkLoad(chunk);
        }
    }

    public void onBlockChanged(BlockPos pos) {
        if (!isRunning) return;

        invalidateSectionAt(pos);

        final int localX = pos.getX() & 15;
        if (localX == 0) invalidateSectionAt(pos.west());
        if (localX == 15) invalidateSectionAt(pos.east());

        final int localY = pos.getY() & 15;
        if (localY == 0) invalidateSectionAt(pos.below());
        if (localY == 15) invalidateSectionAt(pos.above());

        final int localZ = pos.getZ() & 15;
        if (localZ == 0) invalidateSectionAt(pos.north());
        if (localZ == 15) invalidateSectionAt(pos.south());

        physicsWorld.queueCommand(new WakeUpBodiesNearCommand(pos));
    }

    public void registerTerrainBody(int bodyId) {
        terrainBodyCache.put(bodyId, true);
    }

    public void unregisterTerrainBody(int bodyId) {
        terrainBodyCache.remove(bodyId);
    }

    private void invalidateSectionAt(BlockPos pos) {

        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) return;

        SectionPos sectionPos = SectionPos.of(pos);
        TerrainSection section = chunkManager.getSection(sectionPos);

        if (section != null && section.getState() != TerrainSection.State.UNLOADED && section.getState() != TerrainSection.State.MESHING) {
            section.setState(TerrainSection.State.PLACEHOLDER);
            section.setPriority(Double.MAX_VALUE);
        }
    }

    public boolean isSectionReady(SectionPos pos) {
        if (chunkManager == null) return false;
        TerrainSection section = chunkManager.getSection(pos);
        if (section == null) return false;

        TerrainSection.State state = section.getState();
        return state == TerrainSection.State.READY_INACTIVE || state == TerrainSection.State.READY_ACTIVE;
    }

    public void onChunkUnload(ChunkAccess chunk) {
        if (isRunning) {
            chunkManager.onChunkUnload(chunk);
        }
    }

    public void shutdown() {
        if (isShutdown.getAndSet(true)) return;

        this.isRunning = false;
        if (workerThread != null) {
            workerThread.interrupt();
            try {
                workerThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (mesher != null) mesher.shutdown();
        if (chunkManager != null) chunkManager.shutdown();
    }

    public boolean isShutdown() {
        return isShutdown.get();
    }

    public ServerLevel getLevel() {
        return level;
    }

    public boolean isTerrainBody(int bodyId) {
        return terrainBodyCache.containsKey(bodyId);
    }
}