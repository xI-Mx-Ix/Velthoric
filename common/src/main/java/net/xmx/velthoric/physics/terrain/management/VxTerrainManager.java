/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.management;

import com.github.stephengold.joltjni.Body;
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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages the state and lifecycle of terrain physics bodies. It uses packed longs
 * for positions to minimize object allocations and GC pressure.
 *
 * @author xI-Mx-Ix
 */
public class VxTerrainManager {

    private final VxPhysicsWorld physicsWorld;
    private final ServerLevel level;
    private final VxChunkDataStore chunkDataStore;
    private final VxTerrainShapeGenerator shapeGenerator;

    private final Queue<Long> chunksToRebuild = new ConcurrentLinkedQueue<>();
    private static final ThreadLocal<VxUpdateContext> updateContext = ThreadLocal.withInitial(VxUpdateContext::new);

    public static final long TERRAIN_BODY_USER_DATA = 0x5445525241494E42L; // "TERRAINB"

    public VxTerrainManager(VxPhysicsWorld physicsWorld, ServerLevel level, VxChunkDataStore chunkDataStore, VxTerrainShapeGenerator shapeGenerator) {
        this.physicsWorld = physicsWorld;
        this.level = level;
        this.chunkDataStore = chunkDataStore;
        this.shapeGenerator = shapeGenerator;
    }

    public void processRebuildQueue() {
        int processed = 0;
        final int maxToProcess = 128; // Process more chunks per tick
        while (processed < maxToProcess && !chunksToRebuild.isEmpty()) {
            Long packedPos = chunksToRebuild.poll();
            if (packedPos != null) {
                Integer index = chunkDataStore.getIndexForPos(packedPos);
                if (index != null) {
                    shapeGenerator.scheduleShapeGeneration(packedPos, index);
                }
                processed++;
            }
        }
    }

    public void onBlockUpdate(BlockPos worldPos) {
        long packedPos = VxSectionPos.fromBlockPos(worldPos.immutable());
        Integer index = chunkDataStore.getIndexForPos(packedPos);
        if (index == null) return;

        int currentState = chunkDataStore.states[index];
        if (currentState == VxChunkDataStore.STATE_READY_ACTIVE || currentState == VxChunkDataStore.STATE_READY_INACTIVE || currentState == VxChunkDataStore.STATE_AIR_CHUNK) {
            if (!chunksToRebuild.contains(packedPos)) {
                chunksToRebuild.add(packedPos);
            }
        }

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
            if (index != null && chunkDataStore.referenceCounts[index] > 0) {
                shapeGenerator.scheduleShapeGeneration(packedPos, index);
            }
        }
    }

    public void onChunkUnloaded(@NotNull ChunkPos chunkPos) {
        for (int y = level.getMinSection(); y < level.getMaxSection(); y++) {
            unloadChunkPhysicsInternal(VxSectionPos.pack(chunkPos.x, y, chunkPos.z));
        }
    }

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

    public void requestChunk(long packedPos) {
        int index = chunkDataStore.addChunk(packedPos);
        if (++chunkDataStore.referenceCounts[index] == 1) {
            shapeGenerator.scheduleShapeGeneration(packedPos, index);
        }
    }

    public void releaseChunk(long packedPos) {
        Integer index = chunkDataStore.getIndexForPos(packedPos);
        if (index != null && --chunkDataStore.referenceCounts[index] == 0) {
            unloadChunkPhysicsInternal(packedPos);
        }
    }

    private void unloadChunkPhysicsInternal(long packedPos) {
        Integer index = chunkDataStore.getIndexForPos(packedPos);
        if (index == null) return;

        chunkDataStore.states[index] = VxChunkDataStore.STATE_REMOVING;
        chunkDataStore.rebuildVersions[index]++;
        chunksToRebuild.remove(packedPos);

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
        if (bodyId <= 0) return false;
        BodyInterface bodyInterface = physicsWorld.getPhysicsSystem().getBodyInterface();
        if (bodyInterface == null) return false;
        return bodyInterface.getUserData(bodyId) == TERRAIN_BODY_USER_DATA;
    }

    public void cleanupAllBodies() {
        BodyInterface bi = physicsWorld.getBodyInterface();
        if (bi != null) {
            chunkDataStore.getManagedPositions().forEach(packedPos -> {
                Integer index = chunkDataStore.getIndexForPos(packedPos);
                if (index != null) {
                    removeBodyAndShape(index, bi);
                }
            });
        }
    }
}