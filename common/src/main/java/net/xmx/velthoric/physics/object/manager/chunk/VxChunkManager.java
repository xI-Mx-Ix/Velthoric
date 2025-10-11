/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.manager.chunk;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.physics.object.manager.VxObjectDataStore;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.object.manager.VxObjectNetworkDispatcher;
import net.xmx.velthoric.physics.object.type.VxBody;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the spatial partitioning of physics objects into chunks.
 * This class is responsible for tracking which objects reside in which chunk,
 * facilitating efficient proximity queries and handling chunk-based operations.
 *
 * @author xI-Mx-Ix
 */
public class VxChunkManager {

    private final VxObjectManager objectManager;
    private final VxObjectDataStore dataStore;
    private final VxObjectNetworkDispatcher networkDispatcher;

    /**
     * A spatial map that groups objects by the chunk they are in for efficient proximity queries.
     * The key is the long-encoded chunk position.
     */
    private final Long2ObjectMap<List<VxBody>> objectsByChunk = new Long2ObjectOpenHashMap<>();

    /**
     * Constructs a new VxChunkMap.
     *
     * @param objectManager The parent object manager.
     */
    public VxChunkManager(VxObjectManager objectManager) {
        this.objectManager = objectManager;
        this.dataStore = objectManager.getDataStore();
        this.networkDispatcher = objectManager.getNetworkDispatcher();
    }

    /**
     * Starts tracking an object, adding it to the appropriate chunk list based on its position.
     *
     * @param body The object to start tracking.
     */
    public void startTracking(VxBody body) {
        int index = body.getInternalBody().getDataStoreIndex();
        if (index == -1) return;

        long key = objectManager.getObjectChunkPos(index).toLong();
        dataStore.chunkKey[index] = key;

        synchronized (objectsByChunk) {
            objectsByChunk.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(body);
        }
    }

    /**
     * Stops tracking an object, removing it from its last known chunk list.
     *
     * @param body The object to stop tracking.
     */
    public void stopTracking(VxBody body) {
        int index = body.getInternalBody().getDataStoreIndex();
        if (index == -1) return;

        long key = dataStore.chunkKey[index];
        if (key != Long.MAX_VALUE) {
            synchronized (objectsByChunk) {
                List<VxBody> list = objectsByChunk.get(key);
                if (list != null) {
                    list.remove(body);
                    if (list.isEmpty()) {
                        objectsByChunk.remove(key);
                    }
                }
            }
        }
    }

    /**
     * Updates the chunk tracking information for a body when it moves across a chunk border.
     * This method ensures the object is correctly listed in the new chunk and removed from the old one,
     * and notifies the network dispatcher of the change.
     *
     * @param body    The body that moved.
     * @param fromKey The long-encoded key of the chunk it moved from.
     * @param toKey   The long-encoded key of the chunk it moved to.
     */
    public void updateObjectTracking(VxBody body, long fromKey, long toKey) {
        int index = body.getInternalBody().getDataStoreIndex();
        if (index != -1) {
            dataStore.chunkKey[index] = toKey;
        }

        // Remove from the old chunk's list.
        if (fromKey != Long.MAX_VALUE) {
            synchronized (objectsByChunk) {
                List<VxBody> fromList = objectsByChunk.get(fromKey);
                if (fromList != null) {
                    fromList.remove(body);
                    if (fromList.isEmpty()) {
                        objectsByChunk.remove(fromKey);
                    }
                }
            }
        }
        // Add to the new chunk's list.
        synchronized (objectsByChunk) {
            objectsByChunk.computeIfAbsent(toKey, k -> new CopyOnWriteArrayList<>()).add(body);
        }
        // Notify the network dispatcher about the movement for client-side tracking updates.
        networkDispatcher.onObjectMoved(body, new ChunkPos(fromKey), new ChunkPos(toKey));
    }


    /**
     * Retrieves a list of all physics objects within a specific chunk.
     *
     * @param pos The position of the chunk.
     * @return A list of objects in that chunk, which may be empty. The returned list is safe for concurrent iteration.
     */
    public List<VxBody> getObjectsInChunk(ChunkPos pos) {
        synchronized (objectsByChunk) {
            return objectsByChunk.getOrDefault(pos.toLong(), Collections.emptyList());
        }
    }
}