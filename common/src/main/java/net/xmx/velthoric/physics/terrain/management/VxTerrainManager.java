/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.management;

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.enumerate.EActivation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.xmx.velthoric.physics.terrain.VxUpdateContext;
import net.xmx.velthoric.physics.terrain.data.VxChunkDataStore;
import net.xmx.velthoric.physics.terrain.data.VxSectionPos;
import net.xmx.velthoric.physics.terrain.generation.VxTerrainShapeGenerator;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the state and lifecycle of terrain physics bodies. This includes loading, unloading,
 * activating, deactivating, and rebuilding chunk sections in response to game events.
 *
 * @author xI-Mx-Ix
 */
public class VxTerrainManager {

    private final VxPhysicsWorld physicsWorld;
    private final ServerLevel level;
    private final VxChunkDataStore chunkDataStore;
    private final VxTerrainShapeGenerator shapeGenerator;

    private final Set<VxSectionPos> chunksToRebuild = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<VxUpdateContext> updateContext = ThreadLocal.withInitial(VxUpdateContext::new);

    public VxTerrainManager(VxPhysicsWorld physicsWorld, ServerLevel level, VxChunkDataStore chunkDataStore, VxTerrainShapeGenerator shapeGenerator) {
        this.physicsWorld = physicsWorld;
        this.level = level;
        this.chunkDataStore = chunkDataStore;
        this.shapeGenerator = shapeGenerator;
    }

    /**
     * Processes chunks that have been marked for a rebuild due to block updates.
     */
    public void processRebuildQueue() {
        if (chunksToRebuild.isEmpty()) return;

        Set<VxSectionPos> batch = new HashSet<>(chunksToRebuild);
        chunksToRebuild.removeAll(batch);

        for (VxSectionPos pos : batch) {
            Integer index = chunkDataStore.getIndexForPos(pos);
            if (index != null) {
                shapeGenerator.scheduleShapeGeneration(pos, index);
            }
        }
    }

    /**
     * Handles a block update event in the world.
     *
     * @param worldPos The position of the updated block.
     */
    public void onBlockUpdate(BlockPos worldPos) {
        VxSectionPos pos = VxSectionPos.fromBlockPos(worldPos.immutable());
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index == null) return;

        int currentState = chunkDataStore.states[index];
        if (currentState == VxChunkDataStore.STATE_READY_ACTIVE || currentState == VxChunkDataStore.STATE_READY_INACTIVE || currentState == VxChunkDataStore.STATE_AIR_CHUNK) {
            chunksToRebuild.add(pos);
        }

        // Wake up nearby physics bodies
        physicsWorld.execute(() -> {
            BodyInterface bi = physicsWorld.getBodyInterface();
            if (bi == null) return;

            VxUpdateContext ctx = updateContext.get();
            ctx.vec3_1.set(worldPos.getX() - 2.0f, worldPos.getY() - 2.0f, worldPos.getZ() - 2.0f);
            ctx.vec3_2.set(worldPos.getX() + 3.0f, worldPos.getY() + 3.0f, worldPos.getZ() + 3.0f);

            ctx.aabox_1.setMin(ctx.vec3_1);
            ctx.aabox_1.setMax(ctx.vec3_2);

            bi.activateBodiesInAaBox(ctx.aabox_1, ctx.bplFilter, ctx.olFilter);
        });
    }

    /**
     * Handles the loading of a chunk from the vanilla game engine.
     *
     * @param chunk The loaded chunk.
     */
    public void onChunkLoadedFromVanilla(@NotNull LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        for (int y = level.getMinSection(); y < level.getMaxSection(); ++y) {
            VxSectionPos vPos = new VxSectionPos(chunkPos.x, y, chunkPos.z);
            Integer index = chunkDataStore.getIndexForPos(vPos);
            if (index != null && chunkDataStore.referenceCounts[index] > 0) {
                shapeGenerator.scheduleShapeGeneration(vPos, index);
            }
        }
    }

    /**
     * Handles the unloading of a chunk from the vanilla game engine.
     *
     * @param chunkPos The position of the unloaded chunk.
     */
    public void onChunkUnloaded(@NotNull ChunkPos chunkPos) {
        for (int y = level.getMinSection(); y < level.getMaxSection(); y++) {
            unloadChunkPhysicsInternal(new VxSectionPos(chunkPos.x, y, chunkPos.z));
        }
    }

    /**
     * Activates a chunk section, adding its physics body to the simulation.
     *
     * @param index The data store index of the chunk to activate.
     */
    public void activateChunk(int index) {
        if (chunkDataStore.states[index] == VxChunkDataStore.STATE_AIR_CHUNK) {
            return;
        }

        if (chunkDataStore.bodyIds[index] != VxChunkDataStore.UNUSED_BODY_ID && chunkDataStore.states[index] == VxChunkDataStore.STATE_READY_INACTIVE) {
            chunkDataStore.states[index] = VxChunkDataStore.STATE_READY_ACTIVE;
            physicsWorld.execute(() -> {
                BodyInterface bodyInterface = physicsWorld.getBodyInterface();
                int bodyId = chunkDataStore.bodyIds[index];
                if (bodyInterface != null && bodyId != VxChunkDataStore.UNUSED_BODY_ID && !bodyInterface.isAdded(bodyId)) {
                    bodyInterface.addBody(bodyId, EActivation.Activate);
                }
            });
        }
    }

    /**
     * Deactivates a chunk section, removing its physics body from the simulation.
     *
     * @param index The data store index of the chunk to deactivate.
     */
    public void deactivateChunk(int index) {
        if (chunkDataStore.bodyIds[index] != VxChunkDataStore.UNUSED_BODY_ID && chunkDataStore.states[index] == VxChunkDataStore.STATE_READY_ACTIVE) {
            chunkDataStore.states[index] = VxChunkDataStore.STATE_READY_INACTIVE;
            physicsWorld.execute(() -> {
                BodyInterface bodyInterface = physicsWorld.getBodyInterface();
                int bodyId = chunkDataStore.bodyIds[index];
                if (bodyInterface != null && bodyId != VxChunkDataStore.UNUSED_BODY_ID && bodyInterface.isAdded(bodyId)) {
                    bodyInterface.removeBody(bodyId);
                }
            });
        }
    }

    /**
     * Requests that a chunk section be loaded into the physics system.
     *
     * @param pos The position of the chunk section.
     */
    public void requestChunk(VxSectionPos pos) {
        int index = chunkDataStore.addChunk(pos);
        if (++chunkDataStore.referenceCounts[index] == 1) {
            shapeGenerator.scheduleShapeGeneration(pos, index);
        }
    }

    /**
     * Releases a reference to a chunk section. If the reference count drops to zero,
     * the chunk is unloaded from the physics system.
     *
     * @param pos The position of the chunk section.
     */
    public void releaseChunk(VxSectionPos pos) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index != null && --chunkDataStore.referenceCounts[index] == 0) {
            unloadChunkPhysicsInternal(pos);
        }
    }

    /**
     * Unloads a chunk section and cleans up its associated physics resources.
     *
     * @param pos The position of the chunk section to unload.
     */
    private void unloadChunkPhysicsInternal(VxSectionPos pos) {
        Integer index = chunkDataStore.getIndexForPos(pos);
        if (index == null) return;

        chunkDataStore.states[index] = VxChunkDataStore.STATE_REMOVING;
        chunkDataStore.rebuildVersions[index]++; // Invalidate any pending generation
        chunksToRebuild.remove(pos);

        physicsWorld.execute(() -> {
            removeBodyAndShape(index, physicsWorld.getBodyInterface());
            chunkDataStore.removeChunk(pos);
        });
    }

    /**
     * Removes the physics body and shape associated with a given index.
     *
     * @param index         The data store index.
     * @param bodyInterface The body interface for physics operations.
     */
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
     * Checks if a specific terrain body ID belongs to this system.
     * @param bodyId The body ID to check.
     * @return True if it is a terrain body, false otherwise.
     */
    public boolean isTerrainBody(int bodyId) {
        if (bodyId <= 0) return false;
        // This is a fast check that doesn't require synchronization
        for (int id : chunkDataStore.bodyIds) {
            if (id == bodyId) return true;
        }
        return false;
    }

    /**
     * Cleans up all managed terrain bodies from the physics world.
     */
    public void cleanupAllBodies() {
        BodyInterface bi = physicsWorld.getBodyInterface();
        if (bi != null) {
            chunkDataStore.getManagedPositions().forEach(pos -> {
                Integer index = chunkDataStore.getIndexForPos(pos);
                if (index != null) {
                    removeBodyAndShape(index, bi);
                }
            });
        }
    }
}