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
 * An LRU (Least Recently Used) cache for storing compiled terrain physics shapes.
 * <p>
 * This cache helps to avoid regenerating shapes for identical chunk sections that appear
 * frequently. When the cache reaches its capacity, the least recently used entry is
 * evicted, and its associated native Jolt physics shape is properly released to prevent
 * memory leaks. This class is thread-safe.
 *
 * @author xI-Mx-Ix
 */
public class VxTerrainShapeCache {

    private final LinkedHashMap<Integer, ShapeRefC> cache;
    private final int capacity;

    /**
     * Constructs a new shape cache with a specified capacity.
     *
     * @param capacity The maximum number of shapes to store in the cache.
     */
    public VxTerrainShapeCache(int capacity) {
        this.capacity = capacity;
        this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {
            /**
             * Called when a new entry is added to the map and the map's size exceeds the capacity.
             * This implementation ensures that the native resource of the eldest entry is freed.
             * @param eldest The least recently accessed entry.
             * @return True if the eldest entry should be removed, false otherwise.
             */
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
     * Retrieves a shape from the cache for a given key.
     * <p>
     * If a shape is found, a new reference (RefC) to it is returned. The caller
     * is responsible for managing the lifecycle of the returned shape reference by closing it
     * when it's no longer needed.
     *
     * @param key The hash key of the chunk content.
     * @return A new reference to the cached shape, or null if not found.
     */
    public synchronized ShapeRefC get(int key) {
        ShapeRefC masterRef = cache.get(key);
        if (masterRef != null && masterRef.getPtr() != null) {
            // Return a new reference to the same native shape.
            return masterRef.getPtr().toRefC();
        }
        return null;
    }

    /**
     * Puts a new shape into the cache.
     * <p>
     * The cache takes ownership of the provided shape reference. The caller should not
     * use or close the shape reference after passing it to this method. If a shape for
     * the given key already exists, it is replaced, and the old shape's native resource is released.
     *
     * @param key   The hash key of the chunk content.
     * @param shape The shape reference to store. The cache takes ownership.
     */
    public synchronized void put(int key, ShapeRefC shape) {
        if (shape == null) {
            return;
        }

        ShapeRefC oldShape = cache.put(key, shape);

        if (oldShape != null && oldShape != shape) {
            oldShape.close();
        }
    }

    /**
     * Clears the entire cache, releasing all stored native shape resources.
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