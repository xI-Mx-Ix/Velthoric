/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.management;

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.Jolt;
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

    private static final ThreadLocal<VxUpdateContext> updateContext = ThreadLocal.withInitial(VxUpdateContext::new);

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
        for (int index : chunkDataStore.getManagedIndices()) {
            if (chunkDataStore.lifeCounters[index] > 0) {
                chunkDataStore.lifeCounters[index]--;
            } else {
                long pos = chunkDataStore.getPosForIndex(index);
                if (pos != 0L) {
                    unloadChunkPhysicsInternal(pos);
                }
            }
        }
    }

    public void onBlockUpdate(BlockPos worldPos) {
        long packedPos = VxSectionPos.fromBlockPos(worldPos.immutable());
        Integer index = chunkDataStore.getIndexForPos(packedPos);
        if (index == null) return;

        int currentState = chunkDataStore.states[index];
        // Trigger a rebuild if the chunk section is in a valid, active state.
        if (currentState >= VxChunkDataStore.STATE_READY_INACTIVE && currentState != VxChunkDataStore.STATE_REMOVING) {
            shapeGenerator.requestRebuild(packedPos);
        }

        // Wake up any nearby physics bodies that might be sleeping.
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

    public void onChunkLoadedFromVanilla(@NotNull LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        for (int y = level.getMinSection(); y < level.getMaxSection(); ++y) {
            long packedPos = VxSectionPos.pack(chunkPos.x, y, chunkPos.z);
            Integer index = chunkDataStore.getIndexForPos(packedPos);
            if (index != null && chunkDataStore.states[index] == VxChunkDataStore.STATE_AWAITING_CHUNK) {
                shapeGenerator.scheduleShapeGeneration(packedPos, index);
            }
        }
    }

    public void onChunkUnloaded(@NotNull ChunkPos chunkPos) {
        for (int y = level.getMinSection(); y < level.getMaxSection(); y++) {
            long packedPos = VxSectionPos.pack(chunkPos.x, y, chunkPos.z);
            Integer index = chunkDataStore.getIndexForPos(packedPos);
            if (index != null) {
                // If the chunk is unloaded in Minecraft, we mark it as waiting in case it's loaded again soon.
                // The lifecycle counter will eventually clean it up if it's no longer needed.
                chunkDataStore.states[index] = VxChunkDataStore.STATE_AWAITING_CHUNK;
            }
        }
    }

    public void requestChunk(long packedPos) {
        int index = chunkDataStore.addChunk(packedPos);
        chunkDataStore.lifeCounters[index] = 100; // Refresh lifetime

        if (chunkDataStore.states[index] == VxChunkDataStore.STATE_UNLOADED) {
            LevelChunk chunk = level.getChunkSource().getChunk(VxSectionPos.unpackX(packedPos), VxSectionPos.unpackZ(packedPos), false);
            if (chunk == null) {
                chunkDataStore.states[index] = VxChunkDataStore.STATE_AWAITING_CHUNK;
            } else {
                shapeGenerator.scheduleShapeGeneration(packedPos, index);
            }
        }
    }

    private void unloadChunkPhysicsInternal(long packedPos) {
        Integer index = chunkDataStore.getIndexForPos(packedPos);
        if (index == null) return;

        chunkDataStore.states[index] = VxChunkDataStore.STATE_REMOVING;
        chunkDataStore.rebuildVersions[index]++;
        shapeGenerator.cancelRebuild(packedPos);

        physicsWorld.execute(() -> {
            removeBodyAndShape(index, physicsWorld.getBodyInterface());
            chunkDataStore.removeChunk(packedPos);
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

    public boolean isTerrainBody(int bodyId) {
        if (bodyId <= 0 || bodyId == Jolt.cInvalidBodyId) return false;
        BodyInterface bodyInterface = physicsWorld.getPhysicsSystem().getBodyInterface();
        if (bodyInterface == null) return false;
        return bodyInterface.getUserData(bodyId) == TERRAIN_BODY_USER_DATA;
    }

    public void cleanupAllBodies() {
        BodyInterface bi = physicsWorld.getBodyInterface();
        if (bi != null) {
            chunkDataStore.getManagedIndices().forEach(index -> removeBodyAndShape(index, bi));
        }
    }
}