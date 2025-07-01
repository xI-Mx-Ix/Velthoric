package net.xmx.xbullet.physics.terrain.manager;


import com.github.stephengold.joltjni.RVec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManager;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.PhysicsObjectManagerRegistry;
import net.xmx.xbullet.physics.object.global.physicsobject.pcmd.ActivateBodyCommand;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;
import net.xmx.xbullet.physics.terrain.chunk.TerrainSection;
import net.xmx.xbullet.physics.terrain.mesh.TerrainMesher;

import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class TerrainSystem implements Runnable {

    private final PhysicsWorld physicsWorld;
    private final ServerLevel level;

    private static final double WAKE_UP_RADIUS = 10.0;
    private static final double WAKE_UP_RADIUS_SQ = WAKE_UP_RADIUS * WAKE_UP_RADIUS;

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

    public void initialize(PhysicsObjectManager manager) {
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
                XBullet.LOGGER.info("TerrainSystem thread for {} was interrupted.", level.dimension().location());
            } catch (Exception e) {
                XBullet.LOGGER.error("An error occurred in the TerrainSystem thread for {}.", level.dimension().location(), e);

                try { Thread.sleep(5000); } catch (InterruptedException ie) { this.isRunning = false; }
            }
        }
        XBullet.LOGGER.info("TerrainSystem thread for {} has shut down.", level.dimension().location());
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

        System.out.println("TerrainSystem.onBlockChanged(" + pos + ")");

        invalidateSectionAt(pos);

        int x = pos.getX() & 15;
        int y = pos.getY() & 15;
        int z = pos.getZ() & 15;

        if (x == 0) invalidateSectionAt(pos.west());
        if (x == 15) invalidateSectionAt(pos.east());
        if (y == 0) invalidateSectionAt(pos.below());
        if (y == 15) invalidateSectionAt(pos.above());
        if (z == 0) invalidateSectionAt(pos.north());
        if (z == 15) invalidateSectionAt(pos.south());

        wakeUpBodiesNear(pos);
    }

    private void wakeUpBodiesNear(BlockPos pos) {

        final double blockCenterX = pos.getX() + 0.5;
        final double blockCenterY = pos.getY() + 0.5;
        final double blockCenterZ = pos.getZ() + 0.5;

        for (IPhysicsObject obj : PhysicsObjectManagerRegistry.getInstance().getManagerForLevel(level).getManagedObjects().values()) {

            if (obj.getBodyId() == 0) {
                continue;
            }

            RVec3 objPos = obj.getCurrentTransform().getTranslation();
            double dx = objPos.xx() - blockCenterX;
            double dy = objPos.yy() - blockCenterY;
            double dz = objPos.zz() - blockCenterZ;
            double distanceSq = dx * dx + dy * dy + dz * dz;

            if (distanceSq <= WAKE_UP_RADIUS_SQ) {
                physicsWorld.queueCommand(new ActivateBodyCommand(obj.getBodyId()));
            }
        }
    }

    private void invalidateSectionAt(BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) return;

        SectionPos sectionPos = SectionPos.of(pos);
        TerrainSection section = chunkManager.getSection(sectionPos);

        if (section != null && (section.getState() == TerrainSection.State.READY_ACTIVE || section.getState() == TerrainSection.State.READY_INACTIVE)) {
            section.setState(TerrainSection.State.PLACEHOLDER);
            section.setPriority(Double.MAX_VALUE);
        }
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
}