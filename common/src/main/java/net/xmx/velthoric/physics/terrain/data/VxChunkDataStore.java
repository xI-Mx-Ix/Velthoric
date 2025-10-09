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
 * A data-oriented, thread-safe store for the state of all terrain chunk physics bodies.
 * It uses a Structure of Arrays (SoA) layout and manages the lifecycle of chunks
 * via a life counter, inspired by proactive terrain management systems. All access
 * to chunk data is synchronized to prevent race conditions.
 *
 * @author xI-Mx-Ix
 */
public class VxChunkDataStore extends AbstractDataStore {
    private static final int INITIAL_CAPACITY = 4096;
    public static final int UNUSED_BODY_ID = 0;

    // --- Chunk States ---
    public static final int STATE_UNLOADED = 0;
    public static final int STATE_AWAITING_CHUNK = 1; // The system is waiting for the Minecraft chunk to be loaded.
    public static final int STATE_LOADING_SCHEDULED = 2;
    public static final int STATE_GENERATING_SHAPE = 3;
    public static final int STATE_READY_INACTIVE = 4;
    public static final int STATE_READY_ACTIVE = 5;
    public static final int STATE_REMOVING = 6;
    public static final int STATE_AIR_CHUNK = 7;

    private final Map<Long, Integer> posToIndex = new ConcurrentHashMap<>();
    private final List<Long> indexToPos = new ArrayList<>();
    private final Deque<Integer> freeIndices = new ArrayDeque<>();
    private final Lock lock = new ReentrantLock();
    private int count = 0;
    private int capacity = 0;

    // --- Chunk State Data (SoA) - Access via synchronized methods ---
    private volatile int[] states;
    private volatile int[] bodyIds;
    private volatile ShapeRefC[] shapeRefs;
    private volatile int[] rebuildVersions;
    private volatile int[] lifeCounters; // TTL for automatic cleanup

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
            lifeCounters = grow(lifeCounters, newCapacity);
            this.capacity = newCapacity;
        } finally {
            lock.unlock();
        }
    }

    public int addChunk(long packedPos) {
        // First, check without lock for performance.
        Integer existingIndex = posToIndex.get(packedPos);
        if (existingIndex != null) {
            return existingIndex;
        }

        lock.lock();
        try {
            // Double-check after acquiring the lock.
            existingIndex = posToIndex.get(packedPos);
            if (existingIndex != null) {
                return existingIndex;
            }

            if (count == capacity) {
                allocate(capacity + (capacity >> 1));
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

    public void removeChunk(long packedPos) {
        Integer index = posToIndex.remove(packedPos);
        if (index != null) {
            lock.lock();
            try {
                if (shapeRefs[index] != null) {
                    shapeRefs[index].close();
                    shapeRefs[index] = null;
                }
                freeIndices.push(index);
                indexToPos.set(index, 0L);
            } finally {
                lock.unlock();
            }
        }
    }

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

    private void resetIndex(int index) {
        // This method must be called within a locked block.
        states[index] = STATE_UNLOADED;
        bodyIds[index] = UNUSED_BODY_ID;
        shapeRefs[index] = null;
        rebuildVersions[index] = 0;
        lifeCounters[index] = 0;
    }

    // --- Synchronized Accessors ---

    @Nullable
    public Integer getIndexForPos(long packedPos) {
        return posToIndex.get(packedPos);
    }

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

    public Collection<Integer> getManagedIndices() {
        return new ArrayList<>(posToIndex.values());
    }

    public Collection<Long> getManagedPositions() {
        return new ArrayList<>(posToIndex.keySet());
    }

    public int getState(int index) {
        lock.lock();
        try {
            return states[index];
        } finally {
            lock.unlock();
        }
    }

    public void setState(int index, int state) {
        lock.lock();
        try {
            states[index] = state;
        } finally {
            lock.unlock();
        }
    }

    public int getBodyId(int index) {
        lock.lock();
        try {
            return bodyIds[index];
        } finally {
            lock.unlock();
        }
    }

    public void setBodyId(int index, int bodyId) {
        lock.lock();
        try {
            bodyIds[index] = bodyId;
        } finally {
            lock.unlock();
        }
    }

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

    public int getRebuildVersion(int index) {
        lock.lock();
        try {
            return rebuildVersions[index];
        } finally {
            lock.unlock();
        }
    }

    public int incrementAndGetRebuildVersion(int index) {
        lock.lock();
        try {
            return ++rebuildVersions[index];
        } finally {
            lock.unlock();
        }
    }

    public void setLifeCounter(int index, int life) {
        lock.lock();
        try {
            lifeCounters[index] = life;
        } finally {
            lock.unlock();
        }
    }

    public boolean decrementLifeCounterAndCheck(int index) {
        lock.lock();
        try {
            if (lifeCounters[index] > 0) {
                lifeCounters[index]--;
            }
            return lifeCounters[index] <= 0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically checks the current state and, if valid, transitions it to LOADING_SCHEDULED
     * and increments the rebuild version.
     *
     * @param index The index of the chunk section.
     * @return The new version number if scheduling was successful, otherwise -1.
     */
    public int scheduleRebuild(int index) {
        lock.lock();
        try {
            int currentState = states[index];
            if (currentState != STATE_GENERATING_SHAPE && currentState != STATE_LOADING_SCHEDULED) {
                states[index] = STATE_LOADING_SCHEDULED;
                return ++rebuildVersions[index];
            }
            return -1;
        } finally {
            lock.unlock();
        }
    }
}