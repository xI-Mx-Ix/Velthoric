/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.buoyancy;

import net.xmx.velthoric.physics.AbstractDataStore;

/**
 * A data-oriented store for the state of all bodies currently affected by buoyancy.
 * <p>
 * This class uses a "Structure of Arrays" (SoA) layout to maximize CPU cache efficiency
 * during sequential processing. It tracks surface height, fluid coverage, and fluid velocity
 * vectors to support accurate hydrodynamics.
 *
 * @author xI-Mx-Ix
 */
public final class VxBuoyancyDataStore extends AbstractDataStore {
    private int count = 0;
    private int capacity = 0;

    // --- Buoyancy State Data (SoA) ---

    /**
     * The ID of the physics body in the Jolt system.
     */
    public int[] bodyIds;

    /**
     * The world-space Y-coordinate of the fluid surface.
     */
    public float[] surfaceHeights;

    /**
     * The type of fluid (e.g., WATER, LAVA) interacting with the body.
     */
    public VxFluidType[] fluidTypes;

    /**
     * The fraction of the body's horizontal AABB area that is covered by fluid (0.0 to 1.0).
     */
    public float[] areaFractions;

    /**
     * The average X-coordinate of the fluid columns found within the body's AABB.
     */
    public float[] waterCenterX;

    /**
     * The average Z-coordinate of the fluid columns found within the body's AABB.
     */
    public float[] waterCenterZ;

    /**
     * The X component of the fluid's flow velocity vector.
     */
    public float[] flowX;

    /**
     * The Y component of the fluid's flow velocity vector.
     */
    public float[] flowY;

    /**
     * The Z component of the fluid's flow velocity vector.
     */
    public float[] flowZ;

    /**
     * Constructs a new data store with a default initial capacity.
     */
    public VxBuoyancyDataStore() {
        this(256);
    }

    /**
     * Constructs a new data store with a specified initial capacity.
     *
     * @param initialCapacity The initial number of bodies to allocate memory for.
     */
    public VxBuoyancyDataStore(int initialCapacity) {
        allocate(initialCapacity);
    }

    /**
     * Pre-allocates memory for a new number of bodies.
     *
     * @param newCapacity The new capacity.
     */
    private void allocate(int newCapacity) {
        if (newCapacity <= this.capacity) return;

        bodyIds = grow(bodyIds, newCapacity);
        surfaceHeights = grow(surfaceHeights, newCapacity);
        fluidTypes = grow(fluidTypes, newCapacity);
        areaFractions = grow(areaFractions, newCapacity);
        waterCenterX = grow(waterCenterX, newCapacity);
        waterCenterZ = grow(waterCenterZ, newCapacity);
        flowX = grow(flowX, newCapacity);
        flowY = grow(flowY, newCapacity);
        flowZ = grow(flowZ, newCapacity);

        this.capacity = newCapacity;
    }

    /**
     * Adds a body and its hydro-dynamic properties to the data store.
     *
     * @param bodyId        The Jolt body ID.
     * @param surfaceHeight The Y-coordinate of the fluid surface.
     * @param fluidType     The type of fluid the body is in.
     * @param areaFraction  The percentage of the footprint covered by fluid.
     * @param centerX       The center of buoyancy X-position in world space.
     * @param centerZ       The center of buoyancy Z-position in world space.
     * @param fX            The X component of the fluid velocity.
     * @param fY            The Y component of the fluid velocity.
     * @param fZ            The Z component of the fluid velocity.
     */
    public void add(int bodyId, float surfaceHeight, VxFluidType fluidType, float areaFraction,
                    float centerX, float centerZ, float fX, float fY, float fZ) {
        if (count == capacity) {
            allocate(capacity * 2);
        }
        bodyIds[count] = bodyId;
        surfaceHeights[count] = surfaceHeight;
        fluidTypes[count] = fluidType;
        areaFractions[count] = areaFraction;
        waterCenterX[count] = centerX;
        waterCenterZ[count] = centerZ;
        flowX[count] = fX;
        flowY[count] = fY;
        flowZ[count] = fZ;
        count++;
    }

    /**
     * Returns the number of active entries in the store.
     *
     * @return The count of buoyant bodies.
     */
    public int getCount() {
        return this.count;
    }

    /**
     * Resets the store count to zero, effectively clearing the data for the next frame.
     */
    public void clear() {
        this.count = 0;
    }
}