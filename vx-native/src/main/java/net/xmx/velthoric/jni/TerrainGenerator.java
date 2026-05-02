/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.jni;

import java.nio.ByteBuffer;

/**
 * JNI Bridge for the high-performance native Terrain Generator.
 * <p>
 * This class exposes native C++ functions that generate optimized Jolt
 * shapes from raw chunk bounds data. It creates a StaticCompoundShape
 * natively to group individual BoxShapes.
 * </p>
 * <p>
 * Shape caching and Body generation is handled entirely in C++ to minimize
 * JNI overhead and eliminate the need for Java to hold native Shape references.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public final class TerrainGenerator {

    private TerrainGenerator() {
        // Utility class, do not instantiate
    }

    /**
     * Generates a StaticCompoundShape for the provided array of BoxShapeData and caches it.
     * If a shape with the same hash already exists, generation is skipped.
     *
     * @param contentHash The unique hash of the chunk's snapshot contents.
     * @param boxData     A DirectByteBuffer containing the array of BoxShapeData structs.
     * @param boxCount    The number of BoxShapeData structs in the buffer.
     * @return {@code true} if the chunk contains solid collision geometry, {@code false} if it's empty.
     */
    public static native boolean nGenerateAndCache(int contentHash, ByteBuffer boxData, int boxCount);

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