/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.type.factory;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.Jolt;

/**
 * A functional interface that serves as a factory for creating Jolt rigid bodies.
 * This enables an inversion of control pattern, allowing a VxBody to define its creation
 * settings without needing direct access to the physics world or body manager.
 *
 * @author xI-Mx-Ix
 */
@FunctionalInterface
public interface VxRigidBodyFactory {
    /**
     * Creates a Jolt body from the given settings and adds it to the physics world.
     * The implementation of this method (within VxBodyManager) is responsible for
     * the proper resource management of the Jolt objects.
     *
     * @param shapeSettings The settings for the geometric shape.
     * @param bodySettings  The settings for the physical properties.
     * @return The Jolt body ID of the created body, or {@link Jolt#cInvalidBodyId} on failure.
     */
    int create(ShapeSettings shapeSettings, BodyCreationSettings bodySettings);
}