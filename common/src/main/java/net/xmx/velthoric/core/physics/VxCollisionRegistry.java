/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.physics;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A central registry for allocating unique collision group IDs.
 * <p>
 * This ensures that different mods using the Velthoric API can perform
 * group-based collision filtering without overlapping or conflicting
 * with each other's internal IDs.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public final class VxCollisionRegistry {

    /**
     * The next available group ID.
     */
    private static final AtomicInteger nextGroupId = new AtomicInteger(1);

    private VxCollisionRegistry() {
        // Utility class to prevent instantiation
    }

    /**
     * Claims a unique Collision Group ID for use in Jolt's collision filtering.
     * <p>
     * Mods should call this during their initialization phase and store the
     * returned value for later use in their {@code GroupFilter} objects.
     * </p>
     *
     * @return A unique integer ID to identify a collision group.
     */
    public static int claimGroupId() {
        return nextGroupId.getAndIncrement();
    }
}