/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.buoyancy.phase;

import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.FluidState;
import net.xmx.velthoric.physics.body.manager.VxBodyDataStore;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.buoyancy.VxBuoyancyDataStore;
import net.xmx.velthoric.physics.buoyancy.VxFluidType;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * Handles the broad-phase of buoyancy detection on the main game thread.
 * This class scans for bodies in fluids by checking world data against body AABBs.
 * It features a vertical search to find the true fluid surface for submerged objects.
 *
 * @author xI-Mx-Ix
 */
public final class VxBuoyancyBroadPhase {

    private final VxPhysicsWorld physicsWorld;
    private final ServerLevel level;

    /**
     * Maximum number of blocks to search upwards for the fluid surface
     * if the body is fully submerged.
     */
    private static final int MAX_UPWARD_SEARCH = 16;

    /**
     * The fixed radius around the body's center position to scan for fluids.
     * Used as a fallback if the body's AABB is not yet valid (e.g. 0,0,0).
     */
    private static final float SCAN_RADIUS = 0.8f;

    public VxBuoyancyBroadPhase(VxPhysicsWorld physicsWorld) {
        this.physicsWorld = physicsWorld;
        this.level = physicsWorld.getLevel();
    }

    /**
     * Scans for dynamic bodies in contact with fluids.
     * It prioritizes the body's AABB for accurate volume scanning. If the AABB is invalid
     * (e.g., initialization pending), it falls back to a position-based radius scan.
     *
     * @param dataStore The data store to be populated with potential fluid contacts.
     */
    public void findPotentialFluidContacts(VxBuoyancyDataStore dataStore) {
        VxBodyDataStore ds = physicsWorld.getBodyManager().getDataStore();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < ds.getCapacity(); ++i) {
            // Early exit for static bodies or inactive slots.
            if (ds.motionType[i] == EMotionType.Static || ds.getIdForIndex(i) == null || !ds.isActive[i]) {
                continue;
            }

            // Retrieve AABB from data store
            float minX = ds.aabbMinX[i];
            float minY = ds.aabbMinY[i];
            float minZ = ds.aabbMinZ[i];
            float maxX = ds.aabbMaxX[i];
            float maxY = ds.aabbMaxY[i];
            float maxZ = ds.aabbMaxZ[i];

            int minBlockX, maxBlockX;
            int minBlockY, maxBlockY;
            int minBlockZ, maxBlockZ;

            // The lowest point of the body to check against the water surface
            float bottomThreshold;

            // Check if AABB is valid (dimensions > epsilon)
            boolean hasValidAABB = (maxX - minX) > 1e-4f && (maxY - minY) > 1e-4f && (maxZ - minZ) > 1e-4f;

            if (hasValidAABB) {
                // USE AABB: Precise scanning based on physics bounds
                minBlockX = (int) Math.floor(minX);
                maxBlockX = (int) Math.floor(maxX);
                minBlockY = (int) Math.floor(minY);
                maxBlockY = (int) Math.floor(maxY);
                minBlockZ = (int) Math.floor(minZ);
                maxBlockZ = (int) Math.floor(maxZ);
                bottomThreshold = minY;
            } else {
                // FALLBACK: Use position + SCAN_RADIUS
                double posX = ds.posX[i];
                double posY = ds.posY[i];
                double posZ = ds.posZ[i];

                minBlockX = (int) Math.floor(posX - SCAN_RADIUS);
                maxBlockX = (int) Math.floor(posX + SCAN_RADIUS);
                minBlockY = (int) Math.floor(posY - SCAN_RADIUS);
                maxBlockY = (int) Math.floor(posY + SCAN_RADIUS);
                minBlockZ = (int) Math.floor(posZ - SCAN_RADIUS);
                maxBlockZ = (int) Math.floor(posZ + SCAN_RADIUS);
                bottomThreshold = (float) posY - SCAN_RADIUS;
            }

            float totalSurfaceHeight = 0;
            int fluidColumnCount = 0;
            int totalScannedColumns = 0;
            float sumX = 0;
            float sumZ = 0;
            VxFluidType detectedType = null;

            for (int x = minBlockX; x <= maxBlockX; ++x) {
                for (int z = minBlockZ; z <= maxBlockZ; ++z) {
                    totalScannedColumns++;

                    // This is the safest way to access chunk data without causing the server thread to wait for I/O.
                    ChunkAccess chunk = this.level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                    if (chunk == null) continue;

                    float foundHeight = -1;

                    // Check top for submersion
                    mutablePos.set(x, maxBlockY, z);
                    FluidState topFluid = chunk.getFluidState(mutablePos);

                    if (!topFluid.isEmpty()) {
                        foundHeight = findSurfaceUpwards(chunk, x, maxBlockY, z, mutablePos);
                        if (detectedType == null) detectedType = getFluidTypeFromState(topFluid);
                    } else {
                        // Scan downwards
                        for (int y = maxBlockY - 1; y >= minBlockY; --y) {
                            mutablePos.set(x, y, z);
                            FluidState fluidState = chunk.getFluidState(mutablePos);
                            if (!fluidState.isEmpty()) {
                                foundHeight = y + fluidState.getHeight(level, mutablePos);
                                if (detectedType == null) detectedType = getFluidTypeFromState(fluidState);
                                break;
                            }
                        }
                    }

                    if (foundHeight != -1) {
                        totalSurfaceHeight += foundHeight;
                        sumX += x + 0.5f; // Center of the block
                        sumZ += z + 0.5f;
                        fluidColumnCount++;
                    }
                }
            }

            if (fluidColumnCount > 0 && detectedType != null) {
                float averageSurfaceHeight = totalSurfaceHeight / fluidColumnCount;
                float areaFraction = (float) fluidColumnCount / totalScannedColumns;
                float centerX = sumX / fluidColumnCount;
                float centerZ = sumZ / fluidColumnCount;

                // Ensure the water surface is actually above the bottom of the object
                if (averageSurfaceHeight > bottomThreshold) {
                    UUID id = ds.getIdForIndex(i);
                    if (id == null) continue;

                    VxBody vxBody = physicsWorld.getBodyManager().getVxBody(id);
                    if (vxBody != null) {
                        dataStore.add(vxBody.getBodyId(), averageSurfaceHeight, detectedType, areaFraction, centerX, centerZ);
                    }
                }
            }
        }
    }

    /**
     * Scans blocks vertically upwards from a submerged point to find the transition to air.
     */
    private float findSurfaceUpwards(ChunkAccess chunk, int x, int startY, int z, BlockPos.MutableBlockPos pos) {
        for (int y = startY + 1; y <= startY + MAX_UPWARD_SEARCH; y++) {
            pos.set(x, y, z);
            FluidState state = chunk.getFluidState(pos);
            if (state.isEmpty()) {
                // Air found: the surface is the top of the fluid in the block below.
                pos.set(x, y - 1, z);
                FluidState below = chunk.getFluidState(pos);
                return (y - 1) + below.getHeight(level, pos);
            }
        }
        return startY + 1.0f; // Return top of original search block if surface is too deep.
    }

    /**
     * Maps Minecraft fluid states to internal fluid types.
     */
    private VxFluidType getFluidTypeFromState(FluidState state) {
        if (state.is(FluidTags.WATER)) return VxFluidType.WATER;
        if (state.is(FluidTags.LAVA)) return VxFluidType.LAVA;
        return null;
    }
}