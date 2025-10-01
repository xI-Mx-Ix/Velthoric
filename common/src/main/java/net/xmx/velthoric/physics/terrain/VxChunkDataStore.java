/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain;

import com.github.stephengold.joltjni.ShapeRefC;
import net.xmx.velthoric.physics.object.AbstractDataStore;
import net.xmx.velthoric.physics.terrain.chunk.VxSectionPos;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A data-oriented store for the state of all terrain chunk physics bodies.
 * <p>
 * This class uses a "Structure of Arrays" (SoA) layout, where each property of a chunk
 * is stored in a separate array. This is highly efficient for cache locality when
 * iterating over chunk data. It manages the mapping from a {@link VxSectionPos} to a
 * stable integer index, allowing for fast lookups and data manipulation. This class is
 * thread-safe.
 *
 * @author xI-Mx-Ix
 */
public class VxChunkDataStore extends AbstractDataStore {
    private static final int INITIAL_CAPACITY = 4096;
    public static final int UNUSED_BODY_ID = 0;

    private final Map<VxSectionPos, Integer> posToIndex = new HashMap<>();
    private final List<VxSectionPos> indexToPos = new ArrayList<>();
    private final Deque<Integer> freeIndices = new ArrayDeque<>();
    private int count = 0;
    private int capacity = 0;

    // --- Chunk State Data (SoA) ---
    public int[] states;
    public int[] bodyIds;
    public ShapeRefC[] shapeRefs;
    public boolean[] isPlaceholder;
    public int[] rebuildVersions;
    public int[] referenceCounts;

    public VxChunkDataStore() {
        allocate(INITIAL_CAPACITY);
    }

    private void allocate(int newCapacity) {
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
     * Reserves a new index for a terrain chunk or retrieves the existing one.
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

    /**
     * Safely sets the shape for a given index, ensuring the previous shape is closed.
     *
     * @param index The data store index.
     * @param shape The new shape reference.
     */
    public void setShape(int index, ShapeRefC shape) {
        if (shapeRefs[index] != null && shapeRefs[index] != shape) {
            shapeRefs[index].close();
        }
        shapeRefs[index] = shape;
    }

    @Nullable
    public synchronized Integer getIndexForPos(VxSectionPos pos) {
        return posToIndex.get(pos);
    }

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

    public synchronized int getChunkCount() {
        return this.count - freeIndices.size();
    }

    public int getCapacity() {
        return this.capacity;
    }

    private void resetIndex(int index) {
        states[index] = 0; // STATE_UNLOADED
        bodyIds[index] = UNUSED_BODY_ID;
        shapeRefs[index] = null;
        isPlaceholder[index] = true;
        rebuildVersions[index] = 0;
        referenceCounts[index] = 0;
    }
}