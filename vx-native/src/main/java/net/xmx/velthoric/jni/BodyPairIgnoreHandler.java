/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.jni;

/**
 * A Java wrapper for the native C++ {@code Velthoric::BodyPairIgnoreHandler}.
 * <p>
 * This handler manages a thread-safe list of body pairs that should ignore collisions 
 * with each other. It is designed for high-frequency lookups during the physics tick, 
 * utilizing a lock-free fast path and O(1) hash sets.
 * </p>
 * <p>
 * Ownership: This object owns its native C++ counterpart. Calling {@link #close()} 
 * will securely release the native memory.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public class BodyPairIgnoreHandler extends NativeObject {

    /**
     * Constructs a new BodyPairIgnoreHandler and allocates its native C++ counterpart.
     */
    public BodyPairIgnoreHandler() {
        super(nCreateHandler());
    }

    /**
     * Safely destroys the native handler. Called automatically by {@link #close()}.
     * 
     * @param address The virtual address of the native object.
     */
    @Override
    protected void nClose(long address) {
        nDestroyHandler(address);
    }

    /**
     * Adds a body pair to the native ignore list. Collisions between these two bodies 
     * will be suppressed until removed or cleared.
     *
     * @param bodyId1 The first Jolt body ID (index and sequence).
     * @param bodyId2 The second Jolt body ID (index and sequence).
     */
    public void ignorePair(int bodyId1, int bodyId2) {
        nIgnorePair(va(), bodyId1, bodyId2);
    }

    /**
     * Removes a body pair from the native ignore list, restoring normal collision behavior.
     *
     * @param bodyId1 The first Jolt body ID.
     * @param bodyId2 The second Jolt body ID.
     */
    public void removeIgnorePair(int bodyId1, int bodyId2) {
        nRemoveIgnorePair(va(), bodyId1, bodyId2);
    }

    /**
     * Checks if a specific body pair is currently being ignored by the native system.
     *
     * @param bodyId1 The first Jolt body ID.
     * @param bodyId2 The second Jolt body ID.
     * @return {@code true} if collisions are currently suppressed, {@code false} otherwise.
     */
    public boolean isPairIgnored(int bodyId1, int bodyId2) {
        return nIsPairIgnored(va(), bodyId1, bodyId2);
    }

    /**
     * Retrieves all currently ignored body pairs as a flattened integer array.
     *
     * @return A flattened array of pairs: [b1_1, b1_2, b2_1, b2_2, ...], or {@code null} if empty.
     */
    public int[] getIgnoredPairs() {
        return nGetIgnoredPairs(va());
    }

    /**
     * Notifies the native handler that a body was removed from the world.
     * All collision filters involving this body will be automatically purged.
     *
     * @param bodyId The Jolt body ID that was removed.
     */
    public void onBodyRemoved(int bodyId) {
        nOnBodyRemoved(va(), bodyId);
    }

    /**
     * Clears all active collision filters in the native handler.
     */
    public void clear() {
        nClear(va());
    }

    /**
     * Returns the total number of body pairs currently stored in the native ignore list.
     *
     * @return The count of ignored pairs.
     */
    public int size() {
        return nSize(va());
    }

    // Native JNI Bridge

    /**
     * Allocates a new native BodyPairIgnoreHandler on the heap.
     * @return Virtual address of the new instance.
     */
    private static native long nCreateHandler();

    /**
     * Securely deletes the native handler.
     * @param address Virtual address of the instance.
     */
    private static native void nDestroyHandler(long address);

    /**
     * Adds a normalized pair key to the native set.
     * @param address Virtual address of the handler.
     * @param bodyId1 First body ID.
     * @param bodyId2 Second body ID.
     */
    private static native void nIgnorePair(long address, int bodyId1, int bodyId2);

    /**
     * Removes a pair key from the native set.
     * @param address Virtual address of the handler.
     * @param bodyId1 First body ID.
     * @param bodyId2 Second body ID.
     */
    private static native void nRemoveIgnorePair(long address, int bodyId1, int bodyId2);

    /**
     * Performs a thread-safe check if a pair is ignored.
     * @param address Virtual address of the handler.
     * @param bodyId1 First body ID.
     * @param bodyId2 Second body ID.
     * @return True if ignored.
     */
    private static native boolean nIsPairIgnored(long address, int bodyId1, int bodyId2);

    /**
     * Flattens the native set into a Java int array.
     * @param address Virtual address of the handler.
     * @return Flattened array of pairs.
     */
    private static native int[] nGetIgnoredPairs(long address);

    /**
     * Purges all pairs involving the specified body ID.
     * @param address Virtual address of the handler.
     * @param bodyId Body ID to cleanup.
     */
    private static native void nOnBodyRemoved(long address, int bodyId);

    /**
     * Atomically clears all filters.
     * @param address Virtual address of the handler.
     */
    private static native void nClear(long address);

    /**
     * Returns the number of ignored pairs.
     * @param address Virtual address of the handler.
     * @return Pair count.
     */
    private static native int nSize(long address);
}