/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.client.body.renderer;

import net.xmx.velthoric.physics.object.type.VxRigidBody;

/**
 * An abstract base renderer for any object that extends {@link VxRigidBody}.
 * Concrete implementations will handle the specific visual representation (e.g., model, texture)
 * of a particular rigid body type.
 *
 * @param <T> The specific type of VxRigidBody this renderer can draw.
 * @author xI-Mx-Ix
 */
public abstract class VxRigidBodyRenderer<T extends VxRigidBody> extends VxBodyRenderer<T> {
}