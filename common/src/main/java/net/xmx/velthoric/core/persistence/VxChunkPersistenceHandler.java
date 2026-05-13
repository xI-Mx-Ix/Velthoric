/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.persistence;

import net.minecraft.world.level.ChunkPos;

/**
 * Defines a contract for systems that require chunk-based persistence.
 * <p>
 * Implementing systems can register themselves with a {@link net.xmx.velthoric.core.physics.world.VxPhysicsWorld}
 * to automatically receive lifecycle events for saving and unloading data synchronized with Minecraft's 
 * chunk management.
 *
 * @author xI-Mx-Ix
 */
public interface VxChunkPersistenceHandler {

    /**
     * Called when a chunk's data should be serialized and queued for storage.
     *
     * @param pos The position of the chunk to save.
     */
    void onChunkSave(ChunkPos pos);

    /**
     * Called when a chunk's data should be loaded from storage.
     *
     * @param pos The position of the chunk to load.
     */
    void onChunkLoad(ChunkPos pos);

    /**
     * Called when a chunk is being removed from memory.
     *
     * @param pos The position of the chunk being unloaded.
     */
    void onChunkUnload(ChunkPos pos);

    /**
     * Forces all pending persistence tasks to be written to disk.
     *
     * @param block If true, the call blocks until all I/O operations are complete.
     */
    void flush(boolean block);
}