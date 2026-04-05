/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.shape;

import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.SphereShapeSettings;

/**
 * A spherical collision shape defined by a radius.
 *
 * @author xI-Mx-Ix
 */
public class VxSphereShape extends VxCollisionShape {

    private final float radius;

    /**
     * Creates a sphere shape with the specified radius.
     *
     * @param radius The radius of the sphere.
     */
    public VxSphereShape(float radius) {
        this.radius = radius;
    }

    @Override
    protected ShapeSettings createSettings() {
        return new SphereShapeSettings(radius);
    }

    /**
     * @return The radius of this sphere shape.
     */
    public float getRadius() {
        return radius;
    }
}