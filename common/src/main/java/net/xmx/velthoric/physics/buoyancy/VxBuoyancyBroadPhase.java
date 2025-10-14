/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.buoyancy;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
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
import net.xmx.velthoric.physics.body.manager.VxBodyDataStore;
import net.xmx.velthoric.physics.body.manager.VxJoltBridge;
import net.xmx.velthoric.physics.body.type.VxBody;
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
     * Scans all physics bodies and identifies those potentially inside a fluid.
     * This method uses the body's bounding box for a more accurate and stable detection,
     * checking multiple points and scanning vertically to find the highest fluid surface
     * affecting the body.
     *
     * @return A {@link VxBuoyancyResult} object containing the lists of bodies in fluid and their properties.
     */
    public VxBuoyancyResult findPotentialFluidContacts() {
        VxBodyDataStore ds = physicsWorld.getBodyManager().getDataStore();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        IntArrayList bodiesFound = new IntArrayList();

        Int2FloatMap newSurfaceHeights = new Int2FloatOpenHashMap();
        Int2ObjectMap<VxFluidType> newFluidTypes = new Int2ObjectOpenHashMap<>();
        LongSet dryChunkCache = new LongOpenHashSet();

        for (int i = 0; i < ds.getCapacity(); ++i) {
            UUID id = ds.getIdForIndex(i);
            if (id == null) continue;

            VxBody vxBody = physicsWorld.getBodyManager().getVxBody(id);
            if (vxBody == null) continue;

            Body body = VxJoltBridge.INSTANCE.getJoltBody(physicsWorld, vxBody);
            if (body == null || body.isStatic()) continue;

            int bodyId = body.getId();
            if (bodyId == 0) continue;

            // Use the body's axis-aligned bounding box (AABB) for a more precise check.
            ConstAaBox bounds = body.getWorldSpaceBounds();
            Vec3 min = bounds.getMin();
            Vec3 max = bounds.getMax();

            // Broad check to see if any chunk occupied by the body could have fluid.
            // If all relevant chunks are in the dry cache, we can skip this body.
            boolean canSkip = true;
            for (int chunkX = (int) min.getX() >> 4; chunkX <= (int) max.getX() >> 4; chunkX++) {
                for (int chunkZ = (int) min.getZ() >> 4; chunkZ <= (int) max.getZ() >> 4; chunkZ++) {
                    if (!dryChunkCache.contains(BlockPos.asLong(chunkX, 0, chunkZ))) {
                        canSkip = false;
                        break;
                    }
                }
                if (!canSkip) break;
            }
            if (canSkip) continue;

            // Find the highest fluid surface affecting the body by sampling key points.
            float highestSurface = -Float.MAX_VALUE;
            VxFluidType detectedType = null;
            boolean fluidFoundInBounds = false;

            // Sample points within the AABB's horizontal projection: 4 corners and the center.
            float minX = min.getX();
            float maxX = max.getX();
            float minZ = min.getZ();
            float maxZ = max.getZ();
            float[] xPoints = {minX, maxX, minX, maxX, (minX + maxX) * 0.5f};
            float[] zPoints = {minZ, minZ, maxZ, maxZ, (minZ + maxZ) * 0.5f};

            for (int p = 0; p < xPoints.length; p++) {
                // For each horizontal sample point, scan vertically downwards from the top of the AABB.
                // This ensures we find the fluid even if the top of the body is above the water surface.
                int startY = (int) Math.ceil(max.getY());
                int endY = (int) Math.floor(min.getY());

                for (int y = startY; y >= endY; y--) {
                    mutablePos.set(xPoints[p], y, zPoints[p]);
                    FluidState fluidState = this.level.getFluidState(mutablePos);

                    if (!fluidState.isEmpty()) {
                        fluidFoundInBounds = true;
                        // Calculate the precise surface height: the block's Y position plus the fluid's height within that block.
                        float surfaceHeight = y + fluidState.getHeight(level, mutablePos);
                        if (surfaceHeight > highestSurface) {
                            highestSurface = surfaceHeight;
                            if (fluidState.is(FluidTags.WATER)) {
                                detectedType = VxFluidType.WATER;
                            } else if (fluidState.is(FluidTags.LAVA)) {
                                detectedType = VxFluidType.LAVA;
                            }
                        }
                        // Once fluid is found in this vertical column, break to avoid checking blocks below it.
                        break;
                    }
                }
            }

            // If a fluid was detected and its surface is high enough to submerge part of the body, add it.
            if (fluidFoundInBounds && detectedType != null) {
                if (highestSurface > min.getY()) {
                    bodiesFound.add(bodyId);
                    newSurfaceHeights.put(bodyId, highestSurface);
                    newFluidTypes.put(bodyId, detectedType);
                }
            } else {
                // If no fluid was found at all for this body, cache the chunks it occupies as dry
                // to optimize future checks for other bodies in the same chunks.
                for (int chunkX = (int) min.getX() >> 4; chunkX <= (int) max.getX() >> 4; chunkX++) {
                    for (int chunkZ = (int) min.getZ() >> 4; chunkZ <= (int) max.getZ() >> 4; chunkZ++) {
                        dryChunkCache.add(BlockPos.asLong(chunkX, 0, chunkZ));
                    }
                }
            }
        }
        return new VxBuoyancyResult(bodiesFound.toIntArray(), newSurfaceHeights, newFluidTypes);
    }
}