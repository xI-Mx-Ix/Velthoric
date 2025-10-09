/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.generation;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.natives.VxLayers;
import net.xmx.velthoric.physics.terrain.chunk.VxChunkSnapshot;
import net.xmx.velthoric.physics.terrain.data.VxChunkDataStore;
import net.xmx.velthoric.physics.terrain.data.VxSectionPos;
import net.xmx.velthoric.physics.terrain.job.VxTerrainJobSystem;
import net.xmx.velthoric.physics.terrain.management.VxTerrainManager;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the asynchronous generation of terrain physics shapes. It processes a queue
 * of chunk sections that need rebuilding and handles the replacement of physics bodies
 * in the simulation.
 *
 * @author xI-Mx-Ix
 */
public class VxTerrainShapeGenerator {

    private final VxPhysicsWorld physicsWorld;
    private final ServerLevel level;
    private final VxTerrainJobSystem jobSystem;
    private final VxChunkDataStore chunkDataStore;
    private final VxGreedyMesher greedyMesher;

    private final Set<Long> chunksToRebuild = ConcurrentHashMap.newKeySet();

    public VxTerrainShapeGenerator(VxPhysicsWorld physicsWorld, ServerLevel level, VxTerrainJobSystem jobSystem, VxChunkDataStore chunkDataStore, VxGreedyMesher greedyMesher) {
        this.physicsWorld = physicsWorld;
        this.level = level;
        this.jobSystem = jobSystem;
        this.chunkDataStore = chunkDataStore;
        this.greedyMesher = greedyMesher;
    }

    /**
     * Main tick method for the shape generator, called from the worker thread.
     * Processes the queue of chunks marked for rebuilding.
     */
    public void tick() {
        int processed = 0;
        final int maxToProcess = 128;

        if (chunksToRebuild.isEmpty()) {
            return;
        }

        Iterator<Long> iterator = chunksToRebuild.iterator();
        while (iterator.hasNext() && processed < maxToProcess) {
            long packedPos = iterator.next();
            iterator.remove();

            Integer index = chunkDataStore.getIndexForPos(packedPos);
            if (index != null) {
                scheduleShapeGeneration(packedPos, index);
            }
            processed++;
        }
    }

    /**
     * Adds a chunk section position to the queue for a rebuild, typically after a block update.
     * @param packedPos The packed long position of the chunk section to rebuild.
     */
    public void requestRebuild(long packedPos) {
        chunksToRebuild.add(packedPos);
    }

    /**
     * Removes a chunk section from the rebuild queue, e.g., when it's being unloaded.
     * @param packedPos The packed long position to cancel.
     */
    public void cancelRebuild(long packedPos) {
        chunksToRebuild.remove(packedPos);
    }

    public void scheduleShapeGeneration(long packedPos, int index) {
        if (jobSystem.isShutdown()) return;

        // Atomically check state, set to loading, and get new version.
        // This prevents race conditions from multiple block updates on the same chunk.
        final int version = chunkDataStore.scheduleRebuild(index);
        if (version == -1) {
            return; // Another thread is already handling a rebuild for this chunk.
        }

        jobSystem.submit(() -> {
            // Check if a newer version has been requested since this job was submitted.
            if (version < chunkDataStore.getRebuildVersion(index) || jobSystem.isShutdown()) {
                return; // This job is stale, a newer one has been scheduled.
            }

            LevelChunk chunk = level.getChunkSource().getChunk(VxSectionPos.unpackX(packedPos), VxSectionPos.unpackZ(packedPos), false);
            if (chunk == null) {
                // If chunk is not loaded, wait for it. onChunkLoaded will trigger a new build.
                chunkDataStore.setState(index, VxChunkDataStore.STATE_AWAITING_CHUNK);
                return;
            }

            // Now we have the chunk, proceed with generation.
            chunkDataStore.setState(index, VxChunkDataStore.STATE_GENERATING_SHAPE);
            VxChunkSnapshot snapshot = VxChunkSnapshot.snapshotFromChunk(level, chunk, packedPos);
            processShapeGenerationOnWorker(packedPos, index, version, snapshot);
        });
    }

    private void processShapeGenerationOnWorker(long packedPos, int index, int version, VxChunkSnapshot snapshot) {
        if (version < chunkDataStore.getRebuildVersion(index)) return; // Stale check

        try {
            ShapeRefC generatedShape = greedyMesher.generateShape(snapshot);
            // Switch to the main physics thread to apply the new shape.
            physicsWorld.execute(() -> applyGeneratedShape(packedPos, index, version, generatedShape));
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Exception during terrain shape generation for {}", VxSectionPos.unpackToSectionPos(packedPos), e);
            chunkDataStore.setState(index, VxChunkDataStore.STATE_UNLOADED);
        }
    }

    private void applyGeneratedShape(long packedPos, int index, int version, ShapeRefC shape) {
        // Final check on the physics thread to ensure this job is still valid.
        if (version < chunkDataStore.getRebuildVersion(index) || chunkDataStore.getState(index) == VxChunkDataStore.STATE_REMOVING) {
            if (shape != null) shape.close();
            return;
        }

        BodyInterface bodyInterface = physicsWorld.getBodyInterface();
        if (bodyInterface == null) {
            if (shape != null) shape.close();
            return;
        }

        // Remove the old body if it exists.
        int oldBodyId = chunkDataStore.getBodyId(index);
        if (oldBodyId != VxChunkDataStore.UNUSED_BODY_ID) {
            if (bodyInterface.isAdded(oldBodyId)) {
                bodyInterface.removeBody(oldBodyId);
            }
            bodyInterface.destroyBody(oldBodyId);
        }
        // Also releases old shape reference
        chunkDataStore.setShape(index, null);
        chunkDataStore.setBodyId(index, VxChunkDataStore.UNUSED_BODY_ID);

        // If the new shape is not null (i.e., not an all-air chunk), create and add the new body.
        if (shape != null) {
            BlockPos origin = VxSectionPos.unpackToOrigin(packedPos);
            RVec3 position = new RVec3(origin.getX(), origin.getY(), origin.getZ());
            try (BodyCreationSettings bcs = new BodyCreationSettings(shape, position, Quat.sIdentity(), EMotionType.Static, VxLayers.TERRAIN)) {
                bcs.setUserData(VxTerrainManager.TERRAIN_BODY_USER_DATA);
                Body newBody = bodyInterface.createBody(bcs);

                if (newBody != null) {
                    // Add the new body to the world immediately to prevent objects from falling through.
                    bodyInterface.addBody(newBody.getId(), EActivation.DontActivate);

                    // Update the data store with the new information.
                    chunkDataStore.setBodyId(index, newBody.getId());
                    chunkDataStore.setShape(index, shape);
                    chunkDataStore.setState(index, VxChunkDataStore.STATE_READY_INACTIVE);
                } else {
                    VxMainClass.LOGGER.error("Failed to create terrain body for chunk {}", VxSectionPos.unpackToSectionPos(packedPos));
                    shape.close(); // Clean up the shape if body creation fails.
                    chunkDataStore.setState(index, VxChunkDataStore.STATE_UNLOADED);
                }
            }
        } else {
            // This chunk section is entirely air, so it has no body.
            chunkDataStore.setState(index, VxChunkDataStore.STATE_AIR_CHUNK);
        }
    }
}