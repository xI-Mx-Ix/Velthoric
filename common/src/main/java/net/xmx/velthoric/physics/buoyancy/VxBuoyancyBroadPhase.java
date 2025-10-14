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

    /**
     * Scans the AABBs of all dynamic bodies to identify those potentially in contact with a fluid.
     * For each potential contact, it calculates an average fluid surface height based on the fluid
     * columns underneath the body. This approach provides a more realistic buoyancy plane,
     * especially for objects in uneven or flowing fluids.
     *
     * @param dataStore The data store to be populated with potential fluid contacts.
     */
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

            float totalSurfaceHeight = 0;
            int fluidColumnCount = 0;
            VxFluidType detectedType = null;

            int minBlockX = (int) Math.floor(minX);
            int maxBlockX = (int) Math.floor(maxX);
            int minBlockY = (int) Math.floor(minY);
            int maxBlockY = (int) Math.floor(maxY);
            int minBlockZ = (int) Math.floor(minZ);
            int maxBlockZ = (int) Math.floor(maxZ);

            for (int x = minBlockX; x <= maxBlockX; ++x) {
                for (int z = minBlockZ; z <= maxBlockZ; ++z) {
                    // Scan downwards from the top of the AABB to find the fluid surface in this column.
                    for (int y = maxBlockY; y >= minBlockY; --y) {
                        mutablePos.set(x, y, z);
                        FluidState fluidState = this.level.getFluidState(mutablePos);

                        if (!fluidState.isEmpty()) {
                            totalSurfaceHeight += y + fluidState.getHeight(level, mutablePos);
                            fluidColumnCount++;

                            if (fluidState.is(FluidTags.WATER)) {
                                detectedType = VxFluidType.WATER;
                            } else if (fluidState.is(FluidTags.LAVA)) {
                                detectedType = VxFluidType.LAVA;
                            }
                            // Once the surface is found for this column, move to the next.
                            break;
                        }
                    }
                }
            }

            if (fluidColumnCount > 0 && detectedType != null) {
                float averageSurfaceHeight = totalSurfaceHeight / fluidColumnCount;

                // Only consider the body for buoyancy if its bottom is below the average surface.
                if (averageSurfaceHeight > minY) {
                    VxBody vxBody = physicsWorld.getBodyManager().getVxBody(id);
                    if (vxBody != null) {
                        dataStore.add(vxBody.getBodyId(), averageSurfaceHeight, detectedType);
                    }
                }
            }
        }
    }
}