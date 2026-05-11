/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.shape;

import com.github.stephengold.joltjni.TriangleShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.Vec3;

/**
 * A triangle collision shape defined by three vertices.
 * <p>
 * This represents a single triangle, not a triangle mesh. It is a convex shape.
 * An optional convex radius rounds off the edges for more efficient collision detection.
 *
 * @author xI-Mx-Ix
 */
public class VxTriangleShape extends VxCollisionShape {

    private final Vec3 v1;
    private final Vec3 v2;
    private final Vec3 v3;
    private final float convexRadius;

    /**
     * Creates a triangle shape with the default convex radius.
     *
     * @param v1 The first vertex.
     * @param v2 The second vertex.
     * @param v3 The third vertex.
     */
    public VxTriangleShape(Vec3 v1, Vec3 v2, Vec3 v3) {
        this(v1, v2, v3, 0.05f);
    }

    /**
     * Creates a triangle shape with a custom convex radius.
     *
     * @param v1           The first vertex.
     * @param v2           The second vertex.
     * @param v3           The third vertex.
     * @param convexRadius The convex radius for rounding edges.
     */
    public VxTriangleShape(Vec3 v1, Vec3 v2, Vec3 v3, float convexRadius) {
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
        this.convexRadius = convexRadius;
    }

    @Override
    protected ShapeSettings createSettings() {
        return new TriangleShapeSettings(v1, v2, v3, convexRadius);
    }

    /**
     * @return The first vertex of this triangle.
     */
    public Vec3 getV1() {
        return v1;
    }

    /**
     * @return The second vertex of this triangle.
     */
    public Vec3 getV2() {
        return v2;
    }

    /**
     * @return The third vertex of this triangle.
     */
    public Vec3 getV3() {
        return v3;
    }

    /**
     * @return The convex radius of this triangle shape.
     */
    public float getConvexRadius() {
        return convexRadius;
    }
}