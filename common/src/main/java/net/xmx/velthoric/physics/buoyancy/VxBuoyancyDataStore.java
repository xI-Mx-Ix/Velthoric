/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.buoyancy;

import net.xmx.velthoric.physics.AbstractDataStore;

/**
 * A data-oriented store for the state of all bodies currently affected by buoyancy.
 * This class uses a "Structure of Arrays" (SoA) layout. It tracks not only the
 * surface height but also the horizontal coverage of the fluid to allow for
 * partial submersion calculations.
 *
 * @author xI-Mx-Ix
 */
public final class VxBuoyancyDataStore extends AbstractDataStore {
    private int count = 0;
    private int capacity = 0;

    // --- Buoyancy State Data (SoA) ---
    public int[] bodyIds;
    public float[] surfaceHeights;
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
     * Constructs a new data store with a default initial capacity.
     */
    public VxBuoyancyDataStore() {
        this(256);
    }

    /**
     * Constructs a new data store with a specified initial capacity.
     * @param initialCapacity The initial number of bodies to allocate memory for.
     */
    public VxBuoyancyDataStore(int initialCapacity) {
        allocate(initialCapacity);
    }

    /**
     * Pre-allocates memory for a new number of bodies.
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

        this.capacity = newCapacity;
    }

    /**
     * Adds a body and its fluid properties to the data store.
     *
     * @param bodyId        The Jolt body ID.
     * @param surfaceHeight The Y-coordinate of the fluid surface.
     * @param fluidType     The type of fluid the body is in.
     * @param areaFraction  The percentage of the footprint covered by fluid.
     * @param centerX       The center of buoyancy X-position in world space.
     * @param centerZ       The center of buoyancy Z-position in world space.
     */
    public void add(int bodyId, float surfaceHeight, VxFluidType fluidType, float areaFraction, float centerX, float centerZ) {
        if (count == capacity) {
            allocate(capacity * 2);
        }
        bodyIds[count] = bodyId;
        surfaceHeights[count] = surfaceHeight;
        fluidTypes[count] = fluidType;
        areaFractions[count] = areaFraction;
        waterCenterX[count] = centerX;
        waterCenterZ[count] = centerZ;
        count++;
    }

    public int getCount() {
        return this.count;
    }

    public void clear() {
        this.count = 0;
    }
}