/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.management;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BroadPhaseLayerFilter;
import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.ObjectLayerFilter;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.xmx.velthoric.physics.terrain.data.VxChunkDataStore;
import net.xmx.velthoric.physics.terrain.data.VxSectionPos;
import net.xmx.velthoric.physics.terrain.generation.VxTerrainShapeGenerator;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.NotNull;

/**
 * Manages the state and lifecycle of terrain physics bodies. It handles ticking logic
 * for chunk lifetime, processes rebuilds, and triggers shape generation.
 *
 * @author xI-Mx-Ix
 */
public class VxTerrainManager {

    private final VxPhysicsWorld physicsWorld;
    private final ServerLevel level;
    private final VxChunkDataStore chunkDataStore;
    private final VxTerrainShapeGenerator shapeGenerator;

    public static final long TERRAIN_BODY_USER_DATA = 0x5445525241494E42L; // "TERRAINB"

    public VxTerrainManager(VxPhysicsWorld physicsWorld, ServerLevel level, VxChunkDataStore chunkDataStore, VxTerrainShapeGenerator shapeGenerator) {
        this.physicsWorld = physicsWorld;
        this.level = level;
        this.chunkDataStore = chunkDataStore;
        this.shapeGenerator = shapeGenerator;
    }

    /**
     * Main tick method for the terrain manager, called from the worker thread.
     */
    public void tick() {
        processLifeCycle();
    }

    /**
     * Decrements the life counter of all managed chunks and removes those that expire.
     */
    private void processLifeCycle() {
        // Iterate over a snapshot of positions to avoid concurrency issues.
        for (long pos : chunkDataStore.getManagedPositions()) {
            Integer index = chunkDataStore.getIndexForPos(pos);
            if (index == null) continue; // Chunk was removed since we took the snapshot.

            if (chunkDataStore.decrementLifeCounterAndCheck(index)) {
                unloadChunkPhysicsInternal(pos);
            }
        }
    }

    /**
     * Handles a block update event from the game.
     *
     * @param worldPos The world position of the block that changed.
     */
    public void onBlockUpdate(BlockPos worldPos) {
        long packedPos = VxSectionPos.fromBlockPos(worldPos.immutable());
        Integer index = chunkDataStore.getIndexForPos(packedPos);
        if (index == null) return;

        int currentState = chunkDataStore.getState(index);
        // Trigger a rebuild if the chunk section is in a valid, active state.
        if (currentState >= VxChunkDataStore.STATE_READY_INACTIVE && currentState != VxChunkDataStore.STATE_REMOVING) {
            shapeGenerator.requestRebuild(packedPos);
        }

        // Wake up any nearby physics bodies that might be sleeping and affected by the change.
        physicsWorld.execute(() -> {
            BodyInterface bi = physicsWorld.getBodyInterface();
            if (bi == null) return;
            // Create temporary objects for the query.
            // These are lightweight and their allocation is not on a critical path.
            Vec3 min = new Vec3(worldPos.getX() - 2.0f, worldPos.getY() - 2.0f, worldPos.getZ() - 2.0f);
            Vec3 max = new Vec3(worldPos.getX() + 3.0f, worldPos.getY() + 3.0f, worldPos.getZ() + 3.0f);
            AaBox area = new AaBox(min, max);
            // Default filters activate all bodies in the area.
            bi.activateBodiesInAaBox(area, new BroadPhaseLayerFilter(), new ObjectLayerFilter());
        });
    }

    /**
     * Called when a Minecraft chunk is loaded. It checks for any terrain sections
     * within that chunk that were waiting for data and schedules their generation.
     *
     * @param chunk The loaded chunk.
     */
    public void handleChunkLoad(@NotNull LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        for (int y = level.getMinSection(); y < level.getMaxSection(); ++y) {
            long packedPos = VxSectionPos.pack(chunkPos.x, y, chunkPos.z);
            Integer index = chunkDataStore.getIndexForPos(packedPos);
            if (index != null && chunkDataStore.getState(index) == VxChunkDataStore.STATE_AWAITING_CHUNK) {
                shapeGenerator.requestRebuild(packedPos);
            }
        }
    }

    /**
     * Called when a Minecraft chunk is unloaded. This marks the corresponding physics
     * sections as awaiting the chunk, in case it is loaded again soon. The lifecycle
     * counter will eventually clean them up if they are no longer needed.
     *
     * @param chunkPos The position of the unloaded chunk.
     */
    public void handleChunkUnload(@NotNull ChunkPos chunkPos) {
        for (int y = level.getMinSection(); y < level.getMaxSection(); y++) {
            long packedPos = VxSectionPos.pack(chunkPos.x, y, chunkPos.z);
            Integer index = chunkDataStore.getIndexForPos(packedPos);
            if (index != null) {
                chunkDataStore.setState(index, VxChunkDataStore.STATE_AWAITING_CHUNK);
            }
        }
    }

    /**
     * Requests that a chunk section be managed by the system. This typically comes
     * from the terrain tracker. It resets the chunk's life counter and triggers
     * shape generation if needed.
     *
     * @param packedPos The packed position of the chunk section.
     */
    public void requestChunk(long packedPos) {
        int index = chunkDataStore.addChunk(packedPos);
        chunkDataStore.setLifeCounter(index, 100); // Refresh lifetime

        if (chunkDataStore.getState(index) == VxChunkDataStore.STATE_UNLOADED) {
            LevelChunk chunk = level.getChunkSource().getChunk(VxSectionPos.unpackX(packedPos), VxSectionPos.unpackZ(packedPos), false);
            if (chunk == null) {
                chunkDataStore.setState(index, VxChunkDataStore.STATE_AWAITING_CHUNK);
            } else {
                shapeGenerator.requestRebuild(packedPos);
            }
        }
    }

    /**
     * Internal method to unload the physics representation of a chunk section.
     *
     * @param packedPos The packed position of the section to unload.
     */
    private void unloadChunkPhysicsInternal(long packedPos) {
        Integer index = chunkDataStore.getIndexForPos(packedPos);
        if (index == null) return;

        chunkDataStore.setState(index, VxChunkDataStore.STATE_REMOVING);
        chunkDataStore.incrementAndGetRebuildVersion(index); // Invalidate any ongoing generation
        shapeGenerator.cancelRebuild(packedPos);

        physicsWorld.execute(() -> {
            removeBodyAndShape(index, physicsWorld.getBodyInterface());
            chunkDataStore.removeChunk(packedPos);
        });
    }

    /**
     * Safely removes a physics body and its associated shape from the world.
     *
     * @param index The index of the chunk in the data store.
     * @param bodyInterface The Jolt body interface.
     */
    private void removeBodyAndShape(int index, BodyInterface bodyInterface) {
        int bodyId = chunkDataStore.getBodyId(index);
        if (bodyId != VxChunkDataStore.UNUSED_BODY_ID && bodyInterface != null) {
            if (bodyInterface.isAdded(bodyId)) {
                bodyInterface.removeBody(bodyId);
            }
            bodyInterface.destroyBody(bodyId);
        }
        chunkDataStore.setBodyId(index, VxChunkDataStore.UNUSED_BODY_ID);
        // The shape reference is managed by the data store and will be closed on removal.
        chunkDataStore.setShape(index, null);
    }

    /**
     * Checks if a given body ID corresponds to a terrain body.
     *
     * @param bodyId The ID of the body to check.
     * @return True if it is a terrain body, false otherwise.
     */
    public boolean isTerrainBody(int bodyId) {
        if (bodyId <= 0 || bodyId == Jolt.cInvalidBodyId) return false;
        BodyInterface bodyInterface = physicsWorld.getPhysicsSystem().getBodyInterface();
        if (bodyInterface == null) return false;
        return bodyInterface.getUserData(bodyId) == TERRAIN_BODY_USER_DATA;
    }

    /**
     * Cleans up all terrain bodies managed by this instance.
     * Called during system shutdown.
     */
    public void cleanupAllBodies() {
        BodyInterface bi = physicsWorld.getBodyInterface();
        if (bi != null) {
            chunkDataStore.getManagedIndices().forEach(index -> removeBodyAndShape(index, bi));
        }
    }
}