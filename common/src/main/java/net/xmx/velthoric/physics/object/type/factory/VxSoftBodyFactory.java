/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.type.factory;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.SoftBodyCreationSettings;
import com.github.stephengold.joltjni.SoftBodySharedSettings;

/**
 * A functional interface that serves as a factory for creating Jolt soft bodies.
 * This follows the same inversion of control pattern as {@link VxRigidBodyFactory}.
 */
@FunctionalInterface
public interface VxSoftBodyFactory {
    /**
     * Creates a Jolt soft body from the given settings and adds it to the physics world.
     *
     * @param sharedSettings The shared settings for the soft body's material.
     * @param creationSettings The instance-specific settings for the soft body.
     * @return The Jolt body ID of the created body, or {@link Jolt#cInvalidBodyId} on failure.
     */
    int create(SoftBodySharedSettings sharedSettings, SoftBodyCreationSettings creationSettings);
}