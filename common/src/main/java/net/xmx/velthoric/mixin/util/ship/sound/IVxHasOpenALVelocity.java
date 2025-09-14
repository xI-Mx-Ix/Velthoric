/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.util.ship.sound;

import org.joml.Vector3d;

public interface IVxHasOpenALVelocity {
    void velthoric$setVelocity(Vector3d velocity);
}