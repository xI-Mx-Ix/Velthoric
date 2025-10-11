/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.client.body.renderer;

import net.xmx.velthoric.physics.object.type.VxSoftBody;

/**
 * An abstract base renderer for any object that extends {@link VxSoftBody}.
 * Concrete implementations will handle the specific visual representation, typically
 * involving dynamic mesh generation from the body's vertex data.
 *
 * @param <T> The specific type of VxSoftBody this renderer can draw.
 * @author xI-Mx-Ix
 */
public abstract class VxSoftBodyRenderer<T extends VxSoftBody> extends VxBodyRenderer<T> {
}