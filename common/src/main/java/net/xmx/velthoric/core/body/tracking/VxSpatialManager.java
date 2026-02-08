/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.tracking;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.core.body.type.VxBody;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages the spatial partitioning of physics bodies into chunks.
 * This class is responsible for tracking which objects reside in which chunk,
 * facilitating efficient proximity queries and handling chunk-based operations.
 * 
 * This manager is independent of the physics engine and data store, 
 * operating purely on long-encoded chunk keys and body references.
 *
 * @author xI-Mx-Ix
 */
public class VxSpatialManager {

    /**
     * A spatial map that groups objects by the chunk they are in for efficient proximity queries.
     * The list is a simple ArrayList, as all access is externally synchronized on the map itself.
     */
    private final Long2ObjectMap<List<VxBody>> bodiesByChunk = new Long2ObjectOpenHashMap<>();

    /**
     * Starts tracking a body by adding it to the specified chunk bucket.
     *
     * @param chunkKey The long-encoded key of the chunk.
     * @param body     The body to start tracking.
     */
    public void add(long chunkKey, VxBody body) {
        synchronized (bodiesByChunk) {
            bodiesByChunk.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(body);
        }
    }

    /**
     * Stops tracking a body by removing it from the specified chunk bucket.
     *
     * @param chunkKey The long-encoded key of the chunk.
     * @param body     The body to stop tracking.
     */
    public void remove(long chunkKey, VxBody body) {
        if (chunkKey == Long.MAX_VALUE) return;

        synchronized (bodiesByChunk) {
            List<VxBody> list = bodiesByChunk.get(chunkKey);
            if (list != null) {
                list.remove(body);
                if (list.isEmpty()) {
                    bodiesByChunk.remove(chunkKey);
                }
            }
        }
    }

    /**
     * Updates the spatial tracking information for a body when it moves across a chunk border.
     * Ensures the body is correctly listed in the new chunk and removed from the old one.
     *
     * @param body    The body that moved.
     * @param fromKey The long-encoded key of the chunk it moved from.
     * @param toKey   The long-encoded key of the chunk it moved to.
     */
    public void move(VxBody body, long fromKey, long toKey) {
        if (fromKey == toKey) return;

        synchronized (bodiesByChunk) {
            // Remove from the old chunk's list.
            if (fromKey != Long.MAX_VALUE) {
                List<VxBody> fromList = bodiesByChunk.get(fromKey);
                if (fromList != null) {
                    fromList.remove(body);
                    if (fromList.isEmpty()) {
                        bodiesByChunk.remove(fromKey);
                    }
                }
            }
            // Add to the new chunk's list.
            bodiesByChunk.computeIfAbsent(toKey, k -> new ArrayList<>()).add(body);
        }
    }

    /**
     * Executes a given action for each body in the specified chunk in a thread-safe manner.
     *
     * @param chunkKey The long-encoded key of the chunk.
     * @param action   The action to perform on each body.
     */
    public void forEachInChunk(long chunkKey, Consumer<VxBody> action) {
        synchronized (bodiesByChunk) {
            List<VxBody> bodies = bodiesByChunk.get(chunkKey);
            if (bodies != null && !bodies.isEmpty()) {
                for (VxBody body : bodies) {
                    action.accept(body);
                }
            }
        }
    }

    /**
     * Atomically removes all bodies associated with a chunk and returns them.
     *
     * @param chunkKey The long-encoded key of the chunk to clear.
     * @return The list of bodies that were in the chunk, or an empty list if none.
     */
    public List<VxBody> removeAllInChunk(long chunkKey) {
        synchronized (bodiesByChunk) {
            List<VxBody> removed = bodiesByChunk.remove(chunkKey);
            return removed != null ? removed : Collections.emptyList();
        }
    }

    /**
     * Calculates the packed long representation of a chunk position from world coordinates.
     *
     * @param x The world X coordinate.
     * @param z The world Z coordinate.
     * @return The packed chunk coordinates as a long.
     */
    public static long calculateChunkKey(double x, double z) {
        return ChunkPos.asLong(
                SectionPos.posToSectionCoord(x),
                SectionPos.posToSectionCoord(z)
        );
    }
}