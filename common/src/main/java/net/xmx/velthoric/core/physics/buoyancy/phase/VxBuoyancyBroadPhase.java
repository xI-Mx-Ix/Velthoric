/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.physics.buoyancy.phase;

import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FlowingFluid;
import net.xmx.velthoric.core.behavior.VxBehaviors;
import net.xmx.velthoric.core.body.server.VxServerBodyDataStore;
import net.xmx.velthoric.core.body.server.VxServerBodyDataContainer;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.physics.buoyancy.VxBuoyancyDataStore;
import net.xmx.velthoric.core.physics.buoyancy.VxFluidType;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Handles the broad-phase of buoyancy detection on the main game thread.
 * <p>
 * This class is responsible for scanning the Minecraft world to determine which physics bodies
 * are interacting with fluids (Water, Lava). It populates a data store with environmental
 * data such as surface height, submerged area fraction, and fluid flow direction.
 * <p>
 * This implementation is optimized for garbage collection efficiency by avoiding the allocation
 * of iterator objects or temporary vectors during the voxel scan. Instead of calculating fluid
 * flow vectors for every intersecting block, it calculates the geometric centroid of the
 * submerged volume and samples the flow vector using a low-allocation method once per body.
 *
 * @author xI-Mx-Ix
 */
public final class VxBuoyancyBroadPhase {

    /**
     * The physics world containing the bodies to be processed.
     */
    private final VxPhysicsWorld physicsWorld;

    /**
     * The server level used to query block and fluid states.
     */
    private final ServerLevel level;

    /**
     * Maximum number of blocks to search upwards for the fluid surface if a body is fully submerged.
     */
    private static final int MAX_UPWARD_SEARCH = 16;

    /**
     * The fixed radius around the body's center position to scan for fluids if the bounding box is invalid.
     */
    private static final float SCAN_RADIUS = 0.8f;

    /**
     * A reusable mutable block position used to query neighboring blocks for flow calculations.
     */
    private final BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();

    /**
     * A reusable vector to store fluid flow directions for each body.
     */
    private final Vector3f flowVector = new Vector3f();

    private static final FluidState EMPTY_FLUID = Fluids.EMPTY.defaultFluidState();

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
     * Scans for dynamic bodies in contact with fluids using a voxel iteration strategy.
     * <p>
     * It uses the body's axis-aligned bounding box (AABB) for volume scanning. If the AABB
     * is not yet valid, it falls back to a scan within a fixed radius. The results including
     * surface height and fluid flow are written into the provided data store.
     *
     * @param dataStore The data store to be populated with fluid contacts.
     */
    public void findPotentialFluidContacts(VxBuoyancyDataStore dataStore) {
        VxServerBodyDataStore ds = physicsWorld.getBodyManager().getDataStore();
        VxServerBodyDataContainer c = ds.serverCurrent();

        // Reusable mutable position used for volume iteration.
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        int capacity = c.getCapacity();

        for (int i = 0; i < capacity; ++i) {
            // Static bodies or inactive slots are ignored.
            if (c.motionType[i] == EMotionType.Static || ds.getIdForIndex(i) == null || !c.isActive[i]) {
                continue;
            }
            if ((c.behaviorBits[i] & VxBehaviors.BUOYANCY.getMask()) == 0) continue;

            // Retrieve world-space bounds from the data store.
            float minX = c.aabbMinX[i];
            float minY = c.aabbMinY[i];
            float minZ = c.aabbMinZ[i];
            float maxX = c.aabbMaxX[i];
            float maxY = c.aabbMaxY[i];
            float maxZ = c.aabbMaxZ[i];

            int minBlockX, maxBlockX;
            int minBlockY, maxBlockY;
            int minBlockZ, maxBlockZ;

            float bottomThreshold;

            // Determine if the bounding box is large enough for a valid scan.
            boolean hasValidAABB = (maxX - minX) > 1e-4f && (maxY - minY) > 1e-4f && (maxZ - minZ) > 1e-4f;

            if (hasValidAABB) {
                // Use the precise physics bounding box for block coordinate calculation.
                minBlockX = (int) Math.floor(minX);
                maxBlockX = (int) Math.floor(maxX);
                minBlockY = (int) Math.floor(minY);
                maxBlockY = (int) Math.floor(maxY);
                minBlockZ = (int) Math.floor(minZ);
                maxBlockZ = (int) Math.floor(maxZ);
                bottomThreshold = minY;
            } else {
                // Use a default radius if the bounding box is not yet initialized.
                double posX = c.posX[i];
                double posY = c.posY[i];
                double posZ = c.posZ[i];

                minBlockX = (int) Math.floor(posX - SCAN_RADIUS);
                maxBlockX = (int) Math.floor(posX + SCAN_RADIUS);
                minBlockY = (int) Math.floor(posY - SCAN_RADIUS);
                maxBlockY = (int) Math.floor(posY + SCAN_RADIUS);
                minBlockZ = (int) Math.floor(posZ - SCAN_RADIUS);
                maxBlockZ = (int) Math.floor(posZ + SCAN_RADIUS);
                bottomThreshold = (float) posY - SCAN_RADIUS;
            }

            float totalSurfaceHeight = 0;
            float sumX = 0;
            float sumZ = 0;

            int fluidColumnCount = 0;
            int totalScannedColumns = 0;
            VxFluidType detectedType = null;

            // Iterate through every block column covered by the body.
            for (int x = minBlockX; x <= maxBlockX; ++x) {
                for (int z = minBlockZ; z <= maxBlockZ; ++z) {
                    totalScannedColumns++;

                    // Retrieve chunk data directly to avoid blocking or heavy queries.
                    ChunkAccess chunk = this.level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                    if (chunk == null) continue;

                    float foundHeight = -1;

                    // Check the top-most point for submersion.
                    FluidState topFluid = getFluidStateFromContainer(chunk, x, maxBlockY, z);

                    if (!topFluid.isEmpty()) {
                        // Scan upwards to find the true surface if the body is fully under fluid.
                        foundHeight = findSurfaceUpwards(chunk, x, maxBlockY, z, mutablePos);
                        if (detectedType == null) detectedType = getFluidTypeFromState(topFluid);
                    } else {
                        // Scan downwards within the volume to find the fluid surface.
                        for (int y = maxBlockY - 1; y >= minBlockY; --y) {
                            FluidState fluidState = getFluidStateFromContainer(chunk, x, y, z);
                            if (!fluidState.isEmpty()) {
                                mutablePos.set(x, y, z);
                                foundHeight = y + fluidState.getHeight(level, mutablePos);
                                if (detectedType == null) detectedType = getFluidTypeFromState(fluidState);
                                break;
                            }
                        }
                    }

                    if (foundHeight != -1) {
                        totalSurfaceHeight += foundHeight;
                        sumX += x + 0.5f;
                        sumZ += z + 0.5f;
                        fluidColumnCount++;
                    }
                }
            }

            if (fluidColumnCount > 0 && detectedType != null) {
                float baseSurfaceHeight = totalSurfaceHeight / fluidColumnCount;

                // Ensure the surface is high enough to affect the body.
                if (baseSurfaceHeight > bottomThreshold) {
                    float areaFraction = (float) fluidColumnCount / totalScannedColumns;
                    float centerX = sumX / fluidColumnCount;
                    float centerZ = sumZ / fluidColumnCount;

                    float averageSurfaceHeight = getSmoothSurfaceHeight(centerX, centerZ, (int) Math.floor(baseSurfaceHeight));

                    // Surface Normal calculation via Central Difference Gradient
                    float eps = 0.1f;
                    int baseY = (int) Math.floor(baseSurfaceHeight);
                    float hL = getSmoothSurfaceHeight(centerX - eps, centerZ, baseY);
                    float hR = getSmoothSurfaceHeight(centerX + eps, centerZ, baseY);
                    float hD = getSmoothSurfaceHeight(centerX, centerZ - eps, baseY);
                    float hU = getSmoothSurfaceHeight(centerX, centerZ + eps, baseY);

                    float nx = hL - hR;
                    float ny = 2.0f * eps;
                    float nz = hD - hU;

                    float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (len > 0) {
                        nx /= len;
                        ny /= len;
                        nz /= len;
                    } else {
                        nx = 0f; ny = 1f; nz = 0f;
                    }

                    // Sample the fluid flow at the calculated center of buoyancy.
                    mutablePos.set(centerX, averageSurfaceHeight - 0.5f, centerZ);
                    computeFlowLowAlloc(level, mutablePos, level.getFluidState(mutablePos), flowVector);

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
                                    flowVector.x(),
                                    flowVector.y(),
                                    flowVector.z(),
                                    nx,
                                    ny,
                                    nz
                            );
                        }
                    }
                }
            }
        }
    }

    /**
     * Scans blocks vertically upwards from a submerged starting point to find the fluid surface.
     *
     * @param chunk  The chunk to search in.
     * @param x      The world X coordinate.
     * @param startY The starting Y coordinate.
     * @param z      The world Z coordinate.
     * @param pos    A reusable mutable position for iteration.
     * @return The absolute Y height of the fluid surface.
     */
    private float findSurfaceUpwards(ChunkAccess chunk, int x, int startY, int z, BlockPos.MutableBlockPos pos) {
        for (int y = startY + 1; y <= startY + MAX_UPWARD_SEARCH; y++) {
            FluidState state = getFluidStateFromContainer(chunk, x, y, z);
            if (state.isEmpty()) {
                pos.set(x, y - 1, z);
                FluidState below = getFluidStateFromContainer(chunk, x, y - 1, z);
                return (y - 1) + below.getHeight(level, pos);
            }
        }
        return startY + 1.0f;
    }

    /**
     * Calculates the fluid flow direction at a position without creating new vector or position objects.
     * <p>
     * This method replicates the directional height difference logic found in the vanilla
     * fluid implementation to determine flow direction and velocity.
     *
     * @param level   The server level.
     * @param pos     The position to sample the flow at.
     * @param state   The fluid state at the sample position.
     * @param dest    The destination vector to store the calculated flow direction.
     */
    private void computeFlowLowAlloc(ServerLevel level, BlockPos pos, FluidState state, Vector3f dest) {
        dest.set(0, 0, 0);

        // Only flowing fluids have a direction vector.
        if (!(state.getType() instanceof FlowingFluid flowing)) {
            return;
        }

        float dx = 0.0f;
        float dz = 0.0f;

        // Check each horizontal neighbor to calculate the gradient.
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            neighborPos.setWithOffset(pos, direction);
            FluidState neighborState = level.getFluidState(neighborPos);

            if (neighborState.getType().isSame(flowing)) {
                float neighborHeight = neighborState.getOwnHeight();
                float diff = state.getOwnHeight() - neighborHeight;

                if (diff != 0.0f) {
                    dx += (float) direction.getStepX() * diff;
                    dz += (float) direction.getStepZ() * diff;
                }
            }
        }

        dest.set(dx, 0.0f, dz);

        // Normalize the vector if it has a valid length.
        if (dest.lengthSquared() > 1e-6f) {
            dest.normalize();
        }
    }

    /**
     * Gets the fluid state directly from the region's PalettedContainer to optimize read speeds.
     */
    private FluidState getFluidStateFromContainer(ChunkAccess chunk, int x, int y, int z) {
        int sectionIndex = chunk.getSectionIndex(y);
        LevelChunkSection[] sections = chunk.getSections();
        if (sectionIndex >= 0 && sectionIndex < sections.length) {
            LevelChunkSection section = sections[sectionIndex];
            if (section != null && !section.hasOnlyAir()) {
                return section.getStates().get(x & 15, y & 15, z & 15).getFluidState();
            }
        }
        return EMPTY_FLUID;
    }

    /**
     * Bilinearly interpolates the exact fluid height over sub-block coordinates.
     */
    private float getSmoothSurfaceHeight(float startPx, float startPz, int baseY) {
        float px = startPx - 0.5f;
        float pz = startPz - 0.5f;

        int x1 = (int) Math.floor(px);
        int z1 = (int) Math.floor(pz);
        int x2 = x1 + 1;
        int z2 = z1 + 1;

        float fx = px - x1;
        float fz = pz - z1;

        float h00 = getFluidHeightAtFast(x1, baseY, z1);
        float h10 = getFluidHeightAtFast(x2, baseY, z1);
        float h01 = getFluidHeightAtFast(x1, baseY, z2);
        float h11 = getFluidHeightAtFast(x2, baseY, z2);

        return h00 * (1 - fx) * (1 - fz)
             + h10 * fx * (1 - fz)
             + h01 * (1 - fx) * fz
             + h11 * fx * fz;
    }

    private float getFluidHeightAtFast(int x, int baseY, int z) {
        ChunkAccess chunk = this.level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) return baseY;

        FluidState state = getFluidStateFromContainer(chunk, x, baseY, z);
        if (!state.isEmpty()) {
            FluidState stateAbove = getFluidStateFromContainer(chunk, x, baseY + 1, z);
            if (!stateAbove.isEmpty()) {
                return baseY + 1.0f;
            }
            neighborPos.set(x, baseY, z);
            return baseY + state.getHeight(level, neighborPos);
        }

        FluidState stateBelow = getFluidStateFromContainer(chunk, x, baseY - 1, z);
        if (!stateBelow.isEmpty()) {
            neighborPos.set(x, baseY - 1, z);
            return (baseY - 1) + stateBelow.getHeight(level, neighborPos);
        }

        return baseY;
    }

    /**
     * Maps Minecraft fluid states to internal simplified fluid types.
     *
     * @param state The Minecraft fluid state.
     * @return The corresponding internal fluid type, or null if the fluid is not supported.
     */
    private VxFluidType getFluidTypeFromState(FluidState state) {
        if (state.is(FluidTags.WATER)) return VxFluidType.WATER;
        if (state.is(FluidTags.LAVA)) return VxFluidType.LAVA;
        return null;
    }
}