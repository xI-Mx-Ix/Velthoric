/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.shape;

import com.github.stephengold.joltjni.EmptyShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;

/**
 * An empty collision shape with no geometry.
 * <p>
 * This shape is useful for bodies that need to exist in the physics world
 * but should not collide with anything.
 *
 * @author xI-Mx-Ix
 */
public class VxEmptyShape extends VxCollisionShape {

    /**
     * Singleton instance, since an empty shape has no configuration.
     */
    public static final VxEmptyShape INSTANCE = new VxEmptyShape();

    private VxEmptyShape() {
    }

    @Override
    protected ShapeSettings createSettings() {
        return new EmptyShapeSettings();
    }
}