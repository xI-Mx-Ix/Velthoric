/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.job;

/**
 * Defines the priority levels for terrain generation tasks.
 * This is currently unused but kept for potential future use in a priority queue system.
 *
 * @author xI-Mx-Ix
 */
public enum VxTaskPriority {
    /**
     * Lowest priority, for background tasks.
     */
    LOW,
    /**
     * Default priority for standard block updates.
     */
    MEDIUM,
    /**
     * High priority, for chunks that are about to be needed.
     */
    HIGH,
    /**
     * Highest priority, for chunks that are immediately required for simulation.
     */
    CRITICAL,
}