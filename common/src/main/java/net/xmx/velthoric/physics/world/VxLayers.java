/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.world;

/**
 * Defines the object layers used within the Jolt physics simulation.
 * Layers are used to efficiently determine which types of objects can collide with each other.
 *
 * @author xI-Mx-Ix
 */
public class VxLayers {
    /** The layer for immovable objects like terrain and static geometry. */
    public static final short STATIC = 0;
    /** The layer for movable objects that are affected by forces, like vehicles or debris. */
    public static final short DYNAMIC = 1;
    /** The total number of defined layers. */
    public static final short NUM_LAYERS = 2;
}