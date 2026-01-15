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
     * <b>Lazy Loading:</b> If the region file does not exist on disk, this method
     * returns an empty list immediately instead of creating an empty file.
     *
     * @param pos The chunk position.
     * @return A future containing the list of deserialized data objects.
     */
    public CompletableFuture<List<D>> loadChunk(ChunkPos pos) {
        return CompletableFuture.supplyAsync(() -> {
            // 1. Check pending writes first (Read-Your-Writes consistency)
            ByteBuf pendingBuf = pendingWrites.get(pos.toLong());
            if (pendingBuf != null) {
                // Slice to protect the original buffer indexes
                return deserializeChunk(pendingBuf.slice());
            }

            // 2. Read from disk
            try {
                // Request the file only if it exists (create=false)
                VxRegionFile regionFile = regionCache.getRegionFile(pos, false);

                // If file doesn't exist, we have no data
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
     * This method executes immediately on the calling thread to capture the state of the objects.
     * The serialization result is stored in a pooled buffer and handed off to the I/O thread.
     *
     * @param pos     The chunk position.
     * @param objects The objects to save.
     */
    public void saveChunk(ChunkPos pos, Collection<T> objects) {
        if (objects.isEmpty()) {
            // If empty, we schedule a write of an empty buffer (or null) to clear data on disk
            // Logic handled in flushing: if buffer is readable=0, we might remove the chunk.
            // For simplicity here, we assume empty list means "delete chunk content".
            pendingWrites.put(pos.toLong(), Unpooled.EMPTY_BUFFER);
            return;
        }

        ByteBuf buffer = ByteBufAllocator.DEFAULT.ioBuffer();
        boolean success = false;
        try {
            VxByteBuf vxBuf = new VxByteBuf(buffer);
            vxBuf.writeInt(objects.size());
            
            for (T obj : objects) {
                writeSingle(obj, vxBuf);
            }
            
            // Put into pending map. Previous value must be released if it exists.
            ByteBuf old = pendingWrites.put(pos.toLong(), buffer);
            if (old != null && old != Unpooled.EMPTY_BUFFER) {
                old.release();
            }
            success = true;
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to serialize chunk {}", pos, e);
        } finally {
            // If something failed and we didn't store the buffer, release it.
            if (!success) {
                buffer.release();
            }
        }
    }

    /**
     * Flushes all pending buffers to disk.
     * <p>
     * <b>Optimized Saving:</b>
     * <ul>
     *     <li>If writing data: Creates the file if missing.</li>
     *     <li>If deleting data (empty buffer): Does NOT create the file if it is missing.</li>
     * </ul>
     *
     * @param sync If true, blocks until all writes are complete.
     */
    public CompletableFuture<Void> flush(boolean sync) {
        if (pendingWrites.isEmpty()) return CompletableFuture.completedFuture(null);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Long chunkKey : pendingWrites.keySet()) {
            ByteBuf buffer = pendingWrites.remove(chunkKey);
            if (buffer == null) continue;

            ChunkPos pos = new ChunkPos(chunkKey);

            CompletableFuture<Void> writeTask = CompletableFuture.runAsync(() -> {
                try {
                    boolean isDeletion = !buffer.isReadable();

                    // Only create the file if we actually have data to write.
                    // If we are deleting (empty buffer), pass false to prevent creating a file just to write 0s.
                    VxRegionFile regionFile = regionCache.getRegionFile(pos, !isDeletion);

                    if (regionFile != null) {
                        if (!isDeletion) {
                            regionFile.write(pos, buffer);
                        } else {
                            // Write empty buffer to clear the sector
                            regionFile.write(pos, Unpooled.EMPTY_BUFFER);
                        }
                    }
                    // If regionFile is null here, it means we wanted to delete data from a file
                    // that doesn't exist on disk -> Operation is effectively already done.

                } catch (IOException e) {
                    VxMainClass.LOGGER.error("Failed to flush chunk {}", pos, e);
                } finally {
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