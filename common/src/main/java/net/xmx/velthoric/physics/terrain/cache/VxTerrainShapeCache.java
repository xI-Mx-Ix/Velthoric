/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.cache;

import com.github.stephengold.joltjni.ShapeRefC;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A thread-safe LRU (Least Recently Used) cache for storing terrain physics shapes.
 * When the cache exceeds its capacity, the least recently used shape is evicted and
 * its native resources are released.
 *
 * @author xI-Mx-Ix
 */
public class VxTerrainShapeCache {

    private final LinkedHashMap<Integer, ShapeRefC> cache;
    private final int capacity;

    /**
     * Constructs a new terrain shape cache with a specified capacity.
     *
     * @param capacity The maximum number of shapes to store in the cache.
     */
    public VxTerrainShapeCache(int capacity) {
        this.capacity = capacity;
        this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, ShapeRefC> eldest) {
                boolean shouldRemove = size() > VxTerrainShapeCache.this.capacity;
                if (shouldRemove && eldest.getValue() != null) {
                    // Automatically close the native shape when it's evicted
                    eldest.getValue().close();
                }
                return shouldRemove;
            }
        };
    }

    /**
     * Retrieves a shape from the cache. This operation marks the entry as recently used.
     *
     * @param key The hash key of the shape.
     * @return A new {@link ShapeRefC} instance for the cached shape, or null if not found.
     */
    public synchronized ShapeRefC get(int key) {
        ShapeRefC masterRef = cache.get(key);
        if (masterRef != null && masterRef.getPtr() != null) {
            // Return a new reference to the same native object to ensure thread safety
            return masterRef.getPtr().toRefC();
        }
        return null;
    }

    /**
     * Adds a shape to the cache. If a shape with the same key already exists,
     * it is replaced, and the old shape's resources are released.
     *
     * @param key   The hash key for the shape.
     * @param shape The shape reference to store. The cache takes ownership of this reference.
     */
    public synchronized void put(int key, ShapeRefC shape) {
        if (shape == null) return;

        ShapeRefC oldShape = cache.put(key, shape);

        if (oldShape != null && oldShape != shape) {
            oldShape.close();
        }
    }

    /**
     * Clears the cache, releasing all stored native shape resources.
     */
    public synchronized void clear() {
        cache.values().forEach(shapeRef -> {
            if (shapeRef != null) {
                shapeRef.close();
            }
        });
        cache.clear();
    }

    /**
     * Returns an unmodifiable view of the cache's entries.
     *
     * @return An unmodifiable map of the cache entries.
     */
    public synchronized Map<Integer, ShapeRefC> getEntries() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.cache));
    }
}