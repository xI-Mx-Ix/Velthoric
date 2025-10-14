/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.buoyancy;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
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
 * This class efficiently scans for bodies that are potentially in a fluid.
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
     * Scans all physics bodies and populates a data store with those potentially inside a fluid.
     * This method uses the body's bounding box for an accurate and stable detection,
     * checking multiple points and scanning vertically to find the highest fluid surface
     * affecting the body.
     *
     * @param dataStore The {@link VxBuoyancyDataStore} to populate with the results.
     */
    public void findPotentialFluidContacts(VxBuoyancyDataStore dataStore) {
        dataStore.clear(); // Prepare the store for new data.

        VxBodyDataStore ds = physicsWorld.getBodyManager().getDataStore();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

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
                    mutablePos.set((int) xPoints[p], y, (int) zPoints[p]);
                    FluidState fluidState = this.level.getFluidState(mutablePos);

                    if (!fluidState.isEmpty()) {
                        fluidFoundInBounds = true;
                        // Calculate the precise surface height: the block's Y position + fluid height.
                        float surfaceHeight = y + fluidState.getHeight(level, mutablePos);
                        if (surfaceHeight > highestSurface) {
                            highestSurface = surfaceHeight;
                            if (fluidState.is(FluidTags.WATER)) {
                                detectedType = VxFluidType.WATER;
                            } else if (fluidState.is(FluidTags.LAVA)) {
                                detectedType = VxFluidType.LAVA;
                            }
                        }
                        // Once fluid is found in this vertical column, we can check the next column.
                        break;
                    }
                }
            }

            // If a fluid was detected and its surface is high enough to submerge part of the body, add it.
            if (fluidFoundInBounds && detectedType != null) {
                if (highestSurface > min.getY()) {
                    dataStore.add(bodyId, highestSurface, detectedType);
                }
            }
        }
    }
}