/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.shape;

import com.github.stephengold.joltjni.ConvexHullShapeSettings;
import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.ShapeSettings;

import java.nio.FloatBuffer;

/**
 * A convex hull collision shape constructed from a set of 3D points.
 * <p>
 * The shape is defined by providing a collection of points, and Jolt will compute
 * the tightest convex hull that encloses all of them. This is the most flexible
 * of the convex shape types but requires a settings-based creation approach.
 *
 * @author xI-Mx-Ix
 */
public class VxConvexHullShape extends VxCollisionShape {

    private final float[] points;
    private final float maxConvexRadius;

    /**
     * Creates a convex hull shape from a flat array of point coordinates (x, y, z, x, y, z, ...).
     *
     * @param points An array of floats where every 3 consecutive values represent one point (x, y, z).
     */
    public VxConvexHullShape(float[] points) {
        this(points, Float.MAX_VALUE);
    }

    /**
     * Creates a convex hull shape from a flat array of point coordinates with a maximum convex radius.
     *
     * @param points          An array of floats where every 3 consecutive values represent one point.
     * @param maxConvexRadius The maximum convex radius applied to the hull.
     */
    public VxConvexHullShape(float[] points, float maxConvexRadius) {
        this.points = points;
        this.maxConvexRadius = maxConvexRadius;
    }

    /**
     * Creates a convex hull shape from a {@link FloatBuffer} of point coordinates.
     *
     * @param pointBuffer A FloatBuffer containing tightly packed point coordinates (x, y, z).
     */
    public VxConvexHullShape(FloatBuffer pointBuffer) {
        this(bufferToArray(pointBuffer), Float.MAX_VALUE);
    }

    /**
     * Creates a convex hull shape from a {@link FloatBuffer} with a maximum convex radius.
     *
     * @param pointBuffer     A FloatBuffer containing tightly packed point coordinates (x, y, z).
     * @param maxConvexRadius The maximum convex radius applied to the hull.
     */
    public VxConvexHullShape(FloatBuffer pointBuffer, float maxConvexRadius) {
        this(bufferToArray(pointBuffer), maxConvexRadius);
    }

    @Override
    protected ShapeSettings createSettings() {
        int numPoints = points.length / 3;
        FloatBuffer directBuffer = Jolt.newDirectFloatBuffer(points.length);
        directBuffer.put(points);
        directBuffer.flip();
        ConvexHullShapeSettings settings = new ConvexHullShapeSettings(numPoints, directBuffer);
        settings.setMaxConvexRadius(maxConvexRadius);
        return settings;
    }

    /**
     * @return The flat array of point coordinates defining this hull.
     */
    public float[] getPoints() {
        return points;
    }

    /**
     * @return The maximum convex radius of this hull.
     */
    public float getMaxConvexRadius() {
        return maxConvexRadius;
    }

    private static float[] bufferToArray(FloatBuffer buffer) {
        float[] arr = new float[buffer.remaining()];
        buffer.get(arr);
        buffer.rewind();
        return arr;
    }
}