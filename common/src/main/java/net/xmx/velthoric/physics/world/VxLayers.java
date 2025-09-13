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
    public static final short STATIC = 0;
    public static final short DYNAMIC = 1;
    public static final short KINEMATIC = 2;
    public static final short TERRAIN = 3;
    public static final short NUM_LAYERS = 4;
}
