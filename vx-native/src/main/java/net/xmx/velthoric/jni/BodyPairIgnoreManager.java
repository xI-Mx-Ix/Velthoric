/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.jni;

import java.util.ArrayList;
import java.util.List;

/**
 * A Java wrapper representing the native C++ {@code Velthoric::BodyPairIgnoreManager}.
 * <p>
 * This class provides a high-performance mechanism to ignore collisions between specific
 * body pairs in the Jolt Physics system. Unlike collision groups, this approach allows
 * fine-grained control over individual body pairs without the limitations of group-based
 * filtering, making it ideal for scenarios like grabbing mechanics where bodies may be
 * nested inside grabbers.
 * </p>
 * <p>
 * The implementation uses a thread-safe {@code std::unordered_set} in C++ for O(1) lookup
 * performance, and automatically cleans up invalid body IDs when bodies are removed from
 * the physics system via the {@code onBodyRemoved} callback.
 * </p>
 * <p>
 * Because it extends {@link NativeObject}, the manager's native memory is automatically
 * guaranteed to be freed when {@link #close()} is called, preventing use-after-free or
 * double-free errors.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public class BodyPairIgnoreManager extends NativeObject {

    /**
     * Constructs a new native BodyPairIgnoreManager.
     * <p>
     * This allocates the corresponding {@code Velthoric::BodyPairIgnoreManager} C++ object
     * and stores its address in the parent {@link NativeObject}.
     * </p>
     */
    public BodyPairIgnoreManager() {
        super(nCreateManager());
    }

    /**
     * Called internally by {@link #close()} to destroy the native C++ object.
     *
     * @param address The native virtual address of the BodyPairIgnoreManager to destroy.
     */
    @Override
    protected void nClose(long address) {
        nDestroyManager(address);
    }

    /**
     * Adds a body pair to the ignore list, preventing collisions between them.
     * <p>
     * The pair is stored in a normalized order (smaller ID first) to ensure
     * that {@code ignorePair(a, b)} and {@code ignorePair(b, a)} are equivalent.
     * </p>
     *
     * @param bodyId1 The first body ID.
     * @param bodyId2 The second body ID.
     */
    public void ignorePair(int bodyId1, int bodyId2) {
        nIgnorePair(va(), bodyId1, bodyId2);
    }

    /**
     * Removes a body pair from the ignore list, restoring normal collision behavior.
     *
     * @param bodyId1 The first body ID.
     * @param bodyId2 The second body ID.
     */
    public void removeIgnorePair(int bodyId1, int bodyId2) {
        nRemoveIgnorePair(va(), bodyId1, bodyId2);
    }

    /**
     * Checks if a body pair is currently being ignored.
     *
     * @param bodyId1 The first body ID.
     * @param bodyId2 The second body ID.
     * @return {@code true} if the pair is ignored, {@code false} otherwise.
     */
    public boolean isPairIgnored(int bodyId1, int bodyId2) {
        return nIsPairIgnored(va(), bodyId1, bodyId2);
    }

    /**
     * Returns all currently ignored body pairs.
     * <p>
     * Each pair is returned as a two-element int array: {@code [bodyId1, bodyId2]}.
     * The pairs are normalized such that the smaller ID is always first.
     * </p>
     *
     * @return A list of ignored body pairs.
     */
    public List<int[]> getIgnoredPairs() {
        int[] flattened = nGetIgnoredPairs(va());
        if (flattened == null || flattened.length == 0) {
            return List.of();
        }

        List<int[]> pairs = new ArrayList<>(flattened.length / 2);
        for (int i = 0; i < flattened.length; i += 2) {
            pairs.add(new int[]{flattened[i], flattened[i + 1]});
        }
        return pairs;
    }

    /**
     * Callback to notify the manager that a body has been removed from the physics system.
     * <p>
     * This automatically removes all ignored pairs involving the specified body ID,
     * preventing stale references and potential issues with body ID reuse.
     * </p>
     *
     * @param bodyId The body ID that was removed.
     */
    public void onBodyRemoved(int bodyId) {
        nOnBodyRemoved(va(), bodyId);
    }

    /**
     * Clears all ignored pairs.
     */
    public void clear() {
        nClear(va());
    }

    /**
     * Returns the number of currently ignored pairs.
     *
     * @return The count of ignored pairs.
     */
    public int size() {
        return nSize(va());
    }

    /**
     * Returns whether this manager has any ignored pairs.
     * <p>
     * This is a fast check that can be used to skip contact validation
     * when no pairs are being ignored, avoiding unnecessary overhead.
     * </p>
     *
     * @return {@code true} if there are ignored pairs, {@code false} otherwise.
     */
    public boolean hasIgnoredPairs() {
        return nHasIgnoredPairs(va());
    }

    /**
     * Checks if a body is involved in any ignored pairs.
     * <p>
     * This is a fast O(1) check that can be used to skip contact validation
     * for bodies that are not involved in any ignored pairs at all.
     * </p>
     *
     * @param bodyId The body ID to check.
     * @return {@code true} if the body is involved in ignored pairs, {@code false} otherwise.
     */
    public boolean isBodyInvolved(int bodyId) {
        return nIsBodyInvolved(va(), bodyId);
    }

    /**
     * Optimized single-call check for contact validation.
     * <p>
     * This combines all checks (hasIgnoredPairs, isBodyInvolved, isPairIgnored)
     * into a single native call for maximum performance. This is the preferred
     * method to call from the contact listener.
     * </p>
     *
     * @param bodyId1 The first body ID.
     * @param bodyId2 The second body ID.
     * @return {@code true} if the pair should be ignored, {@code false} otherwise.
     */
    public boolean shouldIgnorePair(int bodyId1, int bodyId2) {
        return nShouldIgnorePair(va(), bodyId1, bodyId2);
    }

    // Native methods

    /**
     * Natively allocates a new C++ {@code BodyPairIgnoreManager}.
     *
     * @return The native virtual address of the newly allocated manager.
     */
    private static native long nCreateManager();

    /**
     * Natively deletes the C++ {@code BodyPairIgnoreManager} and releases its memory.
     *
     * @param address The native virtual address of the manager to destroy.
     */
    private static native void nDestroyManager(long address);

    /**
     * Natively adds a body pair to the ignore set.
     *
     * @param address  The native virtual address of the manager.
     * @param bodyId1  The first Jolt body ID.
     * @param bodyId2  The second Jolt body ID.
     */
    private static native void nIgnorePair(long address, int bodyId1, int bodyId2);

    /**
     * Natively removes a body pair from the ignore set.
     *
     * @param address  The native virtual address of the manager.
     * @param bodyId1  The first Jolt body ID.
     * @param bodyId2  The second Jolt body ID.
     */
    private static native void nRemoveIgnorePair(long address, int bodyId1, int bodyId2);

    /**
     * Natively checks if a pair of bodies is ignored.
     *
     * @param address  The native virtual address of the manager.
     * @param bodyId1  The first Jolt body ID.
     * @param bodyId2  The second Jolt body ID.
     * @return {@code true} if the pair is ignored, {@code false} otherwise.
     */
    private static native boolean nIsPairIgnored(long address, int bodyId1, int bodyId2);

    /**
     * Natively retrieves all ignored pairs as a flattened array.
     *
     * @param address  The native virtual address of the manager.
     * @return A flattened array of body IDs [id1, id2, id3, id4, ...].
     */
    private static native int[] nGetIgnoredPairs(long address);

    /**
     * Natively notifies the manager that a body was removed, cleaning up associated pairs.
     *
     * @param address  The native virtual address of the manager.
     * @param bodyId   The Jolt body ID that was removed.
     */
    private static native void nOnBodyRemoved(long address, int bodyId);

    /**
     * Natively clears all ignored pairs.
     *
     * @param address  The native virtual address of the manager.
     */
    private static native void nClear(long address);

    /**
     * Natively returns the number of ignored pairs.
     *
     * @param address  The native virtual address of the manager.
     * @return The count of ignored pairs.
     */
    private static native int nSize(long address);

    /**
     * Natively checks if the manager contains any ignored pairs.
     *
     * @param address  The native virtual address of the manager.
     * @return {@code true} if there are ignored pairs, {@code false} otherwise.
     */
    private static native boolean nHasIgnoredPairs(long address);

    /**
     * Natively checks if a body is part of any ignored pair.
     *
     * @param address  The native virtual address of the manager.
     * @param bodyId   The Jolt body ID to check.
     * @return {@code true} if involved, {@code false} otherwise.
     */
    private static native boolean nIsBodyInvolved(long address, int bodyId);

    /**
     * Natively performs an optimized check to see if a pair should be ignored.
     *
     * @param address  The native virtual address of the manager.
     * @param bodyId1  The first Jolt body ID.
     * @param bodyId2  The second Jolt body ID.
     * @return {@code true} if the collision should be ignored, {@code false} otherwise.
     */
    private static native boolean nShouldIgnorePair(long address, int bodyId1, int bodyId2);
}