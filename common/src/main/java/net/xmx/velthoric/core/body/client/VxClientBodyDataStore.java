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
     * Structural double-buffered container for thread-safe resizing.
     */
    protected volatile VxClientBodyDataContainer clientCurrentContainer;

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
     *
     * @param index The index to reset.
     */
    @Override
    protected void resetIndex(int index) {
        super.resetIndex(index);
        VxClientBodyDataContainer c = clientCurrentContainer;

        c.state0_timestamp[index] = 0;
        c.state1_timestamp[index] = 0;

        c.state0_isActive[index] = false;
        c.state1_isActive[index] = false;

        c.state0_vertexData[index] = null;
        c.state1_vertexData[index] = null;

        c.render_isInitialized[index] = false;
        c.prev_vertexData[index] = null;

        c.customData[index] = null;
        if (c.lastKnownPosition != null && c.lastKnownPosition[index] != null) {
            c.lastKnownPosition[index].loadZero();
        }

        // Reset buffer vectors
        c.state0_velX[index] = c.state0_velY[index] = c.state0_velZ[index] = 0;
        c.state1_velX[index] = c.state1_velY[index] = c.state1_velZ[index] = 0;

        c.state0_posX[index] = c.state0_posY[index] = c.state0_posZ[index] = 0.0;
        c.state1_posX[index] = c.state1_posY[index] = c.state1_posZ[index] = 0.0;
        c.prev_posX[index] = c.prev_posY[index] = c.prev_posZ[index] = 0.0;
    }

    /**
     * Reallocates all data arrays to a new capacity.
     *
     * @param newCapacity The new size for the arrays.
     */
    @Override
    protected void allocate(int newCapacity) {
        super.growBaseArrays(newCapacity);

        VxClientBodyDataContainer old = clientCurrentContainer;
        VxClientBodyDataContainer next = (VxClientBodyDataContainer) currentContainer;

        if (old != null) {
            int copyLength = Math.min(old.capacity, newCapacity);
            System.arraycopy(old.state0_timestamp, 0, next.state0_timestamp, 0, copyLength);
            System.arraycopy(old.state0_posX, 0, next.state0_posX, 0, copyLength);
            System.arraycopy(old.state0_posY, 0, next.state0_posY, 0, copyLength);
            System.arraycopy(old.state0_posZ, 0, next.state0_posZ, 0, copyLength);
            System.arraycopy(old.state0_rotX, 0, next.state0_rotX, 0, copyLength);
            System.arraycopy(old.state0_rotY, 0, next.state0_rotY, 0, copyLength);
            System.arraycopy(old.state0_rotZ, 0, next.state0_rotZ, 0, copyLength);
            System.arraycopy(old.state0_rotW, 0, next.state0_rotW, 0, copyLength);
            System.arraycopy(old.state0_velX, 0, next.state0_velX, 0, copyLength);
            System.arraycopy(old.state0_velY, 0, next.state0_velY, 0, copyLength);
            System.arraycopy(old.state0_velZ, 0, next.state0_velZ, 0, copyLength);
            System.arraycopy(old.state0_isActive, 0, next.state0_isActive, 0, copyLength);
            System.arraycopy(old.state0_vertexData, 0, next.state0_vertexData, 0, copyLength);

            System.arraycopy(old.state1_timestamp, 0, next.state1_timestamp, 0, copyLength);
            System.arraycopy(old.state1_posX, 0, next.state1_posX, 0, copyLength);
            System.arraycopy(old.state1_posY, 0, next.state1_posY, 0, copyLength);
            System.arraycopy(old.state1_posZ, 0, next.state1_posZ, 0, copyLength);
            System.arraycopy(old.state1_rotX, 0, next.state1_rotX, 0, copyLength);
            System.arraycopy(old.state1_rotY, 0, next.state1_rotY, 0, copyLength);
            System.arraycopy(old.state1_rotZ, 0, next.state1_rotZ, 0, copyLength);
            System.arraycopy(old.state1_rotW, 0, next.state1_rotW, 0, copyLength);
            System.arraycopy(old.state1_velX, 0, next.state1_velX, 0, copyLength);
            System.arraycopy(old.state1_velY, 0, next.state1_velY, 0, copyLength);
            System.arraycopy(old.state1_velZ, 0, next.state1_velZ, 0, copyLength);
            System.arraycopy(old.state1_isActive, 0, next.state1_isActive, 0, copyLength);
            System.arraycopy(old.state1_vertexData, 0, next.state1_vertexData, 0, copyLength);

            System.arraycopy(old.prev_posX, 0, next.prev_posX, 0, copyLength);
            System.arraycopy(old.prev_posY, 0, next.prev_posY, 0, copyLength);
            System.arraycopy(old.prev_posZ, 0, next.prev_posZ, 0, copyLength);
            System.arraycopy(old.prev_rotX, 0, next.prev_rotX, 0, copyLength);
            System.arraycopy(old.prev_rotY, 0, next.prev_rotY, 0, copyLength);
            System.arraycopy(old.prev_rotZ, 0, next.prev_rotZ, 0, copyLength);
            System.arraycopy(old.prev_rotW, 0, next.prev_rotW, 0, copyLength);
            System.arraycopy(old.prev_vertexData, 0, next.prev_vertexData, 0, copyLength);

            System.arraycopy(old.render_isInitialized, 0, next.render_isInitialized, 0, copyLength);
            System.arraycopy(old.customData, 0, next.customData, 0, copyLength);
            // lastKnownPosition is already initialized in new container constructor
            for (int i = 0; i < copyLength; i++) {
                next.lastKnownPosition[i].set(old.lastKnownPosition[i]);
            }
        }

        this.clientCurrentContainer = next;
    }

    @Override
    protected VxBodyDataContainer createContainer(int newCapacity) {
        return new VxClientBodyDataContainer(newCapacity);
    }

    public VxClientBodyDataContainer clientCurrent() {
        return clientCurrentContainer;
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