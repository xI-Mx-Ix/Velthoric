/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.shape;

import com.github.stephengold.joltjni.OffsetCenterOfMassShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.Vec3;

/**
 * A decorated collision shape that shifts the center of mass of an inner shape.
 * <p>
 * This is commonly used for vehicles and other objects where the center of mass
 * should not coincide with the geometric center of the shape.
 *
 * @author xI-Mx-Ix
 */
public class VxOffsetCenterOfMassShape extends VxCollisionShape {

    private final Vec3 offset;
    private final VxCollisionShape inner;

    /**
     * Creates an offset center-of-mass shape wrapper.
     *
     * @param offset The offset to apply to the center of mass.
     * @param inner  The inner collision shape to decorate.
     */
    public VxOffsetCenterOfMassShape(Vec3 offset, VxCollisionShape inner) {
        this.offset = offset;
        this.inner = inner;
    }

    @Override
    protected ShapeSettings createSettings() {
        ShapeSettings innerSettings = inner.createSettings();
        return new OffsetCenterOfMassShapeSettings(offset, innerSettings);
    }

    /**
     * @return The center of mass offset.
     */
    public Vec3 getOffset() {
        return offset;
    }

    /**
     * @return The inner collision shape.
     */
    public VxCollisionShape getInner() {
        return inner;
    }
}