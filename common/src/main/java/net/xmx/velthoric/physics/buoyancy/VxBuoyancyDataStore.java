/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.buoyancy;

import net.xmx.velthoric.physics.body.AbstractDataStore;

/**
 * A data-oriented store for the state of all bodies currently affected by buoyancy.
 * This class uses a "Structure of Arrays" (SoA) layout, where each property
 * is stored in a separate array. This is highly efficient for the physics update loop,
 * as it improves CPU cache locality when iterating over all buoyant bodies.
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
     * Constructs a new data store with a default initial capacity.
     */
    public VxBuoyancyDataStore() {
        this(256); // Default initial capacity for 256 buoyant bodies.
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

        this.capacity = newCapacity;
    }

    /**
     * Adds a body and its fluid properties to the data store.
     *
     * @param bodyId        The Jolt body ID.
     * @param surfaceHeight The Y-coordinate of the fluid surface.
     * @param fluidType     The type of fluid the body is in.
     */
    public void add(int bodyId, float surfaceHeight, VxFluidType fluidType) {
        if (count == capacity) {
            allocate(capacity * 2);
        }
        bodyIds[count] = bodyId;
        surfaceHeights[count] = surfaceHeight;
        fluidTypes[count] = fluidType;
        count++;
    }

    /**
     * Gets the number of bodies currently in the store.
     * @return The count of buoyant bodies.
     */
    public int getCount() {
        return this.count;
    }

    /**
     * Clears the store, resetting the body count without de-allocating memory.
     * This makes the store ready for reuse in the next tick.
     */
    public void clear() {
        this.count = 0;
    }
}