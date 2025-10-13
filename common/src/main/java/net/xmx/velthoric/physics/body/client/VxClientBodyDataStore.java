/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.client;

import com.github.stephengold.joltjni.RVec3;
import net.xmx.velthoric.physics.body.AbstractDataStore;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.*;

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
    private final Map<UUID, Integer> uuidToIndex = new HashMap<>();
    // Maps an integer index back to the corresponding UUID.
    private final List<UUID> indexToUuid = new ArrayList<>();
    // A queue of recycled indices from removed bodies to be reused.
    private final Deque<Integer> freeIndices = new ArrayDeque<>();
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
     * @return The index assigned to the new body.
     */
    public int addBody(UUID id) {
        // Grow the arrays if capacity is reached.
        if (count == capacity) {
            allocate(capacity * 2);
        }
        // Reuse an old index if available, otherwise use a new one.
        int index = freeIndices.isEmpty() ? count++ : freeIndices.pop();

        uuidToIndex.put(id, index);
        if (index >= indexToUuid.size()) {
            indexToUuid.add(id);
        } else {
            indexToUuid.set(index, id);
        }
        return index;
    }

    /**
     * Removes a body from the data store, freeing its index for reuse.
     *
     * @param id The UUID of the body to remove.
     */
    public void removeBody(UUID id) {
        Integer index = uuidToIndex.remove(id);
        if (index != null) {
            resetIndex(index);
            freeIndices.push(index);
            indexToUuid.set(index, null);
        }
    }

    /**
     * Clears all data from the store and resets it to its initial state.
     */
    public void clear() {
        uuidToIndex.clear();
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
        return uuidToIndex.get(id);
    }

    /**
     * @return The total number of bodies in the store.
     */
    public int getBodyCount() {
        return this.count;
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