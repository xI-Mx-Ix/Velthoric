/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.client;

import net.xmx.velthoric.math.VxTransform;
import org.jetbrains.annotations.Nullable;

/**
 * A data-transfer object that holds the complete, interpolated state of a physics
 * object for a single render frame. This includes its transform and, for soft bodies,
 * its vertex data.
 *
 * @author xI-Mx-Ix
 */
public class VxRenderState {
    /** The interpolated transform (position and rotation) of the object for the current frame. */
    public final VxTransform transform = new VxTransform();
    /** The interpolated vertex data for soft bodies. Null for rigid bodies. */
    public float @Nullable [] vertexData = null;

    /**
     * Copies the data from another {@link VxRenderState} into this one.
     *
     * @param other The state to copy from.
     */
    public void set(VxRenderState other) {
        this.transform.set(other.transform);
        if (other.vertexData != null) {
            // Allocate a new array if needed, or reuse the existing one if the size matches.
            if (this.vertexData == null || this.vertexData.length != other.vertexData.length) {
                this.vertexData = new float[other.vertexData.length];
            }
            System.arraycopy(other.vertexData, 0, this.vertexData, 0, other.vertexData.length);
        } else {
            this.vertexData = null;
        }
    }
}