/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.jni;

import java.nio.ByteBuffer;

/**
 * JNI Bridge for the high-performance native Terrain Mesher.
 * <p>
 * This class exposes native C++ functions that generate optimized Jolt
 * shapes from raw chunk snapshot data. It utilizes a greedy meshing algorithm
 * on the native side to combine adjacent blocks into larger faces.
 * </p>
 * <p>
 * Shape caching and Body generation is handled entirely in C++ to minimize
 * JNI overhead and eliminate the need for Java to hold native Shape references.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public final class TerrainMesher {

    private TerrainMesher() {
        // Utility class, do not instantiate
    }

    /**
     * Generates a greedy-meshed shape for the provided chunk voxel data and caches it.
     * If a shape with the same hash already exists, generation is skipped.
     *
     * @param contentHash The unique hash of the chunk's snapshot contents.
     * @param voxels      A DirectByteBuffer containing the 16x16x16 voxel grid.
     * @return {@code true} if the chunk contains solid collision geometry, {@code false} if it's air.
     */
    public static native boolean nGenerateAndCache(int contentHash, ByteBuffer voxels);

    /**
     * Creates a new Jolt Physics Body for the terrain using the cached shape.
     *
     * @param bodyInterfaceVa The native virtual address of the Jolt BodyInterface.
     * @param contentHash     The hash of the shape to use from the cache.
     * @param posX            World X position of the chunk.
     * @param posY            World Y position of the chunk.
     * @param posZ            World Z position of the chunk.
     * @param objectLayer     The Jolt object layer ID for terrain.
     * @return The newly created Jolt BodyID, or 0 (cInvalidBodyID) if it failed.
     */
    public static native int nCreateTerrainBody(long bodyInterfaceVa, int contentHash, float posX, float posY, float posZ, short objectLayer);

    /**
     * Updates the shape of an existing Jolt Body using a cached shape.
     *
     * @param bodyInterfaceVa The native virtual address of the Jolt BodyInterface.
     * @param bodyId          The Jolt BodyID to update.
     * @param contentHash     The hash of the new shape to apply from the cache.
     */
    public static native void nUpdateBodyShape(long bodyInterfaceVa, int bodyId, int contentHash);

    /**
     * Clears the native terrain shape cache.
     * Called during shutdown to free all unreferenced mesh resources.
     */
    public static native void nClearCache();

    /**
     * Registers a terrain material in the native C++ physics cache.
     * @param id The internal material ID (1-255).
     * @param friction The friction coefficient.
     * @param restitution The restitution (bounciness).
     */
    public static native void nRegisterMaterial(int id, float friction, float restitution);
}