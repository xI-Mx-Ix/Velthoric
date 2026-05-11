/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.shape;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RotatedTranslatedShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.Vec3;

/**
 * A decorated collision shape that applies a rotation and translation offset to an inner shape.
 * <p>
 * This is useful for positioning a shape relative to the body's center of mass
 * without modifying the inner shape itself.
 *
 * @author xI-Mx-Ix
 */
public class VxRotatedTranslatedShape extends VxCollisionShape {

    private final Vec3 offset;
    private final Quat rotation;
    private final VxCollisionShape inner;

    /**
     * Creates a rotated-translated shape wrapper.
     *
     * @param offset   The translation offset applied to the inner shape.
     * @param rotation The rotation applied to the inner shape.
     * @param inner    The inner collision shape to decorate.
     */
    public VxRotatedTranslatedShape(Vec3 offset, Quat rotation, VxCollisionShape inner) {
        this.offset = offset;
        this.rotation = rotation;
        this.inner = inner;
    }

    @Override
    protected ShapeSettings createSettings() {
        ShapeSettings innerSettings = inner.createSettings();
        return new RotatedTranslatedShapeSettings(offset, rotation, innerSettings);
    }

    /**
     * @return The translation offset.
     */
    public Vec3 getOffset() {
        return offset;
    }

    /**
     * @return The rotation applied to the inner shape.
     */
    public Quat getRotation() {
        return rotation;
    }

    /**
     * @return The inner collision shape.
     */
    public VxCollisionShape getInner() {
        return inner;
    }
}