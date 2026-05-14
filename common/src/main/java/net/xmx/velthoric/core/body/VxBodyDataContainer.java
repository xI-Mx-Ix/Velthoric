/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body;

import net.xmx.velthoric.core.body.shape.VxCollisionShape;

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
     * The collision shape of the body, synchronized between server and client.
     */
    public final VxCollisionShape[] shape;

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
        this.shape = new VxCollisionShape[capacity];
        this.isActive = new boolean[capacity];
        this.behaviorBits = new long[capacity];
        this.bodies = new VxBody[capacity];
    }

    /**
     * Copies all base physics data from this container to another container.
     * <p>
     * This is used during array resizing to migrate existing state to a larger
     * memory allocation while maintaining data integrity.
     *
     * @param other The destination container to copy data into.
     * @throws IllegalArgumentException if the other container's capacity is smaller than this container's.
     */
    public void copyTo(VxBodyDataContainer other) {
        int copyLength = Math.min(this.capacity, other.capacity);
        System.arraycopy(this.posX, 0, other.posX, 0, copyLength);
        System.arraycopy(this.posY, 0, other.posY, 0, copyLength);
        System.arraycopy(this.posZ, 0, other.posZ, 0, copyLength);
        System.arraycopy(this.rotX, 0, other.rotX, 0, copyLength);
        System.arraycopy(this.rotY, 0, other.rotY, 0, copyLength);
        System.arraycopy(this.rotZ, 0, other.rotZ, 0, copyLength);
        System.arraycopy(this.rotW, 0, other.rotW, 0, copyLength);
        System.arraycopy(this.velX, 0, other.velX, 0, copyLength);
        System.arraycopy(this.velY, 0, other.velY, 0, copyLength);
        System.arraycopy(this.velZ, 0, other.velZ, 0, copyLength);
        System.arraycopy(this.vertexData, 0, other.vertexData, 0, copyLength);
        System.arraycopy(this.shape, 0, other.shape, 0, copyLength);
        System.arraycopy(this.isActive, 0, other.isActive, 0, copyLength);
        System.arraycopy(this.behaviorBits, 0, other.behaviorBits, 0, copyLength);
        System.arraycopy(this.bodies, 0, other.bodies, 0, copyLength);
    }

    /**
     * Resets all base physics data at the specified index to default values.
     * <p>
     * This clears object references to prevent memory leaks and restores 
     * mathematical identity values (e.g., identity quaternion for rotation).
     *
     * @param index The slot index to clear.
     */
    public void reset(int index) {
        this.bodies[index] = null;
        this.posX[index] = this.posY[index] = this.posZ[index] = 0.0;
        this.rotX[index] = this.rotY[index] = this.rotZ[index] = 0f;
        this.rotW[index] = 1f; // Identity Quaternion
        this.velX[index] = this.velY[index] = this.velZ[index] = 0f;
        this.vertexData[index] = null;
        this.shape[index] = null;
        this.isActive[index] = false;
        this.behaviorBits[index] = 0L;
    }

    /**
     * Returns the total pre-allocated capacity of this container.
     *
     * @return The maximum number of body slots.
     */
    public int getCapacity() {
        return capacity;
    }
}