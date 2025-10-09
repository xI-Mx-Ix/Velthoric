/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.BroadPhaseLayerFilter;
import com.github.stephengold.joltjni.ObjectLayerFilter;
import com.github.stephengold.joltjni.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * A thread-local container for temporary objects used during physics updates.
 * This avoids repeated object allocation and reduces garbage collection pressure by
 * reusing expensive objects like collections and Jolt data structures.
 *
 * @author xI-Mx-Ix
 */
public class VxUpdateContext {
    // Jolt-specific temporary objects
    public final Vec3 vec3_1 = new Vec3();
    public final Vec3 vec3_2 = new Vec3();
    public final AaBox aabox_1 = new AaBox();
    public final BroadPhaseLayerFilter bplFilter = new BroadPhaseLayerFilter();
    public final ObjectLayerFilter olFilter = new ObjectLayerFilter();

    // Reusable collections for the terrain tracker to avoid allocations in the update loop
    public final Set<Long> requiredChunksSet = new HashSet<>(2048);
    public final Set<Long> toAddSet = new HashSet<>(512);
    public final Set<Long> toRemoveSet = new HashSet<>(512);
}