/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.job;

/**
 * Defines the priority levels for terrain generation tasks.
 * This allows the system to prioritize chunks that are immediately needed by players.
 *
 * @author xI-Mx-Ix
 */
public enum VxTaskPriority {
    /** Low priority, for background loading far from any activity. */
    LOW,
    /** Default priority for standard operations like block updates. */
    MEDIUM,
    /** High priority, for chunks in the immediate preloading vicinity of an object. */
    HIGH,
    /** Critical priority, for chunks that are actively needed for simulation right now. */
    CRITICAL,
}