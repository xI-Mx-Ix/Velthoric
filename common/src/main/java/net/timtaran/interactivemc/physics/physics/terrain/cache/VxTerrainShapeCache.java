/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.physics.terrain.cache;

import com.github.stephengold.joltjni.ShapeRefC;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An in-memory, fixed-size LRU (Least Recently Used) cache for terrain physics shapes.
 * It stores compiled Jolt physics shapes, keyed by a content hash of the chunk data,
 * to avoid regenerating them repeatedly. When the cache is full, the least recently
 * used shape is evicted and its native resources are released.
 *
 * @author xI-Mx-Ix
 */
public final class VxTerrainShapeCache {

    private final LinkedHashMap<Integer, ShapeRefC> cache;
    private final int capacity;

    public VxTerrainShapeCache(int capacity) {
        this.capacity = capacity;
        this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, ShapeRefC> eldest) {
                boolean shouldRemove = size() > VxTerrainShapeCache.this.capacity;
                if (shouldRemove && eldest.getValue() != null) {
                    eldest.getValue().close();
                }
                return shouldRemove;
            }
        };
    }

    /**
     * Retrieves a shape from the cache. Returns a new reference to the shape.
     * @param key The content hash of the shape.
     * @return A new ShapeRefC, or null if not found.
     */
    public synchronized ShapeRefC get(int key) {
        ShapeRefC masterRef = cache.get(key);
        if (masterRef != null && masterRef.getPtr() != null) {
            return masterRef.getPtr().toRefC();
        }
        return null;
    }

    /**
     * Adds a shape to the cache. The cache takes ownership of the provided ShapeRefC.
     * If a shape with the same key already exists, the old one is closed.
     * @param key The content hash of the shape.
     * @param shape The shape reference to store.
     */
    public synchronized void put(int key, ShapeRefC shape) {
        if (shape == null) return;

        ShapeRefC oldShape = cache.put(key, shape);

        if (oldShape != null && oldShape != shape) {
            oldShape.close();
        }
    }

    /**
     * Clears the cache, closing all stored shapes.
     */
    public synchronized void clear() {
        cache.values().forEach(shapeRef -> {
            if (shapeRef != null) shapeRef.close();
        });
        cache.clear();
    }

    /**
     * Returns an unmodifiable view of the cache's entries.
     * @return An unmodifiable map of the cache.
     */
    public synchronized Map<Integer, ShapeRefC> getEntries() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.cache));
    }
}