/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.shape;

import com.github.stephengold.joltjni.ScaledShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.Vec3;

/**
 * A decorated collision shape that applies a non-uniform scale to an inner shape.
 * <p>
 * This allows reusing a single shape definition at different scales without
 * creating separate shape instances.
 *
 * @author xI-Mx-Ix
 */
public class VxScaledShape extends VxCollisionShape {

    private final VxCollisionShape inner;
    private final Vec3 scale;

    /**
     * Creates a scaled shape wrapper.
     *
     * @param inner The inner collision shape to scale.
     * @param scale The scale factors along each axis.
     */
    public VxScaledShape(VxCollisionShape inner, Vec3 scale) {
        this.inner = inner;
        this.scale = scale;
    }

    @Override
    protected ShapeSettings createSettings() {
        ShapeSettings innerSettings = inner.createSettings();
        return new ScaledShapeSettings(innerSettings, scale);
    }

    /**
     * @return The inner collision shape.
     */
    public VxCollisionShape getInner() {
        return inner;
    }

    /**
     * @return The scale factors applied.
     */
    public Vec3 getScale() {
        return scale;
    }
}