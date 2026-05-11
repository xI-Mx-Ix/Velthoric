/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.shape;

import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.Vec3;

/**
 * A box-shaped collision shape defined by half-extents along each axis.
 * <p>
 * The box is centered at the origin, extending by {@code halfExtents} in each direction.
 * An optional convex radius rounds off the edges and corners for more efficient collision detection.
 *
 * @author xI-Mx-Ix
 */
public class VxBoxShape extends VxCollisionShape {

    private final Vec3 halfExtents;
    private final float convexRadius;

    /**
     * Creates a box shape with the default convex radius.
     *
     * @param halfExtents The half-extents of the box along each axis.
     */
    public VxBoxShape(Vec3 halfExtents) {
        this(halfExtents, 0.05f);
    }

    /**
     * Creates a box shape with a custom convex radius.
     *
     * @param halfExtents  The half-extents of the box along each axis.
     * @param convexRadius The convex radius for rounding edges and corners.
     */
    public VxBoxShape(Vec3 halfExtents, float convexRadius) {
        this.halfExtents = halfExtents;
        this.convexRadius = convexRadius;
    }

    @Override
    protected ShapeSettings createSettings() {
        return new BoxShapeSettings(halfExtents, convexRadius);
    }

    /**
     * @return The half-extents of this box shape.
     */
    public Vec3 getHalfExtents() {
        return halfExtents;
    }

    /**
     * @return The convex radius of this box shape.
     */
    public float getConvexRadius() {
        return convexRadius;
    }
}