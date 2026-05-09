/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.jni;

import java.nio.ByteBuffer;

/**
 * JNI bridge for the native C++ {@code Velthoric::TerrainSystem}.
 * <p>
 * This class exposes the native terrain state management, body lifecycle,
 * and chunk data submission functions. All state is held in C++ to eliminate
 * JNI overhead for high-frequency queries.
 * </p>
 * <p>
 * Inherits from {@link NativeObject} to guarantee safe memory cleanup of the
 * native pointer via {@link #close()}.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public final class TerrainSystem extends NativeObject {

    /**
     * Creates a new native TerrainSystem instance.
     *
     * @param bodyInterfaceVa The native virtual address of the locking Jolt BodyInterface.
     * @param terrainLayer    The Jolt object layer assigned to terrain bodies.
     */
    public TerrainSystem(long bodyInterfaceVa, short terrainLayer) {
        super(nCreate(bodyInterfaceVa, terrainLayer));
    }

    /**
     * Requests a terrain chunk, incrementing its reference count.
     * If this is the first reference, the chunk is registered as pending data.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return {@code true} if this is a new chunk that needs voxel data submission.
     */
    public boolean requestChunk(long packedPos) {
        return nRequestChunk(va(), packedPos);
    }

    /**
     * Releases a terrain chunk, decrementing its reference count.
     * When the count reaches zero, the physics body is destroyed.
     *
     * @param packedPos The bit-packed section coordinate.
     */
    public void releaseChunk(long packedPos) {
        nReleaseChunk(va(), packedPos);
    }

    /**
     * Activates a chunk, adding its physics body to the simulation.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return {@code true} if the chunk is a placeholder and needs re-submission.
     */
    public boolean activateChunk(long packedPos) {
        return nActivateChunk(va(), packedPos);
    }

    /**
     * Deactivates a chunk, removing its physics body from the simulation.
     * The body is retained and can be re-activated later.
     *
     * @param packedPos The bit-packed section coordinate.
     */
    public void deactivateChunk(long packedPos) {
        nDeactivateChunk(va(), packedPos);
    }

    /**
     * Wakes up physics bodies in a 5x5x5 box around the given block position.
     *
     * @param x World X coordinate.
     * @param y World Y coordinate.
     * @param z World Z coordinate.
     */
    public void onBlockUpdate(int x, int y, int z) {
        nOnBlockUpdate(va(), x, y, z);
    }

    /**
     * Checks if a chunk should be prioritized for data submission.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return {@code true} if the chunk needs voxel data.
     */
    public boolean prioritizeChunk(long packedPos) {
        return nPrioritizeChunk(va(), packedPos);
    }

    /**
     * Submits voxel box data for a chunk, creating or updating its physics body natively.
     * The shape is generated in C++ via the TerrainGenerator and cached by content hash.
     *
     * @param packedPos      The bit-packed section coordinate.
     * @param posX           World X position for body placement.
     * @param posY           World Y position for body placement.
     * @param posZ           World Z position for body placement.
     * @param boxData        A DirectByteBuffer containing BoxShapeData structs.
     * @param boxCount       Number of BoxShapeData structs in the buffer.
     * @param contentHash    Unique hash of the chunk content for shape caching.
     * @param isInitialBuild {@code true} if this is the first build (marks as placeholder).
     * @return {@code true} if the chunk contains solid collision geometry.
     */
    public boolean submitChunkData(long packedPos, float posX, float posY, float posZ,
                                   ByteBuffer boxData, int boxCount, int contentHash, boolean isInitialBuild) {
        return nSubmitChunkData(va(), packedPos, posX, posY, posZ, boxData, boxCount, contentHash, isInitialBuild);
    }

    /**
     * Checks if a chunk is in a ready state (active, inactive, or air).
     *
     * @param packedPos The bit-packed section coordinate.
     * @return {@code true} if ready.
     */
    public boolean isReady(long packedPos) {
        return nIsReady(va(), packedPos);
    }

    /**
     * Checks if a chunk is using a placeholder shape.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return {@code true} if placeholder.
     */
    public boolean isPlaceholder(long packedPos) {
        return nIsPlaceholder(va(), packedPos);
    }

    /**
     * Checks if a chunk is managed by the terrain system.
     *
     * @param packedPos The bit-packed section coordinate.
     * @return {@code true} if managed.
     */
    public boolean isManaged(long packedPos) {
        return nIsManaged(va(), packedPos);
    }

    /**
     * Checks if a Jolt body ID belongs to a terrain chunk. O(1) lookup.
     *
     * @param bodyId The Jolt body ID.
     * @return {@code true} if the body is a terrain body.
     */
    public boolean isTerrainBody(int bodyId) {
        return nIsTerrainBody(va(), bodyId);
    }

    /**
     * Returns the packed positions of all currently active terrain chunks.
     *
     * @return A long array of bit-packed section coordinates.
     */
    public long[] getActiveChunkPositions() {
        return nGetActiveChunkPositions(va());
    }

    /**
     * Destroys all physics bodies managed by the terrain system.
     */
    public void cleanupAllBodies() {
        nCleanupAllBodies(va());
    }

    /**
     * Internal handler to execute native destruction logic when the object is closed.
     *
     * @param address The native virtual address to be freed.
     */
    @Override
    protected void nClose(long address) {
        nDestroy(address);
    }

    /**
     * Registers the custom TerrainVoxelShape natively in the Jolt collision dispatcher.
     */
    public static native void nRegisterVoxelShape();

    /**
     * Registers a terrain material in the native C++ physics cache.
     *
     * @param id          The internal material ID (1-65535).
     * @param friction    The friction coefficient.
     * @param restitution The restitution (bounciness).
     */
    public static native void nRegisterMaterial(int id, float friction, float restitution);

    /**
     * Clears the global native terrain shape cache.
     */
    public static native void nClearShapeCache();

    /**
     * Creates a new native TerrainSystem instance.
     *
     * @param bodyInterfaceVa The native virtual address of the locking Jolt BodyInterface.
     * @param terrainLayer    The Jolt object layer assigned to terrain bodies.
     * @return The native pointer address of the new TerrainSystem instance.
     */
    private static native long nCreate(long bodyInterfaceVa, short terrainLayer);

    /**
     * Destroys a native TerrainSystem instance and frees its memory.
     *
     * @param handle The native pointer address of the TerrainSystem.
     */
    private static native void nDestroy(long handle);

    /**
     * Native implementation to request a chunk.
     *
     * @param handle    The native pointer address of the TerrainSystem.
     * @param packedPos The bit-packed section coordinate.
     * @return {@code true} if this is a new chunk that needs data.
     */
    private static native boolean nRequestChunk(long handle, long packedPos);

    /**
     * Native implementation to release a chunk.
     *
     * @param handle    The native pointer address of the TerrainSystem.
     * @param packedPos The bit-packed section coordinate.
     */
    private static native void nReleaseChunk(long handle, long packedPos);

    /**
     * Native implementation to activate a chunk.
     *
     * @param handle    The native pointer address of the TerrainSystem.
     * @param packedPos The bit-packed section coordinate.
     * @return {@code true} if the chunk is a placeholder.
     */
    private static native boolean nActivateChunk(long handle, long packedPos);

    /**
     * Native implementation to deactivate a chunk.
     *
     * @param handle    The native pointer address of the TerrainSystem.
     * @param packedPos The bit-packed section coordinate.
     */
    private static native void nDeactivateChunk(long handle, long packedPos);

    /**
     * Native implementation to check if a chunk needs to be prioritized.
     *
     * @param handle    The native pointer address of the TerrainSystem.
     * @param packedPos The bit-packed section coordinate.
     * @return {@code true} if the chunk needs priority data.
     */
    private static native boolean nPrioritizeChunk(long handle, long packedPos);

    /**
     * Native implementation to submit voxel box data.
     *
     * @param handle         The native pointer address of the TerrainSystem.
     * @param packedPos      The bit-packed section coordinate.
     * @param posX           World X position for body placement.
     * @param posY           World Y position for body placement.
     * @param posZ           World Z position for body placement.
     * @param boxData        A DirectByteBuffer containing BoxShapeData structs.
     * @param boxCount       Number of BoxShapeData structs in the buffer.
     * @param contentHash    Unique hash of the chunk content for shape caching.
     * @param isInitialBuild {@code true} if this is the first build.
     * @return {@code true} if the chunk contains solid collision geometry.
     */
    private static native boolean nSubmitChunkData(long handle, long packedPos,
                                                   float posX, float posY, float posZ,
                                                   ByteBuffer boxData, int boxCount,
                                                   int contentHash, boolean isInitialBuild);

    /**
     * Native implementation to check if a chunk is ready.
     *
     * @param handle    The native pointer address of the TerrainSystem.
     * @param packedPos The bit-packed section coordinate.
     * @return {@code true} if ready.
     */
    private static native boolean nIsReady(long handle, long packedPos);

    /**
     * Native implementation to check if a chunk uses a placeholder shape.
     *
     * @param handle    The native pointer address of the TerrainSystem.
     * @param packedPos The bit-packed section coordinate.
     * @return {@code true} if placeholder.
     */
    private static native boolean nIsPlaceholder(long handle, long packedPos);

    /**
     * Native implementation to check if a chunk is managed.
     *
     * @param handle    The native pointer address of the TerrainSystem.
     * @param packedPos The bit-packed section coordinate.
     * @return {@code true} if managed.
     */
    private static native boolean nIsManaged(long handle, long packedPos);

    /**
     * Native implementation to check if a body ID belongs to a terrain chunk.
     *
     * @param handle The native pointer address of the TerrainSystem.
     * @param bodyId The Jolt body ID.
     * @return {@code true} if terrain body.
     */
    private static native boolean nIsTerrainBody(long handle, int bodyId);

    /**
     * Native implementation to return active chunk positions.
     *
     * @param handle The native pointer address of the TerrainSystem.
     * @return Array of packed coordinates.
     */
    private static native long[] nGetActiveChunkPositions(long handle);

    /**
     * Native implementation to clean up all bodies.
     *
     * @param handle The native pointer address of the TerrainSystem.
     */
    private static native void nCleanupAllBodies(long handle);

    /**
     * Native implementation to handle a block update.
     *
     * @param handle Native pointer address.
     * @param x World X.
     * @param y World Y.
     * @param z World Z.
     */
    private static native void nOnBlockUpdate(long handle, int x, int y, int z);
}