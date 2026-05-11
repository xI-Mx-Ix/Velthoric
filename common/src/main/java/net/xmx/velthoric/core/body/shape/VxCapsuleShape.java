/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.shape;

import com.github.stephengold.joltjni.CapsuleShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;

/**
 * A capsule collision shape (a cylinder with hemispherical caps).
 * <p>
 * The capsule is oriented along the Y axis, centered at the origin.
 *
 * @author xI-Mx-Ix
 */
public class VxCapsuleShape extends VxCollisionShape {

    private final float halfHeight;
    private final float radius;

    /**
     * Creates a capsule shape with the specified dimensions.
     *
     * @param halfHeight The half-height of the cylindrical section.
     * @param radius     The radius of both the cylinder and hemispherical caps.
     */
    public VxCapsuleShape(float halfHeight, float radius) {
        this.halfHeight = halfHeight;
        this.radius = radius;
    }

    @Override
    protected ShapeSettings createSettings() {
        return new CapsuleShapeSettings(halfHeight, radius);
    }

    /**
     * @return The half-height of the cylindrical section.
     */
    public float getHalfHeight() {
        return halfHeight;
    }

    /**
     * @return The radius of the capsule.
     */
    public float getRadius() {
        return radius;
    }
}