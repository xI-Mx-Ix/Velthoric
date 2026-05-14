/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.server;

import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.xmx.velthoric.core.body.VxBodyDataContainer;

/**
 * Server-specific container for physics body data.
 *
 * @author xI-Mx-Ix
 */
public class VxServerBodyDataContainer extends VxBodyDataContainer {
    /**
     * X-component of the angular velocity (radians/sec).
     */
    public final float[] angVelX;
    /**
     * Y-component of the angular velocity (radians/sec).
     */
    public final float[] angVelY;
    /**
     * Z-component of the angular velocity (radians/sec).
     */
    public final float[] angVelZ;

    /**
     * Minimum X-coordinate of the world-space Axis-Aligned Bounding Box (AABB).
     */
    public final float[] aabbMinX;
    /**
     * Minimum Y-coordinate of the world-space Axis-Aligned Bounding Box (AABB).
     */
    public final float[] aabbMinY;
    /**
     * Minimum Z-coordinate of the world-space Axis-Aligned Bounding Box (AABB).
     */
    public final float[] aabbMinZ;

    /**
     * Maximum X-coordinate of the world-space Axis-Aligned Bounding Box (AABB).
     */
    public final float[] aabbMaxX;
    /**
     * Maximum Y-coordinate of the world-space Axis-Aligned Bounding Box (AABB).
     */
    public final float[] aabbMaxY;
    /**
     * Maximum Z-coordinate of the world-space Axis-Aligned Bounding Box (AABB).
     */
    public final float[] aabbMaxZ;

    /**
     * The Jolt physics body type (e.g. Rigid, Soft, Fluid).
     */
    public final EBodyType[] bodyType;
    /**
     * The Jolt motion type (Static, Kinematic, Dynamic).
     */
    public final EMotionType[] motionType;
    /**
     * The Jolt activation state (Activate, DontActivate).
     */
    public final EActivation[] activation;
    /**
     * A long representation of the {@link net.minecraft.world.level.ChunkPos} the body resides in.
     */
    public final long[] chunkKey;
    /**
     * The unique network ID assigned for server-client synchronization.
     */
    public final int[] networkId;

    /**
     * Whether the transform (pos/rot) has changed and needs to be broadcast.
     */
    public final boolean[] isTransformDirty;
    /**
     * Whether the vertex data has changed and needs to be broadcast.
     */
    public final boolean[] isVertexDataDirty;
    /**
     * Whether custom behavior data has changed and needs to be broadcast.
     */
    public final boolean[] isCustomDataDirty;
    /**
     * Whether the collision shape has changed and needs to be broadcast.
     */
    public final boolean[] isShapeDirty;
    /**
     * A set of all indices currently marked as dirty for the next network tick.
     */
    public final IntSet dirtyIndices;
    /**
     * Last system time (ms) when this body's network state was updated.
     */
    public final long[] lastUpdateTimestamp;

    /**
     * Initializes a new server-side container with specialized tracking arrays.
     * All arrays are pre-allocated and dirty tracking systems are initialized.
     *
     * @param capacity The maximum number of bodies.
     */
    public VxServerBodyDataContainer(int capacity) {
        super(capacity);
        this.angVelX = new float[capacity];
        this.angVelY = new float[capacity];
        this.angVelZ = new float[capacity];
        this.aabbMinX = new float[capacity];
        this.aabbMinY = new float[capacity];
        this.aabbMinZ = new float[capacity];
        this.aabbMaxX = new float[capacity];
        this.aabbMaxY = new float[capacity];
        this.aabbMaxZ = new float[capacity];
        this.bodyType = new EBodyType[capacity];
        this.motionType = new EMotionType[capacity];
        this.activation = new EActivation[capacity];
        this.chunkKey = new long[capacity];
        this.networkId = new int[capacity];
        this.isTransformDirty = new boolean[capacity];
        this.isVertexDataDirty = new boolean[capacity];
        this.isCustomDataDirty = new boolean[capacity];
        this.isShapeDirty = new boolean[capacity];
        this.dirtyIndices = new IntOpenHashSet(2048);
        this.lastUpdateTimestamp = new long[capacity];

        for (int i = 0; i < capacity; i++) {
            this.networkId[i] = -1;
            this.chunkKey[i] = Long.MAX_VALUE;
            this.motionType[i] = EMotionType.Dynamic;
            this.activation[i] = EActivation.DontActivate;
        }
    }

    /**
     * Copies all server-specific physics data to another container.
     * <p>
     * Extends the base {@link VxBodyDataContainer#copyTo(VxBodyDataContainer)} by
     * migrating angular velocity, AABBs, network state, and dirty flags.
     *
     * @param other The destination container (must be an instance of {@link VxServerBodyDataContainer}).
     */
    @Override
    public void copyTo(VxBodyDataContainer other) {
        super.copyTo(other);
        if (other instanceof VxServerBodyDataContainer next) {
            int len = Math.min(this.capacity, next.capacity);
            System.arraycopy(this.angVelX, 0, next.angVelX, 0, len);
            System.arraycopy(this.angVelY, 0, next.angVelY, 0, len);
            System.arraycopy(this.angVelZ, 0, next.angVelZ, 0, len);
            System.arraycopy(this.aabbMinX, 0, next.aabbMinX, 0, len);
            System.arraycopy(this.aabbMinY, 0, next.aabbMinY, 0, len);
            System.arraycopy(this.aabbMinZ, 0, next.aabbMinZ, 0, len);
            System.arraycopy(this.aabbMaxX, 0, next.aabbMaxX, 0, len);
            System.arraycopy(this.aabbMaxY, 0, next.aabbMaxY, 0, len);
            System.arraycopy(this.aabbMaxZ, 0, next.aabbMaxZ, 0, len);
            System.arraycopy(this.bodyType, 0, next.bodyType, 0, len);
            System.arraycopy(this.motionType, 0, next.motionType, 0, len);
            System.arraycopy(this.activation, 0, next.activation, 0, len);
            System.arraycopy(this.chunkKey, 0, next.chunkKey, 0, len);
            System.arraycopy(this.networkId, 0, next.networkId, 0, len);
            System.arraycopy(this.isTransformDirty, 0, next.isTransformDirty, 0, len);
            System.arraycopy(this.isVertexDataDirty, 0, next.isVertexDataDirty, 0, len);
            System.arraycopy(this.isCustomDataDirty, 0, next.isCustomDataDirty, 0, len);
            System.arraycopy(this.isShapeDirty, 0, next.isShapeDirty, 0, len);
            System.arraycopy(this.lastUpdateTimestamp, 0, next.lastUpdateTimestamp, 0, len);
            next.dirtyIndices.addAll(this.dirtyIndices);
        }
    }

    /**
     * Resets all server-specific physics data at the specified index to default values.
     * <p>
     * Clears physical bounds, network mapping, and dirty tracking state for the slot.
     *
     * @param index The slot index to clear.
     */
    @Override
    public void reset(int index) {
        super.reset(index);
        this.angVelX[index] = this.angVelY[index] = this.angVelZ[index] = 0f;
        this.aabbMinX[index] = this.aabbMinY[index] = this.aabbMinZ[index] = 0f;
        this.aabbMaxX[index] = this.aabbMaxY[index] = this.aabbMaxZ[index] = 0f;
        this.bodyType[index] = null;
        this.chunkKey[index] = Long.MAX_VALUE;
        this.networkId[index] = -1;
        this.motionType[index] = EMotionType.Dynamic;
        this.activation[index] = EActivation.DontActivate;
        this.isTransformDirty[index] = false;
        this.isVertexDataDirty[index] = false;
        this.isCustomDataDirty[index] = false;
        this.isShapeDirty[index] = false;
        this.lastUpdateTimestamp[index] = 0L;
        this.dirtyIndices.remove(index);
    }
}