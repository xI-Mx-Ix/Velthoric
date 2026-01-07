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
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.physics.body.manager.VxServerBodyDataStore;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.buoyancy.VxBuoyancyDataStore;
import net.xmx.velthoric.physics.buoyancy.VxFluidType;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

/**
 * Handles the broad-phase of buoyancy detection on the main game thread.
 * <p>
 * This class is responsible for scanning the Minecraft world to determine which physics bodies
 * are interacting with fluids (Water, Lava). It populates a data store with environmental
 * data such as surface height, submerged area fraction, and fluid flow direction.
 * <p>
 * <b>Performance Note:</b> This implementation is highly optimized for Garbage Collection (GC) efficiency.
 * It avoids allocating iterator objects or temporary vectors during the voxel scan. Instead of
 * calculating fluid flow vectors for every intersecting block (which creates excessive temporary objects),
 * it calculates the geometric centroid of the submerged volume and samples the flow vector exactly
 * once per body.
 *
 * @author xI-Mx-Ix
 */
public final class VxBuoyancyBroadPhase {

    private final VxPhysicsWorld physicsWorld;
    private final ServerLevel level;

    /**
     * Maximum number of blocks to search upwards for the fluid surface
     * if the body is fully submerged. This prevents infinite loops if a body falls into the void.
     */
    private static final int MAX_UPWARD_SEARCH = 16;

    /**
     * The fixed radius around the body's center position to scan for fluids.
     * Used as a fallback if the body's AABB is not yet valid (e.g. during initial spawn).
     */
    private static final float SCAN_RADIUS = 0.8f;

    /**
     * Constructs a new broad-phase handler.
     *
     * @param physicsWorld The physics world context.
     */
    public VxBuoyancyBroadPhase(VxPhysicsWorld physicsWorld) {
        this.physicsWorld = physicsWorld;
        this.level = physicsWorld.getLevel();
    }

    /**
     * Scans for dynamic bodies in contact with fluids using a garbage-free voxel iteration strategy.
     * <p>
     * It prioritizes the body's AABB for accurate volume scanning. If the AABB is invalid,
     * it falls back to a position-based radius scan. The results are written into the provided
     * {@link VxBuoyancyDataStore}.
     *
     * @param dataStore The data store to be populated with potential fluid contacts.
     */
    public void findPotentialFluidContacts(VxBuoyancyDataStore dataStore) {
        VxServerBodyDataStore ds = physicsWorld.getBodyManager().getDataStore();

        // Reusable mutable position to prevent allocation inside the loop.
        // This object is mutated thousands of times per tick but never discarded.
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        int capacity = ds.getCapacity();

        for (int i = 0; i < capacity; ++i) {
            // Early exit for static bodies (which don't float), invalid IDs, or inactive slots.
            if (ds.motionType[i] == EMotionType.Static || ds.getIdForIndex(i) == null || !ds.isActive[i]) {
                continue;
            }

            // Retrieve AABB coordinates directly from the primitive arrays in the data store.
            float minX = ds.aabbMinX[i];
            float minY = ds.aabbMinY[i];
            float minZ = ds.aabbMinZ[i];
            float maxX = ds.aabbMaxX[i];
            float maxY = ds.aabbMaxY[i];
            float maxZ = ds.aabbMaxZ[i];

            int minBlockX, maxBlockX;
            int minBlockY, maxBlockY;
            int minBlockZ, maxBlockZ;

            // The lowest point of the body to check against the water surface.
            // If the water surface is below this point, buoyancy is not applied.
            float bottomThreshold;

            // Check if AABB is valid (dimensions > epsilon).
            // Newly created bodies might have 0-sized AABBs before the first physics step.
            boolean hasValidAABB = (maxX - minX) > 1e-4f && (maxY - minY) > 1e-4f && (maxZ - minZ) > 1e-4f;

            if (hasValidAABB) {
                // USE AABB: Precise scanning based on actual physics bounds.
                minBlockX = (int) Math.floor(minX);
                maxBlockX = (int) Math.floor(maxX);
                minBlockY = (int) Math.floor(minY);
                maxBlockY = (int) Math.floor(maxY);
                minBlockZ = (int) Math.floor(minZ);
                maxBlockZ = (int) Math.floor(maxZ);
                bottomThreshold = minY;
            } else {
                // FALLBACK: Use position + SCAN_RADIUS.
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

            // Accumulators for surface height and geometric center calculation.
            float totalSurfaceHeight = 0;
            float sumX = 0;
            float sumZ = 0;

            int fluidColumnCount = 0;
            int totalScannedColumns = 0;
            VxFluidType detectedType = null;

            // --- Voxel Iteration ---
            // We iterate strictly with primitives to avoid Iterator allocations.
            // This loop scans the volume occupied by the body to find fluid blocks.
            for (int x = minBlockX; x <= maxBlockX; ++x) {

                // Optimization: Access chunk data directly via ChunkSource.
                // We use bit-shifting (x >> 4) to determine chunk coordinates.
                // This bypasses the potentially slow 'level.getBlockState()' which includes lighting checks etc.
                // Note: We only fetch the chunk once per X-column if Z doesn't cross a boundary, but for safety
                // regarding chunk boundaries in Z, we fetch/check inside the inner loop or handle it robustly.

                for (int z = minBlockZ; z <= maxBlockZ; ++z) {
                    totalScannedColumns++;

                    // Get the chunk immediately without waiting for disk I/O.
                    // If the chunk is not loaded, we skip this column to avoid stalling the main thread.
                    ChunkAccess chunk = this.level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                    if (chunk == null) continue;

                    float foundHeight = -1;

                    // 1. Check Top (Submersion check)
                    // We check the top-most block of the AABB first. If it is fluid, the body is likely fully submerged.
                    mutablePos.set(x, maxBlockY, z);
                    FluidState topFluid = chunk.getFluidState(mutablePos);

                    if (!topFluid.isEmpty()) {
                        // Body is submerged at the top: Search upwards to find where the water actually ends.
                        foundHeight = findSurfaceUpwards(chunk, x, maxBlockY, z, mutablePos);
                        if (detectedType == null) detectedType = getFluidTypeFromState(topFluid);
                    } else {
                        // 2. Scan Downwards
                        // The top is air, so we scan downwards within the AABB to find the water surface.
                        for (int y = maxBlockY - 1; y >= minBlockY; --y) {
                            mutablePos.set(x, y, z);
                            FluidState fluidState = chunk.getFluidState(mutablePos);
                            if (!fluidState.isEmpty()) {
                                // Found the surface.
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

                        // NOTE: We deliberately do NOT calculate flow here.
                        // Calling 'getFlow()' creates a new Vec3 object. Doing this 1000s of times
                        // causes massive GC pressure. We will sample it once later using the centroid.
                    }
                }
            }

            if (fluidColumnCount > 0 && detectedType != null) {
                float averageSurfaceHeight = totalSurfaceHeight / fluidColumnCount;

                // Ensure the water surface is actually above the bottom of the object.
                // If the object is hovering slightly above water (due to physics jitter), we don't apply buoyancy.
                if (averageSurfaceHeight > bottomThreshold) {
                    float areaFraction = (float) fluidColumnCount / totalScannedColumns;
                    float centerX = sumX / fluidColumnCount;
                    float centerZ = sumZ / fluidColumnCount;

                    // --- Centroid Flow Sampling ---
                    // Instead of summing flow vectors (expensive allocation inside loop), we sample the flow
                    // at the calculated center of buoyancy. This effectively reduces allocations
                    // from N (blocks) to 1 (body) while still providing accurate directional flow.
                    mutablePos.set(centerX, averageSurfaceHeight - 0.5f, centerZ);

                    // We must use 'level' here because getFlow accesses neighbors to determine slope.
                    // Since we are on the main thread and verified chunk existence above, this is safe.
                    Vec3 flow = level.getFluidState(mutablePos).getFlow(level, mutablePos);

                    UUID id = ds.getIdForIndex(i);
                    if (id != null) {
                        VxBody vxBody = physicsWorld.getBodyManager().getVxBody(id);
                        if (vxBody != null) {
                            dataStore.add(
                                    vxBody.getBodyId(),
                                    averageSurfaceHeight,
                                    detectedType,
                                    areaFraction,
                                    centerX,
                                    centerZ,
                                    (float) flow.x,
                                    (float) flow.y,
                                    (float) flow.z
                            );
                        }
                    }
                }
            }
        }
    }

    /**
     * Scans blocks vertically upwards from a submerged point to find the transition to air.
     * <p>
     * This handles cases where an object is deep underwater. We need the Y-level of the
     * surface to calculate the correct displacement depth.
     *
     * @param chunk  The chunk to read from.
     * @param x      The X coordinate.
     * @param startY The Y coordinate where fluid was first detected.
     * @param z      The Z coordinate.
     * @param pos    A mutable block pos to reuse for iteration.
     * @return The absolute Y height of the fluid surface.
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
        // If the surface is too far up, we return the top of the original search block
        // to prevent searching forever.
        return startY + 1.0f;
    }

    /**
     * Maps Minecraft fluid states to internal fluid types.
     *
     * @param state The Minecraft FluidState.
     * @return The corresponding VxFluidType, or null if not a valid fluid.
     */
    private VxFluidType getFluidTypeFromState(FluidState state) {
        if (state.is(FluidTags.WATER)) return VxFluidType.WATER;
        if (state.is(FluidTags.LAVA)) return VxFluidType.LAVA;
        return null;
    }
}