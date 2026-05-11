/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.shape;

import com.github.stephengold.joltjni.CylinderShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;

/**
 * A cylinder collision shape oriented along the Y axis.
 * <p>
 * An optional convex radius rounds off the edges for more efficient collision detection.
 *
 * @author xI-Mx-Ix
 */
public class VxCylinderShape extends VxCollisionShape {

    private final float halfHeight;
    private final float radius;
    private final float convexRadius;

    /**
     * Creates a cylinder shape with the default convex radius.
     *
     * @param halfHeight The half-height of the cylinder.
     * @param radius     The radius of the cylinder.
     */
    public VxCylinderShape(float halfHeight, float radius) {
        this(halfHeight, radius, 0.05f);
    }

    /**
     * Creates a cylinder shape with a custom convex radius.
     *
     * @param halfHeight   The half-height of the cylinder.
     * @param radius       The radius of the cylinder.
     * @param convexRadius The convex radius for rounding edges.
     */
    public VxCylinderShape(float halfHeight, float radius, float convexRadius) {
        this.halfHeight = halfHeight;
        this.radius = radius;
        this.convexRadius = convexRadius;
    }

    @Override
    protected ShapeSettings createSettings() {
        return new CylinderShapeSettings(halfHeight, radius, convexRadius);
    }

    /**
     * @return The half-height of this cylinder shape.
     */
    public float getHalfHeight() {
        return halfHeight;
    }

    /**
     * @return The radius of this cylinder shape.
     */
    public float getRadius() {
        return radius;
    }

    /**
     * @return The convex radius of this cylinder shape.
     */
    public float getConvexRadius() {
        return convexRadius;
    }
}