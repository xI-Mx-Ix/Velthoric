/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.buoyancy;

import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.xmx.velthoric.physics.object.manager.VxObjectDataStore;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * Handles the broad-phase of buoyancy detection on the main game thread.
 * This class efficiently scans for bodies that are potentially in a fluid, using
 * optimizations like chunk-caching to minimize expensive world lookups.
 *
 * @author xI-Mx-Ix
 */
public final class VxBuoyancyBroadPhase {

    private final VxPhysicsWorld physicsWorld;
    private final ServerLevel level;

    /**
     * Constructs a new broad-phase handler.
     * @param physicsWorld The physics world to operate on.
     */
    public VxBuoyancyBroadPhase(VxPhysicsWorld physicsWorld) {
        this.physicsWorld = physicsWorld;
        this.level = physicsWorld.getLevel();
    }

    /**
     * Scans all physics objects and identifies those potentially inside a fluid.
     *
     * @return A {@link VxBuoyancyResult} object containing the lists of bodies in fluid and their properties.
     */
    public VxBuoyancyResult findPotentialFluidContacts() {
        VxObjectDataStore ds = physicsWorld.getObjectManager().getDataStore();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        IntArrayList bodiesFound = new IntArrayList();

        Int2FloatMap newSurfaceHeights = new Int2FloatOpenHashMap();
        Int2ObjectMap<VxFluidType> newFluidTypes = new Int2ObjectOpenHashMap<>();
        LongSet dryChunkCache = new LongOpenHashSet();

        for (int i = 0; i < ds.getCapacity(); ++i) {
            UUID id = ds.getIdForIndex(i);
            if (id == null) continue;

            VxBody body = physicsWorld.getObjectManager().getObject(id);
            if (body == null) continue;

            int bodyId = body.getBodyId();
            if (bodyId == 0) continue;

            long chunkPos = BlockPos.asLong((int)ds.posX[i] >> 4, 0, (int)ds.posZ[i] >> 4);
            if (dryChunkCache.contains(chunkPos)) {
                continue;
            }

            mutablePos.set(ds.posX[i], ds.posY[i] + 0.1, ds.posZ[i]);
            FluidState fluidState = this.level.getFluidState(mutablePos);

            VxFluidType detectedType = null;
            if (fluidState.is(FluidTags.WATER)) {
                detectedType = VxFluidType.WATER;
            } else if (fluidState.is(FluidTags.LAVA)) {
                detectedType = VxFluidType.LAVA;
            }

            if (detectedType != null && !fluidState.isEmpty()) {
                bodiesFound.add(bodyId);
                // Use getHeight, which is the correct method in the provided FluidState class.
                float surfaceHeight = mutablePos.getY() + fluidState.getHeight(level, mutablePos) - 0.1f;
                newSurfaceHeights.put(bodyId, surfaceHeight);
                newFluidTypes.put(bodyId, detectedType);
            } else {
                dryChunkCache.add(chunkPos);
            }
        }

        return new VxBuoyancyResult(bodiesFound.toIntArray(), newSurfaceHeights, newFluidTypes);
    }
}