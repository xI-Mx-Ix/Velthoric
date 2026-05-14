/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.client;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.body.VxBodyDataContainer;
import net.xmx.velthoric.core.body.VxBodyDataStore;

import java.util.UUID;

/**
 * A client-side data store for physics bodies.
 * <p>
 * In this implementation, the shared arrays inherited from {@link VxBodyDataStore} ({@code posX}, {@code rotX}, etc.)
 * store the <b>Interpolated Render State</b>. This is the visual result displayed to the player.
 * <p>
 * This class maintains separate "history buffers" (State 0, State 1) to perform the interpolation
 * between server updates:
 * <ul>
 *     <li><b>State 1:</b> The most recent state received from the server.</li>
 *     <li><b>State 0:</b> The state prior to State 1.</li>
 *     <li><b>Prev:</b> The render state from the previous frame (for frame-time interpolation).</li>
 * </ul>
 *
 * @author xI-Mx-Ix
 */
public class VxClientBodyDataStore extends VxBodyDataStore {

    // --- Core Client Mappings ---

    /**
     * Maps a session-specific network ID to its integer index.
     */
    private final Int2IntMap networkIdToIndex = new Int2IntOpenHashMap();

    /**
     * Constructs the client data store.
     */
    public VxClientBodyDataStore() {
        super();
        allocate(INITIAL_CAPACITY);
        networkIdToIndex.defaultReturnValue(-1);
    }

    /**
     * Adds a new body to the client store with a specific Network ID.
     *
     * @param body      The body object to add.
     * @param networkId The session-specific network ID.
     * @return The assigned index.
     */
    public int addBody(VxBody body, int networkId) {
        int index = super.reserveIndex(body);
        networkIdToIndex.put(networkId, index);
        return index;
    }

    /**
     * Removes a body from the data store using its network ID.
     *
     * @param networkId The network ID of the body to remove.
     */
    public void removeBodyByNetworkId(int networkId) {
        int index = networkIdToIndex.remove(networkId);
        if (index != -1) {
            UUID id = getIdForIndex(index);
            if (id != null) {
                // This calls super.removeBody -> which calls this.resetIndex(index)
                super.removeBody(id);
            }
        }
    }

    /**
     * Resets all data at a specific index to default values.
     * <p>
     * Delegates to {@link VxClientBodyDataContainer#reset(int)}.
     *
     * @param index The index to reset.
     */
    @Override
    protected void resetIndex(int index) {
        super.resetIndex(index);
    }

    /**
     * Reallocates all data arrays to a new capacity.
     * <p>
     * Triggers base reallocation logic which handles the atomical swap
     * of the underlying {@link VxClientBodyDataContainer}.
     *
     * @param newCapacity The new size for the arrays.
     */
    @Override
    protected void allocate(int newCapacity) {
        super.growBaseArrays(newCapacity);
    }

    @Override
    protected VxBodyDataContainer createContainer(int newCapacity) {
        return new VxClientBodyDataContainer(newCapacity);
    }

    /**
     * Returns the currently active client-side data container.
     *
     * @return The typed container instance.
     */
    public VxClientBodyDataContainer clientCurrent() {
        return (VxClientBodyDataContainer) currentContainer;
    }

    /**
     * Gets the index for a given body network ID.
     *
     * @param networkId The network ID of the body.
     * @return The integer index, or null if the body is not in the store.
     */
    public Integer getIndexForNetworkId(int networkId) {
        int index = networkIdToIndex.get(networkId);
        return index == -1 ? null : index;
    }

    /**
     * Calculates the approximate memory usage of all data arrays in the store.
     * <p>
     * Sums the sizes of all base arrays and client-specific interpolation buffers.
     *
     * @return The estimated memory usage in bytes.
     */
    public long getMemoryUsageBytes() {
        if (capacity == 0) return 0;

        long bytes = 0;

        // Base Arrays (from super)
        bytes += (long) capacity * 8 * 3; // pos
        bytes += (long) capacity * 4 * 7; // rot, vel
        bytes += capacity;                // boolean

        // Client Buffers
        bytes += (long) capacity * 8 * 2;  // timestamps
        bytes += (long) capacity * 8 * 9;  // double[] positions (state0, state1, prev)
        bytes += (long) capacity * 4 * 25; // float[] (rotations, velocities, etc)
        bytes += capacity * 3L;            // boolean[]

        return bytes;
    }

    /**
     * Clears all data from the store and resets it to its initial state.
     */
    @Override
    public void clear() {
        networkIdToIndex.clear();
        super.clear();
    }
}