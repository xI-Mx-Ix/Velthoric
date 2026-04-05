/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.shape;

import com.github.stephengold.joltjni.MutableCompoundShapeSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A compound collision shape that combines multiple child shapes into a single shape
 * which can be modified after creation.
 * <p>
 * Unlike {@link VxStaticCompoundShape}, child shapes can be added or removed after
 * the compound has been instantiated in the physics simulation.
 * <p>
 * Each child shape is positioned and rotated relative to the compound's local origin.
 *
 * @author xI-Mx-Ix
 */
public class VxMutableCompoundShape extends VxCollisionShape {

    private final List<ChildShape> children = new ArrayList<>();

    /**
     * Adds a child shape at the specified local position with no rotation.
     *
     * @param shape    The child collision shape.
     * @param position The local position of the child shape.
     * @return This instance for chaining.
     */
    public VxMutableCompoundShape addShape(VxCollisionShape shape, Vec3 position) {
        return addShape(shape, position, Quat.sIdentity());
    }

    /**
     * Adds a child shape at the specified local position and rotation.
     *
     * @param shape    The child collision shape.
     * @param position The local position of the child shape.
     * @param rotation The local rotation of the child shape.
     * @return This instance for chaining.
     */
    public VxMutableCompoundShape addShape(VxCollisionShape shape, Vec3 position, Quat rotation) {
        children.add(new ChildShape(shape, position, rotation));
        return this;
    }

    @Override
    protected ShapeSettings createSettings() {
        MutableCompoundShapeSettings settings = new MutableCompoundShapeSettings();
        for (ChildShape child : children) {
            try (ShapeSettings childSettings = child.shape.createSettings()) {
                settings.addShape(child.position, child.rotation, childSettings);
            }
        }
        return settings;
    }

    /**
     * @return An unmodifiable view of the child shapes.
     */
    public List<ChildShape> getChildren() {
        return List.copyOf(children);
    }

    /**
     * Represents a child shape with its local transform within the compound.
     */
    public record ChildShape(VxCollisionShape shape, Vec3 position, Quat rotation) {
    }
}