/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.buoyancy;

import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.xmx.velthoric.physics.body.manager.VxBodyDataStore;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * Handles the broad-phase of buoyancy detection on the main game thread.
 * This class efficiently scans for bodies that are potentially in a fluid by
 * reading all body state directly from the cached data store, eliminating all JNI calls.
 *
 * @author xI-Mx-Ix
 */
public final class VxBuoyancyBroadPhase {

    private final VxPhysicsWorld physicsWorld;
    private final ServerLevel level;

    public VxBuoyancyBroadPhase(VxPhysicsWorld physicsWorld) {
        this.physicsWorld = physicsWorld;
        this.level = physicsWorld.getLevel();
    }

    public void findPotentialFluidContacts(VxBuoyancyDataStore dataStore) {
        VxBodyDataStore ds = physicsWorld.getBodyManager().getDataStore();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < ds.getCapacity(); ++i) {
            // Early exit for static bodies using the cached motion type. No JNI call needed.
            if (ds.motionType[i] == EMotionType.Static) {
                continue;
            }

            UUID id = ds.getIdForIndex(i);
            if (id == null) {
                continue;
            }

            // Read the AABB directly from the cached data store.
            float minX = ds.aabbMinX[i];
            float minY = ds.aabbMinY[i];
            float minZ = ds.aabbMinZ[i];
            float maxX = ds.aabbMaxX[i];
            float maxY = ds.aabbMaxY[i];
            float maxZ = ds.aabbMaxZ[i];

            if (minX >= maxX || minY >= maxY || minZ >= maxZ) {
                continue;
            }

            float highestSurface = -Float.MAX_VALUE;
            VxFluidType detectedType = null;
            boolean fluidFoundInBounds = false;

            int minBlockX = (int) Math.floor(minX);
            int maxBlockX = (int) Math.floor(maxX);
            int minBlockY = (int) Math.floor(minY);
            int maxBlockY = (int) Math.floor(maxY);
            int minBlockZ = (int) Math.floor(minZ);
            int maxBlockZ = (int) Math.floor(maxZ);

            for (int x = minBlockX; x <= maxBlockX; ++x) {
                for (int z = minBlockZ; z <= maxBlockZ; ++z) {
                    for (int y = maxBlockY; y >= minBlockY; --y) {
                        mutablePos.set(x, y, z);
                        FluidState fluidState = this.level.getFluidState(mutablePos);

                        if (!fluidState.isEmpty()) {
                            fluidFoundInBounds = true;
                            float surfaceHeight = y + fluidState.getHeight(level, mutablePos);
                            if (surfaceHeight > highestSurface) {
                                highestSurface = surfaceHeight;
                                if (fluidState.is(FluidTags.WATER)) {
                                    detectedType = VxFluidType.WATER;
                                } else if (fluidState.is(FluidTags.LAVA)) {
                                    detectedType = VxFluidType.LAVA;
                                }
                            }
                            break;
                        }
                    }
                }
            }

            if (fluidFoundInBounds && detectedType != null) {
                if (highestSurface > minY) {
                    VxBody vxBody = physicsWorld.getBodyManager().getVxBody(id);
                    if (vxBody != null) {
                        dataStore.add(vxBody.getBodyId(), highestSurface, detectedType);
                    }
                }
            }
        }
    }
}