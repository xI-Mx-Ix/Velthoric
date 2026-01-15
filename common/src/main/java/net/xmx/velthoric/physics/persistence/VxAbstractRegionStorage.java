/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.persistence;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.xmx.velthoric.init.VxMainClass;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

/**
 * The high-performance base class for region-based storage systems.
 * <p>
 * <b>Architecture Change (Massive Optimization):</b>
 * Unlike previous iterations that used a "Transaction-based" approach (creating a Future for every single operation),
 * this storage system operates on a <b>"Hot Map"</b> principle.
 * <p>
 * 1. <b>Instant Mutation:</b> Operations like {@link #putInMemory} or {@link #removeInMemory} modify
 * the in-memory {@link ConcurrentHashMap} <b>immediately</b>. This allows bulk operations (like removing 20,000 bodies)
 * to execute in nanoseconds without creating thousands of tasks or futures.<br>
 * 2. <b>Dirty State:</b> When data is modified, the containing region is marked as "dirty" via an {@link AtomicBoolean}.<br>
 * 3. <b>Lazy Persistence:</b> The {@link #saveDirtyRegions()} method must be called periodically (e.g., auto-save).
 * It snapshots the dirty maps and offloads the heavy work (serialization and disk I/O) to the {@link VxIOProcessor}.
 *
 * @param <K> The key type (typically UUID).
 * @param <V> The value type (typically serialized byte array).
 * @author xI-Mx-Ix
 */
public abstract class VxAbstractRegionStorage<K, V> {

    /**
     * Represents a coordinate of a storage region (usually 32x32 chunks).
     */
    public record RegionPos(int x, int z) {
    }

    /**
     * Internal container for a loaded region's data in RAM.
     */
    protected static class RegionHolder<K, V> {
        /**
         * The live data map.
         * Thread-safe for concurrent read/write access from the game thread and IO thread.
         */
        final ConcurrentHashMap<K, V> data = new ConcurrentHashMap<>();

        /**
         * Indicates if this region has changes that need to be flushed to disk.
         */
        final AtomicBoolean dirty = new AtomicBoolean(false);
    }

    protected final ServerLevel level;
    protected final Path storagePath;
    protected final String filePrefix;
    protected VxRegionIndex regionIndex;
    protected final VxIOProcessor ioWorker;

    /**
     * Cache of currently loaded regions. Access to this map is extremely fast (hot path).
     */
    protected final ConcurrentHashMap<RegionPos, RegionHolder<K, V>> loadedRegions = new ConcurrentHashMap<>();

    /**
     * Constructs the storage system.
     *
     * @param level            The server level context.
     * @param storageSubFolder The subfolder name (e.g., "body").
     * @param filePrefix       The prefix for region files (e.g., "body").
     */
    public VxAbstractRegionStorage(ServerLevel level, String storageSubFolder, String filePrefix) {
        this.level = level;
        this.filePrefix = filePrefix;

        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        Path dimensionRoot = DimensionType.getStorageFolder(level.dimension(), worldRoot);
        this.storagePath = dimensionRoot.resolve("velthoric").resolve(storageSubFolder);

        // A dedicated worker per storage type ensures maximum I/O throughput
        this.ioWorker = new VxIOProcessor(filePrefix);
    }

    /**
     * Factory method to create the specific index implementation.
     */
    protected abstract VxRegionIndex createRegionIndex(VxIOProcessor processor);

    /**
     * Initializes the storage directory and loads the index.
     */
    public void initialize() {
        this.regionIndex = createRegionIndex(ioWorker);
        try {
            Files.createDirectories(storagePath);
            if (regionIndex != null) {
                regionIndex.load();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to init storage: " + storagePath, e);
        }
    }

    /**
     * Shuts down the storage system, flushing all pending data and closing the I/O worker.
     */
    public void shutdown() {
        // Force flush before closing to ensure no data loss
        saveDirtyRegions().join();
        ioWorker.close();
        loadedRegions.clear();
    }

    // =================================================================================
    //  High-Performance In-Memory Operations
    // =================================================================================

    /**
     * Instantly updates data in the hot map.
     * <p>
     * This operation is blocking-free (RAM only) and thread-safe. It marks the region as dirty,
     * ensuring the data will be persisted during the next flush cycle.
     *
     * @param key   The object key (UUID).
     * @param value The object data.
     * @param pos   The region position where the data belongs.
     */
    protected void putInMemory(K key, V value, RegionPos pos) {
        // Ensure region container exists (fast RAM lookup)
        RegionHolder<K, V> holder = loadedRegions.computeIfAbsent(pos, k -> new RegionHolder<>());

        // Update data instantly
        holder.data.put(key, value);
        holder.dirty.set(true);

        // Update global index instantly
        if (regionIndex != null) {
            regionIndex.put((UUID) key, pos);
        }
    }

    /**
     * Instantly removes data from the hot map.
     * <p>
     * This operation is blocking-free (RAM only). Even if 20,000 items are removed,
     * this method returns immediately for each item, deferring the disk I/O cost.
     *
     * @param key The object key (UUID).
     */
    protected void removeInMemory(K key) {
        if (regionIndex == null) return;

        // Look up location
        RegionPos pos = regionIndex.get((UUID) key);
        if (pos == null) return; // Not persistent, nothing to do

        RegionHolder<K, V> holder = loadedRegions.get(pos);
        if (holder != null) {
            // Remove directly from RAM. Instant.
            if (holder.data.remove(key) != null) {
                holder.dirty.set(true);
            }
        }

        // Update index instantly
        regionIndex.remove((UUID) key);
    }

    /**
     * Retrieves data from the hot map if the region is already loaded.
     *
     * @param key The key to look up.
     * @return The value, or {@code null} if the region is not loaded or the key doesn't exist.
     */
    protected V getFromMemory(K key) {
        if (regionIndex == null) return null;
        RegionPos pos = regionIndex.get((UUID) key);
        if (pos == null) return null;

        RegionHolder<K, V> holder = loadedRegions.get(pos);
        return holder != null ? holder.data.get(key) : null;
    }

    // =================================================================================
    //  Persistence / IO
    // =================================================================================

    /**
     * Asynchronously loads a region from disk into the hot map.
     * <p>
     * If the region is already in RAM, it returns the existing data immediately.
     * Otherwise, it schedules a read task on the I/O worker.
     *
     * @param pos The position to load.
     * @return A {@link CompletableFuture} that completes with the map of data.
     */
    protected CompletableFuture<Map<K, V>> loadRegionAsync(RegionPos pos) {
        // Check RAM first (Fast Path)
        RegionHolder<K, V> existing = loadedRegions.get(pos);
        if (existing != null) {
            return CompletableFuture.completedFuture(existing.data);
        }

        // Trigger disk read (Slow Path)
        return ioWorker.submitRead(getRegionPath(pos)).thenApply(bytes -> {
            // Compute again inside the future in case another thread loaded it meanwhile
            RegionHolder<K, V> holder = loadedRegions.computeIfAbsent(pos, k -> new RegionHolder<>());

            if (bytes != null && bytes.length > 0) {
                // Deserialize only if we actually read data
                deserializeRegion(bytes, holder.data);
            }
            return holder.data;
        });
    }

    /**
     * Flushes all dirty regions to disk.
     * <p>
     * This iterates over the in-memory maps, creates a snapshot of any dirty regions,
     * and queues them for writing. This ensures data consistency without blocking the game loop
     * for the duration of the disk write.
     *
     * @return A {@link CompletableFuture} that completes when all dirty regions are queued for writing.
     */
    public CompletableFuture<Void> saveDirtyRegions() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Save Global Index
        if (regionIndex != null) {
            futures.add(regionIndex.save());
        }

        // Save Dirty Regions
        for (Map.Entry<RegionPos, RegionHolder<K, V>> entry : loadedRegions.entrySet()) {
            RegionHolder<K, V> holder = entry.getValue();
            RegionPos pos = entry.getKey();

            // CAS (Compare-And-Set) to ensure we only save if dirty, and reset the flag atomically.
            if (holder.dirty.compareAndSet(true, false)) {
                // Snapshot the data to avoid ConcurrentModification during serialization.
                // Since this is a batch operation (e.g., auto-save), creating a new HashMap copy
                // is acceptable overhead compared to blocking file I/O.
                Map<K, V> snapshot = new HashMap<>(holder.data);

                // Submit serialization and write task
                futures.add(serializeAndWrite(pos, snapshot));
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Serializes the map and submits it to the I/O worker.
     */
    private CompletableFuture<Void> serializeAndWrite(RegionPos pos, Map<K, V> data) {
        // If the map is empty, we effectively write an empty file (or delete logic in worker could handle it).
        if (data.isEmpty()) {
            return ioWorker.submitWrite(getRegionPath(pos), new byte[0]);
        }

        try {
            ByteBuf buffer = Unpooled.buffer();
            writeRegionData(buffer, data);

            byte[] payload = new byte[buffer.readableBytes()];
            buffer.readBytes(payload);
            buffer.release();

            // Add Checksum for integrity
            CRC32 crc = new CRC32();
            crc.update(payload);
            long checksum = crc.getValue();

            // Final Payload: [Checksum (long)] + [Data bytes]
            ByteBuf fileBuf = Unpooled.buffer(payload.length + 8);
            fileBuf.writeLong(checksum);
            fileBuf.writeBytes(payload);

            byte[] finalBytes = new byte[fileBuf.readableBytes()];
            fileBuf.readBytes(finalBytes);
            fileBuf.release();

            return ioWorker.submitWrite(getRegionPath(pos), finalBytes);

        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to serialize region {}", pos, e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Validates checksum and deserializes raw file bytes into the target map.
     */
    private void deserializeRegion(byte[] bytes, Map<K, V> target) {
        if (bytes.length < 8) return;
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        try {
            long storedChecksum = buf.readLong();
            byte[] payload = new byte[buf.readableBytes()];
            buf.readBytes(payload);

            CRC32 crc = new CRC32();
            crc.update(payload);

            if (storedChecksum == crc.getValue()) {
                ByteBuf dataBuf = Unpooled.wrappedBuffer(payload);
                readRegionData(dataBuf, target);
                dataBuf.release();
            } else {
                VxMainClass.LOGGER.error("Corrupt region file (checksum mismatch) for prefix: {}", filePrefix);
            }
        } finally {
            buf.release();
        }
    }

    private Path getRegionPath(RegionPos pos) {
        return storagePath.resolve(String.format("%s.%d.%d.vxdat", filePrefix, pos.x(), pos.z()));
    }

    /**
     * Retrieves the index manager associated with this storage.
     * <p>
     * This is required by the management systems to trigger index saves
     * during flush operations.
     *
     * @return The active region index.
     */
    public VxRegionIndex getRegionIndex() {
        return regionIndex;
    }

    // Abstract methods for specific implementation details
    protected abstract void readRegionData(ByteBuf buffer, Map<K, V> target);

    protected abstract void writeRegionData(ByteBuf buffer, Map<K, V> source);
}