/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.terrain;

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.Jolt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.xmx.velthoric.core.physics.VxPhysicsLayers;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.core.terrain.generation.VxChunkSnapshot;
import net.xmx.velthoric.core.terrain.management.VxTerrainTracker;
import net.xmx.velthoric.core.terrain.material.VxTerrainMaterial;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.jni.TerrainSystem;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The main entry point and orchestrator for the terrain physics system.
 * <p>
 * This class is a thin Java-side wrapper that delegates all state management,
 * body creation, and shape generation to the native C++ TerrainSystem.
 * Java is responsible only for snapshotting Minecraft chunk data (which requires
 * access to the server level) and forwarding it to the native layer as raw
 * voxel box data.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public final class VxTerrainSystem implements Runnable {

    /**
     * The physics world this terrain system belongs to.
     */
    private final VxPhysicsWorld physicsWorld;

    /**
     * The Minecraft server level for chunk access and main-thread scheduling.
     */
    private final ServerLevel level;

    /**
     * The Minecraft server instance for main-thread task scheduling.
     */
    private final MinecraftServer server;

    /**
     * The dedicated worker thread for terrain tracking and rebuild processing.
     */
    private final Thread workerThread;

    /**
     * Atomic flag indicating whether the system has been initialized and is running.
     */
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    /**
     * The body tracker that determines which chunks need to be loaded based on body positions.
     */
    private VxTerrainTracker terrainTracker;

    /**
     * Native object wrapper to the C++ TerrainSystem instance.
     * All state management and body lifecycle operations are delegated to this.
     */
    private volatile TerrainSystem nativeSystem = null;

    /**
     * Chunks scheduled for a rebuild due to world modification.
     * Uses a concurrent set to avoid blocking the main server thread during block updates.
     */
    private final Set<Long> chunksToRebuild = ConcurrentHashMap.newKeySet();

    /**
     * Thread-local direct byte buffer for efficiently passing box data to native C++.
     * Stores up to 32768 BoxShapeData structs (28 bytes each).
     */
    private static final ThreadLocal<ByteBuffer> shapeBuffer = ThreadLocal.withInitial(() -> {
        ByteBuffer buf = Jolt.newDirectByteBuffer(32768 * 28);
        buf.order(ByteOrder.nativeOrder());
        return buf;
    });

    /**
     * Constructs a new VxTerrainSystem for the given physics world and level.
     *
     * @param physicsWorld The Velthoric physics world.
     * @param level        The Minecraft server level.
     */
    public VxTerrainSystem(VxPhysicsWorld physicsWorld, ServerLevel level) {
        this.physicsWorld = physicsWorld;
        this.level = level;
        this.server = level.getServer();

        this.workerThread = new Thread(this, "Velthoric Terrain System - " + level.dimension().location().getPath());
        this.workerThread.setDaemon(true);
    }

    /**
     * Initializes the terrain system by creating the native TerrainSystem
     * and starting the worker thread.
     */
    public void initialize() {
        if (isInitialized.compareAndSet(false, true)) {
            BodyInterface bi = physicsWorld.getPhysicsSystem().getBodyInterface();
            if (bi != null) {
                this.nativeSystem = new TerrainSystem(bi.va(), VxPhysicsLayers.TERRAIN);
            }
            this.terrainTracker = new VxTerrainTracker(physicsWorld, level, this.nativeSystem);
            this.workerThread.start();
        }
    }

    /**
     * Shuts down the terrain system, stops the worker thread, and cleans up
     * all native resources including physics bodies and shape caches.
     */
    public void shutdown() {
        if (isInitialized.compareAndSet(true, false)) {
            VxMainClass.LOGGER.debug("Initiating shutdown of Terrain System for '{}'...", level.dimension().location());

            workerThread.interrupt();

            try {
                // Wait briefly for the worker thread to exit.
                workerThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (workerThread.isAlive()) {
                VxMainClass.LOGGER.warn("Terrain system thread for '{}' did not terminate quickly. Proceeding with cleanup anyway.", level.dimension().location());
            }

            terrainTracker.clear();
            chunksToRebuild.clear();

            // Clean up native resources
            TerrainSystem sys = this.nativeSystem;
            if (sys != null) {
                try {
                    sys.cleanupAllBodies();
                    TerrainSystem.nClearShapeCache();
                } catch (Exception e) {
                    VxMainClass.LOGGER.error("Error during terrain resource cleanup for '{}'", level.dimension().location(), e);
                }
                sys.close();
                this.nativeSystem = null;
            }

            VxMainClass.LOGGER.debug("Terrain system for '{}' has been shut down.", level.dimension().location());
        }
    }

    /**
     * The main loop for the terrain system worker thread. Periodically updates
     * the body tracker and processes chunks that need to be rebuilt.
     */
    @Override
    public void run() {
        while (isInitialized.get() && server.isRunning() && !Thread.currentThread().isInterrupted()) {
            try {
                if (physicsWorld.isRunning() && nativeSystem != null) {
                    terrainTracker.update();
                    processRebuildQueue();
                }
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (isInitialized.get() && server.isRunning()) {
                    VxMainClass.LOGGER.error("Error in TerrainSystem worker thread for {}", level.dimension().location(), e);
                }
            }
        }
    }

    /**
     * Handles a block update event from the game world. If a terrain chunk is affected,
     * it is queued for a rebuild and nearby physics bodies are woken up.
     *
     * @param worldPos The position of the block that changed.
     */
    public void onBlockUpdate(BlockPos worldPos) {
        TerrainSystem sys = this.nativeSystem;
        if (!isInitialized.get() || !server.isRunning() || sys == null) {
            return;
        }

        long packedPos = SectionPos.asLong(worldPos);
        if (sys.isManaged(packedPos)) {
            chunksToRebuild.add(packedPos);
        }

        physicsWorld.execute(() -> sys.onBlockUpdate(worldPos.getX(), worldPos.getY(), worldPos.getZ()));
    }

    /**
     * Processes the queue of chunks waiting to be rebuilt, scheduling them for
     * snapshot and native data submission.
     */
    private void processRebuildQueue() {
        if (chunksToRebuild.isEmpty()) return;

        Set<Long> batch = ConcurrentHashMap.newKeySet();
        batch.addAll(chunksToRebuild);
        chunksToRebuild.removeAll(batch);

        for (long packedPos : batch) {
            scheduleChunkDataSubmission(packedPos, false);
        }
    }

    /**
     * Schedules the snapshot and native data submission for a terrain chunk.
     * The snapshot is taken on the main server thread, and the serialized data
     * is submitted to the native TerrainSystem.
     *
     * @param packedPos      The bit-packed section coordinate.
     * @param isInitialBuild True if this is the first build for this chunk.
     */
    public void scheduleChunkDataSubmission(long packedPos, boolean isInitialBuild) {
        if (nativeSystem == null) return;

        level.getServer().execute(() -> {
            if (!isInitialized.get() || nativeSystem == null) return;

            LevelChunk chunk = level.getChunkSource().getChunk(SectionPos.x(packedPos), SectionPos.z(packedPos), false);
            if (chunk == null) return;

            VxChunkSnapshot snapshot = VxChunkSnapshot.snapshotFromChunk(level, chunk, packedPos);
            submitSnapshotToNative(packedPos, snapshot, isInitialBuild);
        });
    }

    /**
     * Serializes a chunk snapshot into the box buffer and submits it to the native system.
     * This method converts Minecraft VoxelShapes into BoxShapeData structs that the
     * C++ TerrainGenerator can process.
     *
     * @param packedPos      The bit-packed section coordinate.
     * @param snapshot       The immutable chunk snapshot.
     * @param isInitialBuild True if this is the first build.
     */
    private void submitSnapshotToNative(long packedPos, VxChunkSnapshot snapshot, boolean isInitialBuild) {
        TerrainSystem sys = this.nativeSystem;
        if (sys == null) return;

        float posX = SectionPos.sectionToBlockCoord(SectionPos.x(packedPos));
        float posY = SectionPos.sectionToBlockCoord(SectionPos.y(packedPos));
        float posZ = SectionPos.sectionToBlockCoord(SectionPos.z(packedPos));

        if (snapshot.count() == 0) {
            sys.submitChunkData(packedPos, posX, posY, posZ, null, 0, 0, isInitialBuild);
            return;
        }

        int contentHash = snapshot.hashCode();

        ByteBuffer boxes = shapeBuffer.get();
        boxes.clear();

        int boxCount = 0;
        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();

        int originX = SectionPos.sectionToBlockCoord(SectionPos.x(packedPos));
        int originY = SectionPos.sectionToBlockCoord(SectionPos.y(packedPos));
        int originZ = SectionPos.sectionToBlockCoord(SectionPos.z(packedPos));

        for (int i = 0; i < snapshot.count(); i++) {
            short packed = snapshot.packedPositions()[i];
            int x = (packed >> 8) & 0xF;
            int y = (packed >> 4) & 0xF;
            int z = packed & 0xF;

            worldPos.set(originX + x, originY + y, originZ + z);
            VoxelShape voxelShape = snapshot.states()[i].getCollisionShape(level, worldPos);

            if (voxelShape.isEmpty()) continue;

            int materialId = VxTerrainMaterial.getMaterialId(snapshot.states()[i].getBlock());

            for (AABB aabb : voxelShape.toAabbs()) {
                float hx = (float) (aabb.getXsize() / 2.0);
                float hy = (float) (aabb.getYsize() / 2.0);
                float hz = (float) (aabb.getZsize() / 2.0);

                if (hx <= 0.001f || hy <= 0.001f || hz <= 0.001f) {
                    continue;
                }

                float cx = (float) (x + aabb.minX + hx);
                float cy = (float) (y + aabb.minY + hy);
                float cz = (float) (z + aabb.minZ + hz);

                if (boxCount >= 32768) {
                    break;
                }

                boxes.putFloat(cx);
                boxes.putFloat(cy);
                boxes.putFloat(cz);
                boxes.putFloat(hx);
                boxes.putFloat(hy);
                boxes.putFloat(hz);
                boxes.putInt(materialId);

                boxCount++;
            }
        }

        sys.submitChunkData(packedPos, posX, posY, posZ, boxes, boxCount, contentHash, isInitialBuild);
    }

    /**
     * Checks if a terrain chunk at a given packed position is fully loaded and ready.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return True if the chunk is ready.
     */
    public boolean isReady(long packedPos) {
        TerrainSystem sys = this.nativeSystem;
        return sys != null && sys.isReady(packedPos);
    }

    /**
     * Checks if a terrain chunk at a given position is using a placeholder shape.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return True if placeholder.
     */
    public boolean isPlaceholder(long packedPos) {
        TerrainSystem sys = this.nativeSystem;
        return sys == null || sys.isPlaceholder(packedPos);
    }

    /**
     * Checks if a terrain chunk is ready based on its SectionPos.
     *
     * @param sectionPos The SectionPos of the chunk.
     * @return True if the chunk is ready.
     */
    public boolean isSectionReady(SectionPos sectionPos) {
        if (sectionPos == null) {
            return false;
        }
        return isReady(sectionPos.asLong());
    }

    /**
     * Determines if a given physics body ID belongs to a terrain chunk.
     *
     * @param bodyId The ID of the physics body.
     * @return True if the body is a terrain body.
     */
    public boolean isTerrainBody(int bodyId) {
        if (bodyId <= 0) return false;
        TerrainSystem sys = this.nativeSystem;
        return sys != null && sys.isTerrainBody(bodyId);
    }

    /**
     * Returns the wrapper to the C++ TerrainSystem.
     * Used by the tracker for direct native calls.
     *
     * @return The native system wrapper, or null if not initialized.
     */
    public TerrainSystem getNativeSystem() {
        return this.nativeSystem;
    }

    /**
     * Gets the Minecraft ServerLevel this terrain system is associated with.
     *
     * @return The ServerLevel instance.
     */
    public ServerLevel getLevel() {
        return level;
    }
}