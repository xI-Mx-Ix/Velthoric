/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.client;

import com.github.stephengold.joltjni.RVec3;
import net.xmx.velthoric.core.body.VxBodyDataContainer;

/**
 * Client-specific container for physics body data, including interpolation buffers.
 *
 * @author xI-Mx-Ix
 */
public class VxClientBodyDataContainer extends VxBodyDataContainer {
    /**
     * The timestamp (ns) of the previous interpolation state (state0).
     */
    public final long[] state0_timestamp;
    /**
     * The timestamp (ns) of the current interpolation state (state1).
     */
    public final long[] state1_timestamp;

    /**
     * Previous X, Y, Z coordinates for interpolation (state0).
     */
    public final double[] state0_posX, state0_posY, state0_posZ;
    /**
     * Current X, Y, Z coordinates received from the server (state1).
     */
    public final double[] state1_posX, state1_posY, state1_posZ;
    /**
     * Last rendered X, Y, Z coordinates for smooth frame transitions.
     */
    public final double[] prev_posX, prev_posY, prev_posZ;

    /**
     * Previous rotation quaternion for interpolation (state0).
     */
    public final float[] state0_rotX, state0_rotY, state0_rotZ, state0_rotW;
    /**
     * Current rotation quaternion received from the server (state1).
     */
    public final float[] state1_rotX, state1_rotY, state1_rotZ, state1_rotW;
    /**
     * Last rendered rotation quaternion for smooth frame transitions.
     */
    public final float[] prev_rotX, prev_rotY, prev_rotZ, prev_rotW;

    /**
     * Lineary velocity at state0 used for extrapolation.
     */
    public final float[] state0_velX, state0_velY, state0_velZ;
    /**
     * Linear velocity at state1 used for extrapolation.
     */
    public final float[] state1_velX, state1_velY, state1_velZ;

    /**
     * Activation state at state0.
     */
    public final boolean[] state0_isActive;
    /**
     * Activation state at state1.
     */
    public final boolean[] state1_isActive;

    /**
     * Vertex data at state0.
     */
    public final float[][] state0_vertexData;
    /**
     * Vertex data at state1.
     */
    public final float[][] state1_vertexData;
    /**
     * Last rendered vertex data.
     */
    public final float[][] prev_vertexData;

    /**
     * Whether the renderer has finished initializing resources (e.g. GPU buffers) for this body.
     */
    public final boolean[] render_isInitialized;
    /**
     * Arbitrary custom data objects attached to the body for client-side logic.
     */
    public final Object[] customData;
    /**
     * High-precision culling position used for frustum checks.
     */
    public final RVec3[] lastKnownPosition;

    /**
     * Initializes a new client-side container with triple-buffering for interpolation.
     * Pre-allocates all state buffers and culling objects.
     *
     * @param capacity The maximum number of bodies.
     */
    public VxClientBodyDataContainer(int capacity) {
        super(capacity);
        this.state0_timestamp = new long[capacity];
        this.state1_timestamp = new long[capacity];

        this.state0_posX = new double[capacity];
        this.state0_posY = new double[capacity];
        this.state0_posZ = new double[capacity];
        this.state1_posX = new double[capacity];
        this.state1_posY = new double[capacity];
        this.state1_posZ = new double[capacity];
        this.prev_posX = new double[capacity];
        this.prev_posY = new double[capacity];
        this.prev_posZ = new double[capacity];

        this.state0_rotX = new float[capacity];
        this.state0_rotY = new float[capacity];
        this.state0_rotZ = new float[capacity];
        this.state0_rotW = new float[capacity];
        this.state1_rotX = new float[capacity];
        this.state1_rotY = new float[capacity];
        this.state1_rotZ = new float[capacity];
        this.state1_rotW = new float[capacity];
        this.prev_rotX = new float[capacity];
        this.prev_rotY = new float[capacity];
        this.prev_rotZ = new float[capacity];
        this.prev_rotW = new float[capacity];

        this.state0_velX = new float[capacity];
        this.state0_velY = new float[capacity];
        this.state0_velZ = new float[capacity];
        this.state1_velX = new float[capacity];
        this.state1_velY = new float[capacity];
        this.state1_velZ = new float[capacity];

        this.state0_isActive = new boolean[capacity];
        this.state1_isActive = new boolean[capacity];

        this.state0_vertexData = new float[capacity][];
        this.state1_vertexData = new float[capacity][];
        this.prev_vertexData = new float[capacity][];

        this.render_isInitialized = new boolean[capacity];
        this.customData = new Object[capacity];
        this.lastKnownPosition = new RVec3[capacity];
        for (int i = 0; i < capacity; i++) {
            this.lastKnownPosition[i] = new RVec3();
        }
    }
}