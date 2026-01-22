/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.cache;

import com.github.stephengold.joltjni.ShapeRefC;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;

/**
 * An in-memory, fixed-size LRU (Least Recently Used) cache for terrain physics shapes.
 * <p>
 * It stores compiled Jolt physics shapes, keyed by a content hash of the chunk data,
 * to avoid regenerating them repeatedly. When the cache is full, the least recently
 * used shape is evicted and its native resources are released.
 * </p>
 * <p>
 * This implementation utilizes {@link Int2ObjectLinkedOpenHashMap} to prevent
 * auto-boxing of integer keys, thereby reducing garbage collection pressure
 * in the hot path.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public final class VxTerrainShapeCache {

    private final Int2ObjectLinkedOpenHashMap<ShapeRefC> cache;
    private final int capacity;

    /**
     * Constructs a new terrain shape cache with the specified capacity.
     *
     * @param capacity The maximum number of shapes to store before evicting old ones.
     */
    public VxTerrainShapeCache(int capacity) {
        this.capacity = capacity;
        // We use a fastutil LinkedOpenHashMap.
        // It maintains insertion order, which we will manipulate to emulate LRU behavior.
        this.cache = new Int2ObjectLinkedOpenHashMap<>(capacity, 0.75f);
    }

    /**
     * Retrieves a shape from the cache. Returns a new reference to the shape.
     * <p>
     * This operation marks the retrieved entry as the most recently used, moving it
     * to the end of the iteration order.
     * </p>
     *
     * @param key The content hash of the shape.
     * @return A new ShapeRefC, or null if not found.
     */
    public synchronized ShapeRefC get(int key) {
        // getAndMoveToLast retrieves the value and moves the entry to the last position,
        // maintaining the LRU order (last = most recently used).
        ShapeRefC masterRef = cache.getAndMoveToLast(key);
        if (masterRef != null && masterRef.getPtr() != null) {
            return masterRef.getPtr().toRefC();
        }
        return null;
    }

    /**
     * Adds a shape to the cache. The cache takes ownership of the provided ShapeRefC.
     * <p>
     * If a shape with the same key already exists, the old one is closed and replaced.
     * If the cache exceeds its defined capacity after insertion, the least recently
     * used (oldest) entry is evicted and closed.
     * </p>
     *
     * @param key   The content hash of the shape.
     * @param shape The shape reference to store.
     */
    public synchronized void put(int key, ShapeRefC shape) {
        if (shape == null) return;

        // putAndMoveToLast inserts or updates the value and moves it to the end (most recent).
        ShapeRefC oldShape = cache.putAndMoveToLast(key, shape);

        // If we replaced an existing shape, we must close the old one to free native memory.
        if (oldShape != null && oldShape != shape) {
            oldShape.close();
        }

        // Enforce capacity: if size exceeds limit, remove the first entry (LRU).
        if (cache.size() > capacity) {
            int firstKey = cache.firstIntKey();
            ShapeRefC removed = cache.removeFirst();
            if (removed != null) {
                removed.close();
            }
        }
    }

    /**
     * Clears the cache, closing all stored shapes to release native Jolt resources.
     */
    public synchronized void clear() {
        for (ShapeRefC shapeRef : cache.values()) {
            if (shapeRef != null) shapeRef.close();
        }
        cache.clear();
    }
}