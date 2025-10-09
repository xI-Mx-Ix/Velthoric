/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.data;

import com.github.stephengold.joltjni.ShapeRefC;
import net.xmx.velthoric.physics.object.AbstractDataStore;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A data-oriented store for the state of all terrain chunk physics bodies.
 * <p>
 * This class uses a "Structure of Arrays" (SoA) layout for cache efficiency.
 * It is thread-safe, using a ConcurrentHashMap for lookups from a packed long
 * position to an integer index. A ReentrantLock protects the underlying arrays
 * during resizing or structural modifications, providing better concurrency
 * than synchronizing every method.
 *
 * @author xI-Mx-Ix
 */
public class VxChunkDataStore extends AbstractDataStore {
    private static final int INITIAL_CAPACITY = 4096;
    public static final int UNUSED_BODY_ID = 0;

    // --- Chunk States ---
    public static final int STATE_UNLOADED = 0;
    public static final int STATE_LOADING_SCHEDULED = 1;
    public static final int STATE_GENERATING_SHAPE = 2;
    public static final int STATE_READY_INACTIVE = 3;
    public static final int STATE_READY_ACTIVE = 4;
    public static final int STATE_REMOVING = 5;
    public static final int STATE_AIR_CHUNK = 6;

    private final Map<Long, Integer> posToIndex = new ConcurrentHashMap<>();
    private final List<Long> indexToPos = new ArrayList<>();
    private final Deque<Integer> freeIndices = new ArrayDeque<>();
    private final Lock lock = new ReentrantLock();
    private int count = 0;
    private int capacity = 0;

    // --- Chunk State Data (SoA) ---
    public volatile int[] states;
    public volatile int[] bodyIds;
    public volatile ShapeRefC[] shapeRefs;
    public volatile int[] rebuildVersions;
    public volatile int[] referenceCounts;

    public VxChunkDataStore() {
        allocate(INITIAL_CAPACITY);
    }

    private void allocate(int newCapacity) {
        lock.lock();
        try {
            states = grow(states, newCapacity);
            bodyIds = grow(bodyIds, newCapacity);
            shapeRefs = grow(shapeRefs, newCapacity);
            rebuildVersions = grow(rebuildVersions, newCapacity);
            referenceCounts = grow(referenceCounts, newCapacity);
            this.capacity = newCapacity;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reserves a new index for a terrain chunk or retrieves the existing one.
     *
     * @param packedPos The packed long representing the chunk section's position.
     * @return The data store index for the chunk.
     */
    public int addChunk(long packedPos) {
        Integer existingIndex = posToIndex.get(packedPos);
        if (existingIndex != null) {
            return existingIndex;
        }

        lock.lock();
        try {
            existingIndex = posToIndex.get(packedPos);
            if (existingIndex != null) {
                return existingIndex;
            }

            if (count == capacity) {
                allocate(capacity + (capacity >> 1)); // Grow by 50%
            }
            int index = freeIndices.isEmpty() ? count++ : freeIndices.pop();

            posToIndex.put(packedPos, index);
            if (index >= indexToPos.size()) {
                indexToPos.add(packedPos);
            } else {
                indexToPos.set(index, packedPos);
            }

            resetIndex(index);
            return index;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Releases the index for a given chunk position, making it available for reuse.
     *
     * @param packedPos The packed long of the chunk to remove.
     * @return The released index, or null if the chunk was not found.
     */
    @Nullable
    public Integer removeChunk(long packedPos) {
        Integer index = posToIndex.remove(packedPos);
        if (index != null) {
            lock.lock();
            try {
                if (shapeRefs[index] != null) {
                    shapeRefs[index].close();
                    shapeRefs[index] = null;
                }
                freeIndices.push(index);
                indexToPos.set(index, 0L); // Clear the position
            } finally {
                lock.unlock();
            }
            return index;
        }
        return null;
    }

    /**
     * Clears all data and resets the store to its initial state.
     */
    public void clear() {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    /**
     * Safely sets the shape for a given index, ensuring the previous shape is closed.
     *
     * @param index The data store index.
     * @param shape The new shape reference.
     */
    public void setShape(int index, ShapeRefC shape) {
        lock.lock();
        try {
            if (shapeRefs[index] != null && shapeRefs[index] != shape) {
                shapeRefs[index].close();
            }
            shapeRefs[index] = shape;
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    public Integer getIndexForPos(long packedPos) {
        return posToIndex.get(packedPos);
    }

    /**
     * Gets the packed long position for a given index.
     *
     * @param index The data store index.
     * @return The packed long position, or 0 if the index is invalid.
     */
    public long getPosForIndex(int index) {
        lock.lock();
        try {
            if (index < 0 || index >= indexToPos.size()) {
                return 0L;
            }
            return indexToPos.get(index);
        } finally {
            lock.unlock();
        }
    }

    public Set<Long> getManagedPositions() {
        return new HashSet<>(posToIndex.keySet());
    }

    public Collection<Integer> getActiveIndices() {
        return new ArrayList<>(posToIndex.values());
    }

    private void resetIndex(int index) {
        states[index] = STATE_UNLOADED;
        bodyIds[index] = UNUSED_BODY_ID;
        shapeRefs[index] = null;
        rebuildVersions[index] = 0;
        referenceCounts[index] = 0;
    }
}