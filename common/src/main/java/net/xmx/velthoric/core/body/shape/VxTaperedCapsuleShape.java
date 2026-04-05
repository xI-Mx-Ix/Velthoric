/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.shape;

import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.TaperedCapsuleShapeSettings;

/**
 * A tapered capsule collision shape — a capsule where the top and bottom hemispheres
 * can have different radii.
 * <p>
 * The shape is oriented along the Y axis, centered at the origin.
 *
 * @author xI-Mx-Ix
 */
public class VxTaperedCapsuleShape extends VxCollisionShape {

    private final float halfHeight;
    private final float topRadius;
    private final float bottomRadius;

    /**
     * Creates a tapered capsule shape with the specified dimensions.
     *
     * @param halfHeight   The half-height of the tapered section.
     * @param topRadius    The radius at the top of the capsule.
     * @param bottomRadius The radius at the bottom of the capsule.
     */
    public VxTaperedCapsuleShape(float halfHeight, float topRadius, float bottomRadius) {
        this.halfHeight = halfHeight;
        this.topRadius = topRadius;
        this.bottomRadius = bottomRadius;
    }

    @Override
    protected ShapeSettings createSettings() {
        return new TaperedCapsuleShapeSettings(halfHeight, topRadius, bottomRadius);
    }

    /**
     * @return The half-height of the tapered section.
     */
    public float getHalfHeight() {
        return halfHeight;
    }

    /**
     * @return The radius at the top of the capsule.
     */
    public float getTopRadius() {
        return topRadius;
    }

    /**
     * @return The radius at the bottom of the capsule.
     */
    public float getBottomRadius() {
        return bottomRadius;
    }
}