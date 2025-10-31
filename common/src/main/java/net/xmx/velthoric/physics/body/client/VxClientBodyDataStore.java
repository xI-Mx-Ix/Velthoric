/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.client;

import com.github.stephengold.joltjni.RVec3;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.xmx.velthoric.physics.body.AbstractDataStore;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

/**
 * A client-side data store for physics bodies using a Structure of Arrays (SoA) layout.
 * This approach improves cache performance by keeping related data for a specific operation
 * (e.g., all X positions) contiguous in memory. It manages body states required for
 * interpolation and rendering.
 *
 * @author xI-Mx-Ix
 */
public class VxClientBodyDataStore extends AbstractDataStore {
    // The initial capacity of the data arrays.
    private static final int INITIAL_CAPACITY = 256;

    // --- Core Mappings ---
    // Maps a physics body's UUID to its integer index in the data arrays.
    private final Object2IntMap<UUID> uuidToIndex = new Object2IntOpenHashMap<>();
    // Maps a session-specific network ID to its integer index.
    private final Int2IntMap networkIdToIndex = new Int2IntOpenHashMap();
    // Maps an integer index back to the corresponding UUID.
    private final ObjectArrayList<UUID> indexToUuid = new ObjectArrayList<>();
    // A list of recycled indices from removed bodies to be reused, acting as a stack.
    private final IntArrayList freeIndices = new IntArrayList();
    // The number of active bodies currently in the store.
    private int count = 0;
    // The current allocated size of the data arrays.
    private int capacity = 0;

    // --- State Buffers for Interpolation ---
    // State 0: The "from" state for interpolation.
    public long[] state0_timestamp;
    public float[] state0_posX, state0_posY, state0_posZ;
    public float[] state0_rotX, state0_rotY, state0_rotZ, state0_rotW;
    public float[] state0_velX, state0_velY, state0_velZ;
    public boolean[] state0_isActive;
    public float[] @Nullable [] state0_vertexData;

    // State 1: The "to" state for interpolation. This is the most recent state from the server.
    public long[] state1_timestamp;
    public float[] state1_posX, state1_posY, state1_posZ;
    public float[] state1_rotX, state1_rotY, state1_rotZ, state1_rotW;
    public float[] state1_velX, state1_velY, state1_velZ;
    public boolean[] state1_isActive;
    public float[] @Nullable [] state1_vertexData;

    // --- Render State Buffers ---
    // The state from the *previous render frame*, used for frame-to-frame interpolation.
    public float[] prev_posX, prev_posY, prev_posZ;
    public float[] prev_rotX, prev_rotY, prev_rotZ, prev_rotW;
    public float[] @Nullable [] prev_vertexData;

    // The target render state for the *current render frame*, calculated each tick.
    public float[] render_posX, render_posY, render_posZ;
    public float[] render_rotX, render_rotY, render_rotZ, render_rotW;
    public float[] @Nullable [] render_vertexData;
    public boolean[] render_isInitialized;

    // --- Static and Metadata ---
    // Buffer for custom data sent from the server.
    public ByteBuffer[] customData;
    // The last known position of the body, used for frustum culling.
    public RVec3[] lastKnownPosition;

    /**
     * Constructs the data store and allocates initial memory.
     */
    public VxClientBodyDataStore() {
        allocate(INITIAL_CAPACITY);
        uuidToIndex.defaultReturnValue(-1);
        networkIdToIndex.defaultReturnValue(-1);
    }

    /**
     * Reallocates all data arrays to a new capacity.
     *
     * @param newCapacity The new size for the arrays.
     */
    private void allocate(int newCapacity) {
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

        prev_posX = grow(prev_posX, newCapacity);
        prev_posY = grow(prev_posY, newCapacity);
        prev_posZ = grow(prev_posZ, newCapacity);
        prev_rotX = grow(prev_rotX, newCapacity);
        prev_rotY = grow(prev_rotY, newCapacity);
        prev_rotZ = grow(prev_rotZ, newCapacity);
        prev_rotW = grow(prev_rotW, newCapacity);
        prev_vertexData = grow(prev_vertexData, newCapacity);

        render_posX = grow(render_posX, newCapacity);
        render_posY = grow(render_posY, newCapacity);
        render_posZ = grow(render_posZ, newCapacity);
        render_rotX = grow(render_rotX, newCapacity);
        render_rotY = grow(render_rotY, newCapacity);
        render_rotZ = grow(render_rotZ, newCapacity);
        render_rotW = grow(render_rotW, newCapacity);
        render_vertexData = grow(render_vertexData, newCapacity);
        render_isInitialized = grow(render_isInitialized, newCapacity);

        customData = grow(customData, newCapacity);
        lastKnownPosition = grow(lastKnownPosition, newCapacity);

        this.capacity = newCapacity;
    }

    /**
     * Adds a new body to the data store and returns its assigned index.
     *
     * @param id The UUID of the body to add.
     * @param networkId The session-specific network ID of the body.
     * @return The index assigned to the new body.
     */
    public int addBody(UUID id, int networkId) {
        // Grow the arrays if capacity is reached.
        if (count == capacity) {
            allocate(capacity * 2);
        }
        // Reuse an old index if available, otherwise use a new one.
        int index = freeIndices.isEmpty() ? count++ : freeIndices.removeInt(freeIndices.size() - 1);

        uuidToIndex.put(id, index);
        networkIdToIndex.put(networkId, index);

        if (index >= indexToUuid.size()) {
            indexToUuid.add(id);
        } else {
            indexToUuid.set(index, id);
        }
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
            UUID id = indexToUuid.get(index);
            if (id != null) {
                uuidToIndex.removeInt(id);
            }
            resetIndex(index);
            freeIndices.add(index);
            indexToUuid.set(index, null);
        }
    }

    /**
     * Clears all data from the store and resets it to its initial state.
     */
    public void clear() {
        uuidToIndex.clear();
        networkIdToIndex.clear();
        indexToUuid.clear();
        freeIndices.clear();
        count = 0;
        // Reallocate with initial capacity to free up memory.
        allocate(INITIAL_CAPACITY);
    }

    /**
     * Gets the index for a given body UUID.
     *
     * @param id The UUID of the body.
     * @return The integer index, or null if the body is not in the store.
     */
    @Nullable
    public Integer getIndexForId(UUID id) {
        int index = uuidToIndex.getInt(id);
        return index == -1 ? null : index;
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
     * Gets the UUID for a given index.
     *
     * @param index The index of the body.
     * @return The UUID, or null if the index is invalid or free.
     */
    @Nullable
    public UUID getUuidForIndex(int index) {
        if (index >= 0 && index < indexToUuid.size()) {
            return indexToUuid.get(index);
        }
        return null;
    }

    /**
     * @return The total number of active bodies in the store.
     */
    public int getBodyCount() {
        return uuidToIndex.size();
    }

    /**
     * @return The total number of bodies the store can hold before reallocating.
     */
    public int getCapacity() {
        return this.capacity;
    }

    /**
     * @return The number of free indices available for reuse.
     */
    public int getFreeIndicesCount() {
        return this.freeIndices.size();
    }

    /**
     * Calculates the approximate memory usage of all data arrays in the store.
     *
     * @return The estimated memory usage in bytes.
     */
    public long getMemoryUsageBytes() {
        if (capacity == 0) {
            return 0;
        }

        long bytes = 0;

        // Primitive arrays
        bytes += (long) capacity * 8 * 2;  // 2 long[]
        bytes += (long) capacity * 4 * 34; // 34 float[]
        bytes += capacity * 2L;          // 2 boolean[] (estimating 1 byte per boolean)

        // Reference arrays (assuming 8 bytes per reference on a 64-bit JVM)
        bytes += (long) capacity * 8 * 6;

        return bytes;
    }

    /**
     * @return An unmodifiable collection of all active body UUIDs.
     */
    public Collection<UUID> getAllPhysicsIds() {
        return Collections.unmodifiableSet(uuidToIndex.keySet());
    }

    /**
     * Checks if a body with the given UUID exists in the store.
     *
     * @param id The UUID to check.
     * @return True if the body exists, false otherwise.
     */
    public boolean hasBody(UUID id) {
        return uuidToIndex.containsKey(id);
    }

    /**
     * Resets all data at a specific index to default values.
     *
     * @param index The index to reset.
     */
    private void resetIndex(int index) {
        state0_timestamp[index] = 0;
        state1_timestamp[index] = 0;
        render_isInitialized[index] = false;
        state0_isActive[index] = false;
        state1_isActive[index] = false;
        state0_vertexData[index] = null;
        state1_vertexData[index] = null;
        prev_vertexData[index] = null;
        render_vertexData[index] = null;
        customData[index] = null;
        if (lastKnownPosition != null && lastKnownPosition[index] != null) {
            lastKnownPosition[index].loadZero();
        }
        state0_velX[index] = state0_velY[index] = state0_velZ[index] = 0;
        state1_velX[index] = state1_velY[index] = state1_velZ[index] = 0;
    }
}