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
 * It uses a Structure of Arrays (SoA) layout and manages the lifecycle of chunks
 * via a life counter, inspired by proactive terrain management systems.
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

    // --- Chunk State Data (SoA) ---
    public volatile int[] states;
    public volatile int[] bodyIds;
    public volatile ShapeRefC[] shapeRefs;
    public volatile int[] rebuildVersions;
    public volatile int[] lifeCounters; // TTL for automatic cleanup

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
                indexToPos.set(index, 0L);
            } finally {
                lock.unlock();
            }
            return index;
        }
        return null;
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

    private void resetIndex(int index) {
        states[index] = STATE_UNLOADED;
        bodyIds[index] = UNUSED_BODY_ID;
        shapeRefs[index] = null;
        rebuildVersions[index] = 0;
        lifeCounters[index] = 0;
    }
}