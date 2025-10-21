/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.storage;

import com.github.stephengold.joltjni.ShapeRefC;
import net.xmx.velthoric.physics.body.AbstractDataStore;
import net.xmx.velthoric.physics.terrain.VxSectionPos;
import net.xmx.velthoric.physics.terrain.management.VxTerrainManager;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A data-oriented and thread-safe store for the state of all terrain chunk physics bodies.
 * <p>
 * This class uses a "Structure of Arrays" (SoA) layout, where each property of a chunk
 * is stored in a separate array. It manages the mapping from a {@link VxSectionPos} to a
 * stable integer index, allowing for fast lookups. All state-mutating operations and accesses
 * are synchronized to prevent race conditions between the terrain worker threads and the main thread.
 *
 * @author xI-Mx-Ix
 */
public final class VxChunkDataStore extends AbstractDataStore {
    private static final int INITIAL_CAPACITY = 4096;
    public static final int UNUSED_BODY_ID = 0;

    private final Map<VxSectionPos, Integer> posToIndex = new HashMap<>();
    private final List<VxSectionPos> indexToPos = new ArrayList<>();
    private final Deque<Integer> freeIndices = new ArrayDeque<>();
    private int count = 0;
    private int capacity = 0;

    // --- Chunk State Data (SoA) ---
    // All state arrays are private to enforce synchronized access via methods.
    private int[] states;
    private int[] bodyIds;
    private ShapeRefC[] shapeRefs;
    private boolean[] isPlaceholder;
    private int[] rebuildVersions;
    private int[] referenceCounts;

    public VxChunkDataStore() {
        allocate(INITIAL_CAPACITY);
    }

    private void allocate(int newCapacity) {
        // This method is only called from synchronized contexts, so it's safe.
        states = grow(states, newCapacity);
        bodyIds = grow(bodyIds, newCapacity);
        shapeRefs = grow(shapeRefs, newCapacity);
        isPlaceholder = grow(isPlaceholder, newCapacity);
        rebuildVersions = grow(rebuildVersions, newCapacity);
        referenceCounts = grow(referenceCounts, newCapacity);

        Arrays.fill(isPlaceholder, count, newCapacity, true);
        this.capacity = newCapacity;
    }

    /**
     * Reserves a new index for a terrain chunk or retrieves an existing one.
     * This operation is fully synchronized.
     *
     * @param pos The world-space position of the chunk section.
     * @return The data store index for the chunk.
     */
    public synchronized int addChunk(VxSectionPos pos) {
        Integer existingIndex = posToIndex.get(pos);
        if (existingIndex != null) {
            return existingIndex;
        }

        if (count == capacity) {
            allocate(capacity + (capacity >> 1)); // Grow by 50%
        }
        int index = freeIndices.isEmpty() ? count++ : freeIndices.pop();

        posToIndex.put(pos, index);
        if (index >= indexToPos.size()) {
            indexToPos.add(pos);
        } else {
            indexToPos.set(index, pos);
        }

        resetIndex(index);
        return index;
    }

    /**
     * Releases the index for a given chunk position, making it available for reuse.
     * This also handles the cleanup of associated resources like {@link ShapeRefC}.
     * This operation is fully synchronized.
     *
     * @param pos The position of the chunk to remove.
     * @return The released index, or null if the chunk was not found.
     */
    @Nullable
    public synchronized Integer removeChunk(VxSectionPos pos) {
        Integer index = posToIndex.remove(pos);
        if (index != null) {
            if (shapeRefs[index] != null) {
                shapeRefs[index].close();
                shapeRefs[index] = null;
            }
            freeIndices.push(index);
            indexToPos.set(index, null);
            return index;
        }
        return null;
    }

    /**
     * Clears all data and resets the store to its initial state.
     * All held shape references are properly closed.
     * This operation is fully synchronized.
     */
    public synchronized void clear() {
        for (int i = 0; i < count; i++) {
            if (shapeRefs[i] != null) {
                shapeRefs[i].close();
            }
        }
        posToIndex.clear();
        indexToPos.clear();
        freeIndices.clear();
        count = 0;
        allocate(INITIAL_CAPACITY);
    }

    // --- Thread-Safe Accessors and Atomic Operations ---

    /**
     * Safely gets the current state of a chunk.
     *
     * @param index The data store index.
     * @return The current state.
     */
    public synchronized int getState(int index) {
        return states[index];
    }

    /**
     * Safely sets the state for a given index.
     *
     * @param index The data store index.
     * @param state The new state.
     */
    public synchronized void setState(int index, int state) {
        this.states[index] = state;
    }

    /**
     * Safely gets the body ID for a given index.
     *
     * @param index The data store index.
     * @return The physics body ID.
     */
    public synchronized int getBodyId(int index) {
        return bodyIds[index];
    }

    /**
     * Safely sets the body ID for a given index.
     *
     * @param index The data store index.
     * @param bodyId The new physics body ID.
     */
    public synchronized void setBodyId(int index, int bodyId) {
        this.bodyIds[index] = bodyId;
    }

    /**
     * Safely checks if a chunk is currently represented by a placeholder shape.
     *
     * @param index The data store index.
     * @return True if the chunk is a placeholder.
     */
    public synchronized boolean isPlaceholder(int index) {
        return isPlaceholder[index];
    }

    /**
     * Safely sets the placeholder status for a chunk.
     *
     * @param index The data store index.
     * @param placeholder The new placeholder status.
     */
    public synchronized void setPlaceholder(int index, boolean placeholder) {
        this.isPlaceholder[index] = placeholder;
    }

    /**
     * Atomically increments the reference count for a chunk and returns the new value.
     *
     * @param index The data store index.
     * @return The reference count after incrementing.
     */
    public synchronized int incrementAndGetRefCount(int index) {
        return ++this.referenceCounts[index];
    }

    /**
     * Atomically decrements the reference count for a chunk and returns the new value.
     *
     * @param index The data store index.
     * @return The reference count after decrementing.
     */
    public synchronized int decrementAndGetRefCount(int index) {
        return --this.referenceCounts[index];
    }

    /**
     * Checks if a generation task with a given version is stale.
     *
     * @param index The data store index.
     * @param version The version of the task to check.
     * @return True if the task's version is older than the current version, false otherwise.
     */
    public synchronized boolean isVersionStale(int index, int version) {
        return version < rebuildVersions[index];
    }

    /**
     * Atomically attempts to mark a chunk for shape generation.
     * <p>
     * This method checks if the chunk is in a state that allows it to be rebuilt. If so,
     * it transitions the state, increments the chunk's internal version number, and returns the
     * new version. This ensures that only one generation task can be scheduled at a time.
     *
     * @param index The index of the chunk to schedule.
     * @return The new, unique version number for the generation task if successful, or -1 if the
     *         chunk cannot be scheduled (e.g., it is already being processed).
     */
    public synchronized int scheduleForGeneration(int index) {
        if (states[index] == VxTerrainManager.STATE_REMOVING ||
                states[index] == VxTerrainManager.STATE_LOADING_SCHEDULED ||
                states[index] == VxTerrainManager.STATE_GENERATING_SHAPE) {
            return -1; // Indicate that the operation failed because the chunk is busy.
        }
        states[index] = VxTerrainManager.STATE_LOADING_SCHEDULED;
        return ++rebuildVersions[index];
    }

    /**
     * Safely sets the shape for a given index, ensuring the previous shape is closed.
     *
     * @param index The data store index.
     * @param shape The new shape reference.
     */
    public synchronized void setShape(int index, ShapeRefC shape) {
        if (shapeRefs[index] != null && shapeRefs[index] != shape) {
            shapeRefs[index].close();
        }
        shapeRefs[index] = shape;
    }

    /**
     * Gets the data store index for a given chunk position.
     *
     * @param pos The position of the chunk.
     * @return The index, or null if not found.
     */
    @Nullable
    public synchronized Integer getIndexForPos(VxSectionPos pos) {
        return posToIndex.get(pos);
    }

    /**
     * Gets the chunk position for a given data store index.
     *
     * @param index The index.
     * @return The position, or null if the index is invalid or unused.
     */
    @Nullable
    public synchronized VxSectionPos getPosForIndex(int index) {
        if (index < 0 || index >= indexToPos.size()) {
            return null;
        }
        return indexToPos.get(index);
    }

    /**
     * Returns a thread-safe copy of the currently managed positions.
     *
     * @return A new Set containing all managed positions.
     */
    public synchronized Set<VxSectionPos> getManagedPositions() {
        return new HashSet<>(posToIndex.keySet());
    }

    /**
     * Returns a thread-safe copy of the indices for all currently managed chunks.
     *
     * @return A new List containing all active indices.
     */
    public synchronized Collection<Integer> getActiveIndices() {
        return new ArrayList<>(posToIndex.values());
    }

    /**
     * Returns a copy of all active body IDs. This is safe to iterate over.
     *
     * @return A new array containing all body IDs.
     */
    public synchronized int[] getBodyIds() {
        return Arrays.copyOf(bodyIds, count);
    }

    /**
     * Gets the number of actively managed chunks.
     * @return The total chunk count.
     */
    public synchronized int getChunkCount() {
        return this.count - freeIndices.size();
    }

    /**
     * Gets the current capacity of the internal arrays.
     * @return The capacity.
     */
    public synchronized int getCapacity() {
        return this.capacity;
    }

    private void resetIndex(int index) {
        states[index] = VxTerrainManager.STATE_UNLOADED;
        bodyIds[index] = UNUSED_BODY_ID;
        shapeRefs[index] = null;
        isPlaceholder[index] = true;
        rebuildVersions[index] = 0;
        referenceCounts[index] = 0;
    }
}