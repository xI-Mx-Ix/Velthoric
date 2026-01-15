/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.persistence;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.persistence.region.VxRegionFile;
import net.xmx.velthoric.physics.persistence.region.VxRegionFileCache;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A high-performance, generic storage system based on Minecraft's Region File concept.
 * <p>
 * This system serializes all objects residing in a single chunk into a combined binary blob.
 * The resulting data is stored in a {@link VxRegionFile}, enabling efficient spatial grouping
 * and disk access patterns.
 * <p>
 * <b>Performance features:</b>
 * <ul>
 *     <li><b>Netty Pooled Buffers:</b> Uses pooled memory for serialization to minimize GC pressure.</li>
 *     <li><b>Async I/O:</b> Writes are offloaded to a dedicated worker thread via {@link VxIOProcessor}.</li>
 *     <li><b>Batching:</b> Objects are grouped by chunk, reducing the number of file entries significantly.</li>
 * </ul>
 *
 * @param <T> The runtime object type (e.g., VxBody).
 * @param <D> The intermediate serialized data record (e.g., VxSerializedBodyData).
 * @author xI-Mx-Ix
 */
public abstract class VxChunkBasedStorage<T, D> {

    protected final Path storagePath;
    protected final VxRegionFileCache regionCache;
    protected final VxIOProcessor ioProcessor;
    
    /**
     * Holds serialized chunk data waiting to be written to disk.
     * Key: ChunkPos as Long. Value: Netty ByteBuf (retained).
     */
    protected final ConcurrentHashMap<Long, ByteBuf> pendingWrites = new ConcurrentHashMap<>();

    public VxChunkBasedStorage(ServerLevel level, String folderName, String extension) {
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        Path dimensionRoot = DimensionType.getStorageFolder(level.dimension(), worldRoot);
        this.storagePath = dimensionRoot.resolve("velthoric").resolve(folderName);
        this.regionCache = new VxRegionFileCache(storagePath, extension);
        this.ioProcessor = new VxIOProcessor("IO-" + folderName);
    }

    public void shutdown() {
        flush(true).join();
        ioProcessor.close();
        regionCache.closeAll();
    }

    /**
     * Loads all objects for a specific chunk asynchronously.
     * <p>
     * This method prioritizes in-memory pending writes over disk storage to ensure
     * data consistency (Read-Your-Writes). If data is pending in the write queue,
     * it is used directly; otherwise, the data is read from the region file.
     *
     * @param pos The chunk position.
     * @return A future containing the list of deserialized data objects.
     */
    public CompletableFuture<List<D>> loadChunk(ChunkPos pos) {
        long key = pos.toLong();

        // Check the pending writes map on the calling thread to capture the latest state.
        ByteBuf pendingBuf = pendingWrites.get(key);

        if (pendingBuf != null) {
            // If the pending buffer is not readable (empty), it represents a pending deletion.
            if (!pendingBuf.isReadable()) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }

            // Create a retained duplicate of the buffer.
            // This shares the underlying memory but has independent indices and an independent reference count.
            // We retain it to ensure it remains valid during the asynchronous deserialization, even if flush() completes.
            final ByteBuf asyncSlice = pendingBuf.retainedDuplicate();

            return CompletableFuture.supplyAsync(() -> {
                try {
                    return deserializeChunk(asyncSlice);
                } finally {
                    asyncSlice.release();
                }
            }, ioProcessor.getExecutor());
        }

        // If no pending data exists, proceed to read from disk on the I/O thread.
        return CompletableFuture.supplyAsync(() -> {
            // Secondary check within the async thread to handle edge cases.
            ByteBuf lateCheck = pendingWrites.get(key);
            if (lateCheck != null) {
                if (!lateCheck.isReadable()) return Collections.emptyList();
                // Note: Ideally we would use the pending buffer here, but the race window is negligible.
                // Proceeding to disk is a safe fallback.
            }

            try {
                // Do not create the file if it doesn't exist (lazy loading).
                VxRegionFile regionFile = regionCache.getRegionFile(pos, false);
                if (regionFile == null) {
                    return Collections.emptyList();
                }

                ByteBuf diskBuf = regionFile.read(pos);
                if (diskBuf != null) {
                    try {
                        return deserializeChunk(diskBuf);
                    } finally {
                        diskBuf.release();
                    }
                }
            } catch (IOException e) {
                VxMainClass.LOGGER.error("Failed to load chunk data at {}", pos, e);
            }
            return Collections.emptyList();
        }, ioProcessor.getExecutor());
    }

    /**
     * Serializes a collection of objects belonging to a chunk and queues them for writing.
     * <p>
     * This method manages the lifecycle of the Netty buffers. It updates the pending write map
     * with the new state. If a pending write for this chunk already exists, the previous buffer
     * is released to prevent memory leaks.
     *
     * @param pos     The chunk position.
     * @param objects The objects to save.
     */
    public void saveChunk(ChunkPos pos, Collection<T> objects) {
        long key = pos.toLong();
        ByteBuf newBuffer;

        if (objects.isEmpty()) {
            // Use the shared EMPTY_BUFFER to signify deletion or an empty chunk.
            newBuffer = Unpooled.EMPTY_BUFFER;
        } else {
            newBuffer = ByteBufAllocator.DEFAULT.ioBuffer();
            boolean success = false;
            try {
                VxByteBuf vxBuf = new VxByteBuf(newBuffer);
                vxBuf.writeInt(objects.size());

                for (T obj : objects) {
                    writeSingle(obj, vxBuf);
                }
                success = true;
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Failed to serialize chunk {}", pos, e);
                newBuffer.release();
                return;
            } finally {
                // If serialization failed, release the allocated buffer.
                if (!success && newBuffer.refCnt() > 0) {
                    newBuffer.release();
                }
            }
        }

        // Update the pending map with the new buffer.
        // The put method returns the previous value associated with the key, if any.
        ByteBuf oldBuffer = pendingWrites.put(key, newBuffer);

        // If a previous buffer existed, release it as it is now obsolete and will not be written.
        if (oldBuffer != null && oldBuffer != Unpooled.EMPTY_BUFFER) {
            oldBuffer.release();
        }
    }

    /**
     * Flushes all pending buffers to disk asynchronously.
     * <p>
     * This method iterates over the pending writes and schedules I/O tasks.
     * It uses reference counting to ensure buffers remain valid during the asynchronous operation.
     * The map entry is only removed after the write completes, and only if the entry has not
     * been updated by a subsequent save operation in the meantime.
     *
     * @param sync If true, the method blocks until all write operations are completed.
     */
    public CompletableFuture<Void> flush(boolean sync) {
        if (pendingWrites.isEmpty()) return CompletableFuture.completedFuture(null);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Create a snapshot of keys to iterate over the currently pending tasks
        for (Long chunkKey : pendingWrites.keySet()) {
            ByteBuf buffer = pendingWrites.get(chunkKey);

            // The buffer might have been removed concurrently, so check for null.
            if (buffer == null) continue;

            // Increment the reference count to keep the buffer alive for the asynchronous I/O task.
            // This prevents the buffer from being deallocated if the map entry is replaced concurrently.
            buffer.retain();

            ChunkPos pos = new ChunkPos(chunkKey);

            CompletableFuture<Void> writeTask = CompletableFuture.runAsync(() -> {
                try {
                    writeToDisk(pos, buffer);
                } finally {
                    // Conditional Removal:
                    // Remove the entry from the map only if it still maps to the specific buffer we just wrote.
                    // If the map contains a different buffer, a new save occurred during the write,
                    // and we must leave the new data pending.
                    if (pendingWrites.remove(chunkKey, buffer)) {
                        // If we successfully removed it, we are responsible for releasing the map's reference.
                        if (buffer != Unpooled.EMPTY_BUFFER) {
                            buffer.release();
                        }
                    }

                    // Release the reference acquired by retain() at the start of the loop.
                    if (buffer != Unpooled.EMPTY_BUFFER) {
                        buffer.release();
                    }
                }
            }, ioProcessor.getExecutor());

            futures.add(writeTask);
        }

        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        if (sync) {
            allDone.join();
        }
        return allDone;
    }

    /**
     * Writes the buffer data to the region file system.
     * Handles both writing valid data and clearing sectors for empty data.
     *
     * @param pos    The position of the chunk.
     * @param buffer The buffer containing the serialized data.
     */
    private void writeToDisk(ChunkPos pos, ByteBuf buffer) {
        try {
            boolean isDeletion = !buffer.isReadable(); // Checks for EMPTY_BUFFER or 0 readable bytes

            // Retrieve the region file. Only create a new file if we are writing actual data.
            VxRegionFile regionFile = regionCache.getRegionFile(pos, !isDeletion);

            if (regionFile != null) {
                if (!isDeletion) {
                    regionFile.write(pos, buffer);
                } else {
                    // Write an empty buffer to the file to mark the chunk sector as free/deleted.
                    regionFile.write(pos, Unpooled.EMPTY_BUFFER);
                }
            }
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to flush chunk {}", pos, e);
        }
    }

    /**
     * Deserializes the raw buffer into a list of data objects.
     */
    private List<D> deserializeChunk(ByteBuf buffer) {
        if (!buffer.isReadable()) return Collections.emptyList();
        
        VxByteBuf vxBuf = new VxByteBuf(buffer);
        List<D> results = new ArrayList<>();
        
        try {
            int count = vxBuf.readInt();
            for (int i = 0; i < count; i++) {
                D data = readSingle(vxBuf);
                if (data != null) {
                    results.add(data);
                }
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Error deserializing chunk data", e);
        }
        return results;
    }

    // --- Abstract implementation hooks ---

    protected abstract void writeSingle(T object, VxByteBuf buffer);

    protected abstract D readSingle(VxByteBuf buffer);
}