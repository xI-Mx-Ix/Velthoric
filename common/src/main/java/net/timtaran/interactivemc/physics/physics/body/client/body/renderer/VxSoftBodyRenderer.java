/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.physics.body.client.body.renderer;

import net.timtaran.interactivemc.physics.physics.body.type.VxSoftBody;

/**
 * An abstract base renderer for any body that extends {@link VxSoftBody}.
 * Concrete implementations will handle the specific visual representation, typically
 * involving dynamic mesh generation from the body's vertex data.
 *
 * @param <T> The specific type of VxSoftBody this renderer can draw.
 * @author xI-Mx-Ix
 */
public abstract class VxSoftBodyRenderer<T extends VxSoftBody> extends VxBodyRenderer<T> {
}