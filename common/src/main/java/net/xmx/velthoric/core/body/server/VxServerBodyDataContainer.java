/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.server;

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
     * The Jolt motion type defining mobility (e.g. Static, Kinematic, Dynamic).
     */
    public final EMotionType[] motionType;
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
        this.chunkKey = new long[capacity];
        this.networkId = new int[capacity];
        this.isTransformDirty = new boolean[capacity];
        this.isVertexDataDirty = new boolean[capacity];
        this.isCustomDataDirty = new boolean[capacity];
        this.dirtyIndices = new IntOpenHashSet(2048);
        this.lastUpdateTimestamp = new long[capacity];

        for (int i = 0; i < capacity; i++) {
            this.networkId[i] = -1;
            this.chunkKey[i] = Long.MAX_VALUE;
        }
    }
}