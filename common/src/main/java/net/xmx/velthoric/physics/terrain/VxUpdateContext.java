/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain;

import com.github.stephengold.joltjni.*;

/**
 * A container for thread-local, reusable Jolt physics objects.
 * <p>
 * This class helps to reduce garbage collection pressure by providing pre-allocated
 * objects for common physics calculations within a single thread's execution context.
 *
 * @author xI-Mx-Ix
 */
public class VxUpdateContext {
    final AaBox aabox_1 = new AaBox();
    final BroadPhaseLayerFilter bplFilter = new BroadPhaseLayerFilter();
    final ObjectLayerFilter olFilter = new ObjectLayerFilter();
}