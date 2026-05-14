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

    /**
     * Copies all client-specific physics data and interpolation buffers to another container.
     * <p>
     * Migrates the entire state history (State 0, State 1, Prev) and culling state
     * to the new memory allocation.
     *
     * @param other The destination container (must be an instance of {@link VxClientBodyDataContainer}).
     */
    @Override
    public void copyTo(VxBodyDataContainer other) {
        super.copyTo(other);
        if (other instanceof VxClientBodyDataContainer next) {
            int len = Math.min(this.capacity, next.capacity);
            System.arraycopy(this.state0_timestamp, 0, next.state0_timestamp, 0, len);
            System.arraycopy(this.state0_posX, 0, next.state0_posX, 0, len);
            System.arraycopy(this.state0_posY, 0, next.state0_posY, 0, len);
            System.arraycopy(this.state0_posZ, 0, next.state0_posZ, 0, len);
            System.arraycopy(this.state0_rotX, 0, next.state0_rotX, 0, len);
            System.arraycopy(this.state0_rotY, 0, next.state0_rotY, 0, len);
            System.arraycopy(this.state0_rotZ, 0, next.state0_rotZ, 0, len);
            System.arraycopy(this.state0_rotW, 0, next.state0_rotW, 0, len);
            System.arraycopy(this.state0_velX, 0, next.state0_velX, 0, len);
            System.arraycopy(this.state0_velY, 0, next.state0_velY, 0, len);
            System.arraycopy(this.state0_velZ, 0, next.state0_velZ, 0, len);
            System.arraycopy(this.state0_isActive, 0, next.state0_isActive, 0, len);
            System.arraycopy(this.state0_vertexData, 0, next.state0_vertexData, 0, len);

            System.arraycopy(this.state1_timestamp, 0, next.state1_timestamp, 0, len);
            System.arraycopy(this.state1_posX, 0, next.state1_posX, 0, len);
            System.arraycopy(this.state1_posY, 0, next.state1_posY, 0, len);
            System.arraycopy(this.state1_posZ, 0, next.state1_posZ, 0, len);
            System.arraycopy(this.state1_rotX, 0, next.state1_rotX, 0, len);
            System.arraycopy(this.state1_rotY, 0, next.state1_rotY, 0, len);
            System.arraycopy(this.state1_rotZ, 0, next.state1_rotZ, 0, len);
            System.arraycopy(this.state1_rotW, 0, next.state1_rotW, 0, len);
            System.arraycopy(this.state1_velX, 0, next.state1_velX, 0, len);
            System.arraycopy(this.state1_velY, 0, next.state1_velY, 0, len);
            System.arraycopy(this.state1_velZ, 0, next.state1_velZ, 0, len);
            System.arraycopy(this.state1_isActive, 0, next.state1_isActive, 0, len);
            System.arraycopy(this.state1_vertexData, 0, next.state1_vertexData, 0, len);

            System.arraycopy(this.prev_posX, 0, next.prev_posX, 0, len);
            System.arraycopy(this.prev_posY, 0, next.prev_posY, 0, len);
            System.arraycopy(this.prev_posZ, 0, next.prev_posZ, 0, len);
            System.arraycopy(this.prev_rotX, 0, next.prev_rotX, 0, len);
            System.arraycopy(this.prev_rotY, 0, next.prev_rotY, 0, len);
            System.arraycopy(this.prev_rotZ, 0, next.prev_rotZ, 0, len);
            System.arraycopy(this.prev_rotW, 0, next.prev_rotW, 0, len);
            System.arraycopy(this.prev_vertexData, 0, next.prev_vertexData, 0, len);

            System.arraycopy(this.render_isInitialized, 0, next.render_isInitialized, 0, len);
            System.arraycopy(this.customData, 0, next.customData, 0, len);
            for (int i = 0; i < len; i++) {
                next.lastKnownPosition[i].set(this.lastKnownPosition[i]);
            }
        }
    }

    /**
     * Resets all client-specific physics data at the specified index to default values.
     * <p>
     * Clears all interpolation state history, custom data, and resets culling vectors for the slot.
     *
     * @param index The slot index to clear.
     */
    @Override
    public void reset(int index) {
        super.reset(index);
        this.state0_timestamp[index] = 0;
        this.state1_timestamp[index] = 0;
        this.state0_isActive[index] = false;
        this.state1_isActive[index] = false;
        this.state0_vertexData[index] = null;
        this.state1_vertexData[index] = null;
        this.render_isInitialized[index] = false;
        this.prev_vertexData[index] = null;
        this.customData[index] = null;

        if (this.lastKnownPosition != null && this.lastKnownPosition[index] != null) {
            this.lastKnownPosition[index].loadZero();
        }

        this.state0_velX[index] = this.state0_velY[index] = this.state0_velZ[index] = 0;
        this.state1_velX[index] = this.state1_velY[index] = this.state1_velZ[index] = 0;
        this.state0_posX[index] = this.state0_posY[index] = this.state0_posZ[index] = 0.0;
        this.state1_posX[index] = this.state1_posY[index] = this.state1_posZ[index] = 0.0;
        this.prev_posX[index] = this.prev_posY[index] = this.prev_posZ[index] = 0.0;
        this.state0_rotX[index] = this.state0_rotY[index] = this.state0_rotZ[index] = 0f;
        this.state0_rotW[index] = 1f;
        this.state1_rotX[index] = this.state1_rotY[index] = this.state1_rotZ[index] = 0f;
        this.state1_rotW[index] = 1f;
        this.prev_rotX[index] = this.prev_rotY[index] = this.prev_rotZ[index] = 0f;
        this.prev_rotW[index] = 1f;
    }
}