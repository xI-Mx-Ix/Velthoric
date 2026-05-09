/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.jni;

/**
 * JNI bindings for the custom Jolt TerrainVoxelShape.
 * <p>
 * This class provides access to the native registration logic for the
 * optimized voxel terrain shape, ensuring that it is properly integrated
 * with Jolt's collision dispatcher.
 *
 * @author xI-Mx-Ix
 */
public class TerrainVoxelShape {
    
    /**
     * Registers the custom TerrainVoxelShape natively in the Jolt collision dispatcher.
     * <p>
     * Must be called exactly once during the physics engine bootstrap phase to enable
     * collisions between standard shapes (Box, Sphere, Convex, etc.) and the voxel terrain.
     */
    public static native void nRegisterVoxelShape();
}