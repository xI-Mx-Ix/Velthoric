/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.raycasting;

import com.github.stephengold.joltjni.ObjectLayerFilter;
import net.xmx.velthoric.physics.world.VxLayers;

/**
 * Provides pre-configured object layer filters for physics queries.
 *
 * @author xI-Mx-Ix
 */
public final class VxObjectLayerFilters {

    /**
     * A filter that excludes terrain bodies from collision checks.
     * This is useful for raycasts or other queries that should only interact
     * with dynamic or kinematic objects.
     */
    public static final ObjectLayerFilter IGNORE_TERRAIN = new ObjectLayerFilter() {
        /**
         * Determines whether a collision should occur with a given object layer.
         * <p>
         * This method is intended to be called by the native physics engine during a query.
         * It prevents collisions with the {@link VxLayers#TERRAIN} layer.
         *
         * @param objectLayer the numerical ID of the object layer to check.
         * @return {@code false} if the layer is terrain, {@code true} otherwise.
         */
        @Override
        public boolean shouldCollide(int objectLayer) {
            return objectLayer != VxLayers.TERRAIN;
        }
    };

    private VxObjectLayerFilters() {
        // Private constructor to prevent instantiation.
    }
}