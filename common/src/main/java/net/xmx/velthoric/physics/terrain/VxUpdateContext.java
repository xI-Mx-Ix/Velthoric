/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.BroadPhaseLayerFilter;
import com.github.stephengold.joltjni.ObjectLayerFilter;
import com.github.stephengold.joltjni.Vec3;

/**
 * A thread-local container for temporary Jolt physics objects used during updates.
 * This avoids repeated object allocation and reduces garbage collection pressure.
 *
 * @author xI-Mx-Ix
 */
public class VxUpdateContext {
    public final Vec3 vec3_1 = new Vec3();
    public final Vec3 vec3_2 = new Vec3();
    public final AaBox aabox_1 = new AaBox();
    public final BroadPhaseLayerFilter bplFilter = new BroadPhaseLayerFilter();
    public final ObjectLayerFilter olFilter = new ObjectLayerFilter();
}