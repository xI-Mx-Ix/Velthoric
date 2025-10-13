/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.client.body.renderer;

import net.xmx.velthoric.physics.body.type.VxRigidBody;

/**
 * An abstract base renderer for any body that extends {@link VxRigidBody}.
 * Concrete implementations will handle the specific visual representation (e.g., model, texture)
 * of a particular rigid body type.
 *
 * @param <T> The specific type of VxRigidBody this renderer can draw.
 * @author xI-Mx-Ix
 */
public abstract class VxRigidBodyRenderer<T extends VxRigidBody> extends VxBodyRenderer<T> {
}