/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.client;

import com.github.stephengold.joltjni.RVec3;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.xmx.velthoric.physics.body.VxBodyDataStore;
import net.xmx.velthoric.physics.body.type.VxBody;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
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

    // --- State 0 Buffers (Previous Server State) ---

    /**
     * Timestamp of the 'from' state.
     */
    public long[] state0_timestamp;
    public double[] state0_posX, state0_posY, state0_posZ;
    public float[] state0_rotX, state0_rotY, state0_rotZ, state0_rotW;
    public float[] state0_velX, state0_velY, state0_velZ;
    public boolean[] state0_isActive;
    public float[] @Nullable [] state0_vertexData;

    // --- State 1 Buffers (Latest Server State) ---

    /**
     * Timestamp of the 'to' state.
     */
    public long[] state1_timestamp;
    public double[] state1_posX, state1_posY, state1_posZ;
    public float[] state1_rotX, state1_rotY, state1_rotZ, state1_rotW;
    public float[] state1_velX, state1_velY, state1_velZ;
    public boolean[] state1_isActive;
    public float[] @Nullable [] state1_vertexData;

    // --- Prev Frame Buffers (Frame Interpolation) ---

    // The render state from the *previous frame*. Used to interpolate with the current
    // target (this.posX, etc.) based on partial ticks.
    public double[] prev_posX, prev_posY, prev_posZ;
    public float[] prev_rotX, prev_rotY, prev_rotZ, prev_rotW;
    public float[] @Nullable [] prev_vertexData;

    /**
     * Flag indicating if the render state has been populated at least once.
     */
    public boolean[] render_isInitialized;

    // --- Metadata ---

    /**
     * Buffer for custom data sent from the server.
     */
    public ByteBuffer[] customData;

    /**
     * The last known position of the body, used for frustum culling.
     */
    public RVec3[] lastKnownPosition;

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

        state0_timestamp[index] = 0;
        state1_timestamp[index] = 0;

        state0_isActive[index] = false;
        state1_isActive[index] = false;

        state0_vertexData[index] = null;
        state1_vertexData[index] = null;

        render_isInitialized[index] = false;
        prev_vertexData[index] = null;

        customData[index] = null;
        if (lastKnownPosition != null && lastKnownPosition[index] != null) {
            lastKnownPosition[index].loadZero();
        }

        // Reset buffer vectors
        state0_velX[index] = state0_velY[index] = state0_velZ[index] = 0;
        state1_velX[index] = state1_velY[index] = state1_velZ[index] = 0;

        state0_posX[index] = state0_posY[index] = state0_posZ[index] = 0.0;
        state1_posX[index] = state1_posY[index] = state1_posZ[index] = 0.0;
        prev_posX[index] = prev_posY[index] = prev_posZ[index] = 0.0;
    }

    /**
     * Reallocates all data arrays to a new capacity.
     *
     * @param newCapacity The new size for the arrays.
     */
    @Override
    protected void allocate(int newCapacity) {
        super.growBaseArrays(newCapacity);

        // State 0
        state0_timestamp = grow(state0_timestamp, newCapacity);
        state0_posX = grow(state0_posX, newCapacity);
        state0_posY = grow(state0_posY, newCapacity);
        state0_posZ = grow(state0_posZ, newCapacity);
        state0_rotX = grow(state0_rotX, newCapacity);
        state0_rotY = grow(state0_rotY, newCapacity);
        state0_rotZ = grow(state0_rotZ, newCapacity);
        state0_rotW = grow(state0_rotW, newCapacity);
        state0_velX = grow(state0_velX, newCapacity);
        state0_velY = grow(state0_velY, newCapacity);
        state0_velZ = grow(state0_velZ, newCapacity);
        state0_isActive = grow(state0_isActive, newCapacity);
        state0_vertexData = grow(state0_vertexData, newCapacity);

        // State 1
        state1_timestamp = grow(state1_timestamp, newCapacity);
        state1_posX = grow(state1_posX, newCapacity);
        state1_posY = grow(state1_posY, newCapacity);
        state1_posZ = grow(state1_posZ, newCapacity);
        state1_rotX = grow(state1_rotX, newCapacity);
        state1_rotY = grow(state1_rotY, newCapacity);
        state1_rotZ = grow(state1_rotZ, newCapacity);
        state1_rotW = grow(state1_rotW, newCapacity);
        state1_velX = grow(state1_velX, newCapacity);
        state1_velY = grow(state1_velY, newCapacity);
        state1_velZ = grow(state1_velZ, newCapacity);
        state1_isActive = grow(state1_isActive, newCapacity);
        state1_vertexData = grow(state1_vertexData, newCapacity);

        // Prev (Inter-frame history)
        prev_posX = grow(prev_posX, newCapacity);
        prev_posY = grow(prev_posY, newCapacity);
        prev_posZ = grow(prev_posZ, newCapacity);
        prev_rotX = grow(prev_rotX, newCapacity);
        prev_rotY = grow(prev_rotY, newCapacity);
        prev_rotZ = grow(prev_rotZ, newCapacity);
        prev_rotW = grow(prev_rotW, newCapacity);
        prev_vertexData = grow(prev_vertexData, newCapacity);

        // Metadata
        render_isInitialized = grow(render_isInitialized, newCapacity);
        customData = grow(customData, newCapacity);
        lastKnownPosition = grow(lastKnownPosition, newCapacity);
    }

    /**
     * Gets the index for a given body network ID.
     *
     * @param networkId The network ID of the body.
     * @return The integer index, or null if the body is not in the store.
     */
    @Nullable
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