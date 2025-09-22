/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain;

import com.github.stephengold.joltjni.*;

public class UpdateContext {
    final Vec3 vec3_1 = new Vec3();
    final Vec3 vec3_2 = new Vec3();
    final AaBox aabox_1 = new AaBox();
    final BroadPhaseLayerFilter bplFilter = new BroadPhaseLayerFilter();
    final ObjectLayerFilter olFilter = new ObjectLayerFilter();
}