/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body;

/**
 * A container for the base Structure of Arrays (SoA) physics data.
 * This class is designed to be swapped atomically within a {@link VxBodyDataStore}
 * to ensure thread-safety during array resizing.
 *
 * @author xI-Mx-Ix
 */
public class VxBodyDataContainer {
    /**
     * X-coordinate of the body position in world space.
     */
    public final double[] posX;
    /**
     * Y-coordinate of the body position in world space.
     */
    public final double[] posY;
    /**
     * Z-coordinate of the body position in world space.
     */
    public final double[] posZ;

    /**
     * X-component of the body rotation quaternion.
     */
    public final float[] rotX;
    /**
     * Y-component of the body rotation quaternion.
     */
    public final float[] rotY;
    /**
     * Z-component of the body rotation quaternion.
     */
    public final float[] rotZ;
    /**
     * W-component of the body rotation quaternion.
     */
    public final float[] rotW;

    /**
     * X-component of the linear velocity.
     */
    public final float[] velX;
    /**
     * Y-component of the linear velocity.
     */
    public final float[] velY;
    /**
     * Z-component of the linear velocity.
     */
    public final float[] velZ;

    /**
     * Per-vertex data for complex collision shapes or rendering (e.g. heightmaps, meshes).
     */
    public final float[][] vertexData;
    /**
     * Whether the body is currently active and ticking in the physics simulation.
     */
    public final boolean[] isActive;
    /**
     * Bitmask representing attached behaviors and their network/tick states.
     */
    public final long[] behaviorBits;
    /**
     * Reference to the high-level {@link VxBody} wrapper objects for each slot.
     */
    public final VxBody[] bodies;
    /**
     * The total pre-allocated capacity of the data arrays in this container.
     */
    public final int capacity;

    /**
     * Initializes a new container with the specified capacity.
     * All internal arrays are pre-allocated to this size to avoid runtime allocations.
     *
     * @param capacity The maximum number of bodies this container can manage.
     */
    public VxBodyDataContainer(int capacity) {
        this.capacity = capacity;
        this.posX = new double[capacity];
        this.posY = new double[capacity];
        this.posZ = new double[capacity];
        this.rotX = new float[capacity];
        this.rotY = new float[capacity];
        this.rotZ = new float[capacity];
        this.rotW = new float[capacity];
        this.velX = new float[capacity];
        this.velY = new float[capacity];
        this.velZ = new float[capacity];
        this.vertexData = new float[capacity][];
        this.isActive = new boolean[capacity];
        this.behaviorBits = new long[capacity];
        this.bodies = new VxBody[capacity];
    }

    public int getCapacity() {
        return capacity;
    }
}