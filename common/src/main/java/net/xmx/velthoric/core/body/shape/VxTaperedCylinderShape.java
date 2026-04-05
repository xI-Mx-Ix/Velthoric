/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.shape;

import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.TaperedCylinderShapeSettings;

/**
 * A tapered cylinder collision shape — a cylinder where the top and bottom faces
 * can have different radii.
 * <p>
 * The shape is oriented along the Y axis, centered at the origin.
 * An optional convex radius rounds off the edges for more efficient collision detection.
 *
 * @author xI-Mx-Ix
 */
public class VxTaperedCylinderShape extends VxCollisionShape {

    private final float halfHeight;
    private final float topRadius;
    private final float bottomRadius;
    private final float convexRadius;

    /**
     * Creates a tapered cylinder shape with the default convex radius.
     *
     * @param halfHeight   The half-height of the cylinder.
     * @param topRadius    The radius at the top face.
     * @param bottomRadius The radius at the bottom face.
     */
    public VxTaperedCylinderShape(float halfHeight, float topRadius, float bottomRadius) {
        this(halfHeight, topRadius, bottomRadius, 0.05f);
    }

    /**
     * Creates a tapered cylinder shape with a custom convex radius.
     *
     * @param halfHeight   The half-height of the cylinder.
     * @param topRadius    The radius at the top face.
     * @param bottomRadius The radius at the bottom face.
     * @param convexRadius The convex radius for rounding edges.
     */
    public VxTaperedCylinderShape(float halfHeight, float topRadius, float bottomRadius, float convexRadius) {
        this.halfHeight = halfHeight;
        this.topRadius = topRadius;
        this.bottomRadius = bottomRadius;
        this.convexRadius = convexRadius;
    }

    @Override
    protected ShapeSettings createSettings() {
        return new TaperedCylinderShapeSettings(halfHeight, topRadius, bottomRadius, convexRadius);
    }

    /**
     * @return The half-height of this tapered cylinder shape.
     */
    public float getHalfHeight() {
        return halfHeight;
    }

    /**
     * @return The radius at the top face.
     */
    public float getTopRadius() {
        return topRadius;
    }

    /**
     * @return The radius at the bottom face.
     */
    public float getBottomRadius() {
        return bottomRadius;
    }

    /**
     * @return The convex radius of this tapered cylinder shape.
     */
    public float getConvexRadius() {
        return convexRadius;
    }
}