/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.intersection.raycast;

import com.github.stephengold.joltjni.BodyFilter;
import com.github.stephengold.joltjni.BroadPhaseLayerFilter;
import com.github.stephengold.joltjni.ObjectLayerFilter;
import net.xmx.velthoric.core.physics.VxPhysicsLayers;

/**
 * Provides cached, high-performance Jolt filters for raycasting.
 * <p>
 * Reusing these static instances prevents JNI overhead and garbage collection pressure
 * during frequent raycast operations (e.g., Physics Gun usage).
 *
 * @author xI-Mx-Ix
 */
public final class VxRaycastFilters {

    /**
     * A standard broad-phase filter that permits collision with all layers.
     */
    public static final BroadPhaseLayerFilter BROADPHASE_ALL = new BroadPhaseLayerFilter();

    /**
     * A standard body filter that permits collision with all bodies.
     */
    public static final BodyFilter BODY_ALL = new BodyFilter();

    /**
     * A highly optimized object layer filter that explicitly ignores the TERRAIN layer.
     * This ensures the Physics Gun or other tools do not interact with the static map mesh.
     */
    public static final ObjectLayerFilter IGNORE_TERRAIN = new ObjectLayerFilter() {
        @Override
        public boolean shouldCollide(int objectLayer) {
            // Only collide if the layer is NOT terrain.
            return objectLayer != VxPhysicsLayers.TERRAIN;
        }
    };

    /**
     * A standard object layer filter that allows everything.
     */
    public static final ObjectLayerFilter OBJECT_ALL = new ObjectLayerFilter();

    private VxRaycastFilters() {
        // Prevent instantiation
    }
}