/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.jni;

import java.nio.ByteBuffer;

/**
 * JNI bridge for the native C++ {@code Velthoric::TerrainTracker}.
 * <p>
 * This class exposes the highly optimized native terrain tracking update loop. 
 * The underlying C++ structure automatically interfaces with the Jolt Physics engine,
 * dynamically tracks all moving rigid and kinematic bodies, clusters them, and orchestrates
 * the loading and unloading of static terrain chunks ahead of their movement vectors.
 * </p>
 * <p>
 * This class extends {@link NativeObject} to provide deterministic memory management
 * and safe pointer deallocation upon disposal.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public final class TerrainTracker extends NativeObject {

    /**
     * Creates a new native TerrainTracker instance.
     * <p>
     * Allocates the {@code Velthoric::TerrainTracker} in C++ memory and binds it to the specified
     * Jolt PhysicsSystem and Velthoric TerrainSystem.
     * </p>
     *
     * @param physicsSystemVa The native virtual memory address (pointer) of the Jolt {@code PhysicsSystem}.
     * @param terrainSystemVa The native virtual memory address (pointer) of the Velthoric {@code TerrainSystem}.
     * @param terrainLayer    The 16-bit Jolt object layer reserved exclusively for static terrain bodies.
     */
    public TerrainTracker(long physicsSystemVa, long terrainSystemVa, short terrainLayer) {
        super(nCreate(physicsSystemVa, terrainSystemVa, terrainLayer));
    }

    /**
     * Updates the terrain tracker natively by analyzing the velocities and positions of all active bodies.
     * <p>
     * This method executes the clustering and spatial hash algorithm in C++ and computes 
     * which chunks need to be loaded, activated, or released based on velocity vectors and bounding boxes.
     * It populates the provided arrays with the coordinates of the chunks that require interaction.
     * </p>
     *
     * @param outInitialChunks DirectByteBuffer pre-allocated by Java to store bit-packed chunk positions that 
     *                         need a completely new initial voxel data build. Memory is accessed pointer-to-pointer.
     * @param outUpdateChunks  DirectByteBuffer pre-allocated by Java to store bit-packed chunk positions that
     *                         are already known but require prioritized data updates. Memory is accessed pointer-to-pointer.
     * @return A 64-bit packed integer containing the lengths of the populated arrays:
     *         The upper 32 bits represent the number of entries written to {@code outInitialChunks}.
     *         The lower 32 bits represent the number of entries written to {@code outUpdateChunks}.
     */
    public long update(ByteBuffer outInitialChunks, ByteBuffer outUpdateChunks) {
        return nUpdate(va(), outInitialChunks, outUpdateChunks);
    }

    /**
     * Clears all tracking state and native caches.
     * <p>
     * This method instructs the C++ backend to forget all previously required chunks, 
     * actively releasing and deactivating them through the linked native {@code TerrainSystem}.
     * </p>
     */
    public void clear() {
        nClear(va());
    }

    /**
     * Lifecycle method invoked during the destruction of this NativeObject.
     * Dispatches the native pointer to C++ for deletion, freeing the allocated memory.
     *
     * @param address The virtual memory address of the C++ object.
     */
    @Override
    protected void nClose(long address) {
        nDestroy(address);
    }

    /**
     * Native C++ implementation: Instantiates the Velthoric::TerrainTracker.
     *
     * @param physicsSystemVa Native pointer to JPH::PhysicsSystem.
     * @param terrainSystemVa Native pointer to Velthoric::TerrainSystem.
     * @param terrainLayer    The object layer ID for terrain.
     * @return The 64-bit memory address of the newly allocated TerrainTracker.
     */
    private static native long nCreate(long physicsSystemVa, long terrainSystemVa, short terrainLayer);

    /**
     * Native C++ implementation: Destroys the Velthoric::TerrainTracker instance.
     *
     * @param handle The 64-bit memory address of the C++ TerrainTracker object.
     */
    private static native void nDestroy(long handle);

    /**
     * Native C++ implementation: Executes the physics-driven chunk tracking update loop.
     *
     * @param handle           The 64-bit memory address of the C++ TerrainTracker object.
     * @param outInitialChunks DirectByteBuffer for writing initial chunk positions directly.
     * @param outUpdateChunks  DirectByteBuffer for writing update chunk positions directly.
     * @return A packed 64-bit long: (initialCount << 32) | updateCount.
     */
    private static native long nUpdate(long handle, ByteBuffer outInitialChunks, ByteBuffer outUpdateChunks);

    /**
     * Native C++ implementation: Clears tracking structures and releases chunks.
     *
     * @param handle The 64-bit memory address of the C++ TerrainTracker object.
     */
    private static native void nClear(long handle);
}