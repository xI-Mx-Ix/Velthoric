/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.util;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.BroadPhaseLayerFilter;
import com.github.stephengold.joltjni.ObjectLayerFilter;
import com.github.stephengold.joltjni.Vec3;

/**
 * A thread-local container for temporary Jolt objects used during physics updates.
 * This avoids repeated object allocation in performance-critical loops.
 *
 * @author xI-Mx-Ix
 */
public final class VxUpdateContext {
    public final Vec3 vec3_1 = new Vec3();
    public final Vec3 vec3_2 = new Vec3();
    public final AaBox aabox_1 = new AaBox();
    public final BroadPhaseLayerFilter bplFilter = new BroadPhaseLayerFilter();
    public final ObjectLayerFilter olFilter = new ObjectLayerFilter();
}