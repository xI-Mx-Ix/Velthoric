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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;

/**
 * The base implementation for a chunk-based/region-based storage system.
 * <p>
 * <b>Architectural Change:</b>
 * This class no longer manages threads or file I/O directly. It acts as a bridge between
 * the high-level game objects (Maps of data) and the low-level {@link VxIOProcessor}.
 * <p>
 * Responsibilities:
 * 1. Managing the {@link VxRegionIndex}.
 * 2. Caching loaded regions in memory ({@code loadedRegions}).
 * 3. Serializing/Deserializing data (Data <-> Byte Array) + Checksum validation.
 * 4. Delegating file operations to the {@code VxIOProcessor}.
 *
 * @param <K> The key type (e.g., UUID).
 * @param <V> The value type (e.g., serialized byte array of a body).
 *
 * @author xI-Mx-Ix
 */
public abstract class VxAbstractRegionStorage<K, V> {

    /**
     * A simple record representing the coordinates of a 32x32 chunk region.
     */
    public record RegionPos(int x, int z) {}

    /**
     * In-memory cache of deserialized region data.
     * This allows instant access to data without querying the worker if it's already loaded.
     */
    protected final ConcurrentHashMap<RegionPos, Map<K, V>> loadedRegions = new ConcurrentHashMap<>();

    protected final ServerLevel level;
    protected final Path storagePath;
    protected final String filePrefix;
    protected VxRegionIndex regionIndex;

    /**
     * The dedicated sequential worker handling all file interactions for this storage type.
     */
    protected final VxIOProcessor ioWorker;

    public VxAbstractRegionStorage(ServerLevel level, String storageSubFolder, String filePrefix) {
        this.level = level;
        this.filePrefix = filePrefix;

        // Resolve paths (e.g., world/velthoric/body)
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        Path dimensionRoot = DimensionType.getStorageFolder(level.dimension(), worldRoot);
        this.storagePath = dimensionRoot.resolve("velthoric").resolve(storageSubFolder);

        // Initialize the dedicated worker
        this.ioWorker = new VxIOProcessor(this.storagePath, this.filePrefix);
    }

    protected abstract VxRegionIndex createRegionIndex();

    /**
     * Sets up the storage directory and loads the index.
     */
    public void initialize() {
        this.regionIndex = createRegionIndex();
        try {
            Files.createDirectories(storagePath);
            if (regionIndex != null) {
                regionIndex.load();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create Velthoric storage directory: " + storagePath, e);
        }
    }

    /**
     * Shuts down the storage system, closing the worker and clearing caches.
     */
    public void shutdown() {
        loadedRegions.clear();
        ioWorker.close();
    }

    /**
     * Iterates over all currently loaded regions and schedules them for saving.
     * <p>
     * This implementation takes a "Safe" approach: it resubmits all loaded regions to the worker.
     * The worker's {@code compute} method will update the pending write cache. If data hasn't
     * changed significantly, overhead is mainly serialization, but it ensures no data loss.
     *
     * @return A future that completes when all dirty data is flushed to disk.
     */
    public CompletableFuture<Void> saveDirtyRegions() {
        List<CompletableFuture<Void>> saveFutures = new ArrayList<>();

        // Iterate all loaded regions and schedule save
        loadedRegions.forEach((pos, data) -> {
            if (!data.isEmpty()) {
                saveFutures.add(this.saveRegion(pos));
            }
        });

        if (regionIndex != null) {
            saveFutures.add(regionIndex.save());
        }

        // 1. Wait for all 'store' commands to be queued in the worker
        // 2. Then call synchronize(true) to force the worker to actually write everything to disk
        return CompletableFuture.allOf(saveFutures.toArray(new CompletableFuture[0]))
                .thenCompose(v -> ioWorker.synchronize(true));
    }

    /**
     * Serializes the current state of a region and submits it to the I/O worker.
     *
     * @param pos The position of the region to save.
     * @return A future that completes when the file is physically written.
     */
    public CompletableFuture<Void> saveRegion(RegionPos pos) {
        Map<K, V> data = loadedRegions.get(pos);

        // If the region isn't loaded or is empty, we effectively do nothing (or clear it).
        // Here we assume if it's not in map, we don't touch the file.
        if (data == null) return CompletableFuture.completedFuture(null);

        // 1. CPU Bound Work: Serialize the map to a byte array with checksum.
        // We do this on the calling thread (or common pool) to avoid blocking the I/O thread with compression/hashing.
        byte[] serializedData = serializeRegion(data);

        // 2. I/O Bound Work: Submit to the worker queue.
        return ioWorker.store(pos, serializedData);
    }

    /**
     * Asynchronously loads a region.
     *
     * @param pos The position of the region.
     * @return A future containing the Map of data for that region.
     */
    protected CompletableFuture<Map<K, V>> getRegion(RegionPos pos) {
        // Check in-memory cache first
        Map<K, V> existing = loadedRegions.get(pos);
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }

        // Request raw bytes from the I/O worker
        return ioWorker.load(pos).thenApply(bytes -> {
            // This runs once the I/O thread has fetched the bytes (or grabbed them from write-cache).
            Map<K, V> map = new ConcurrentHashMap<>();
            if (bytes != null && bytes.length > 0) {
                // Deserialize verifies checksum and populates the map
                deserializeRegion(bytes, map);
            }
            // Update cache
            loadedRegions.put(pos, map);
            return map;
        });
    }

    /**
     * Serializes the map into the binary format: [Checksum (8 bytes)][Data].
     */
    private byte[] serializeRegion(Map<K, V> entries) {
        if (entries.isEmpty()) return null;

        ByteBuf dataBuffer = Unpooled.buffer();
        try {
            writeRegionData(dataBuffer, entries);
            if (dataBuffer.readableBytes() == 0) return null;

            // Calculate CRC32 Checksum for data integrity
            CRC32 crc = new CRC32();
            crc.update(dataBuffer.nioBuffer());
            long checksum = crc.getValue();

            // Create final array
            byte[] finalBytes = new byte[8 + dataBuffer.readableBytes()];
            ByteBuffer buffer = ByteBuffer.wrap(finalBytes);

            // Write Header
            buffer.putLong(checksum);

            // Write Data
            dataBuffer.readBytes(finalBytes, 8, dataBuffer.readableBytes());

            return finalBytes;
        } finally {
            dataBuffer.release();
        }
    }

    /**
     * Deserializes raw file bytes into the provided map, performing validation.
     */
    private void deserializeRegion(byte[] fileBytes, Map<K, V> targetMap) {
        if (fileBytes.length < 8) {
            VxMainClass.LOGGER.warn("Velthoric region file {} is too small to be valid.", filePrefix);
            return;
        }

        ByteBuf buffer = Unpooled.wrappedBuffer(fileBytes);
        try {
            long storedChecksum = buffer.readLong();
            ByteBuf dataSlice = buffer.slice();

            // Verify Integrity
            CRC32 crc = new CRC32();
            crc.update(dataSlice.nioBuffer());

            if (storedChecksum != crc.getValue()) {
                VxMainClass.LOGGER.error("Checksum mismatch for region {}. Data corrupted, loading empty.", filePrefix);
                return;
            }

            // Parse content
            readRegionData(dataSlice, targetMap);
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to deserialize region data for {}", filePrefix, e);
        } finally {
            buffer.release();
        }
    }

    public VxRegionIndex getRegionIndex() {
        return regionIndex;
    }

    // Abstract methods to handle specific object types (Body vs Constraint)
    protected abstract void readRegionData(ByteBuf buffer, Map<K, V> regionData);

    protected abstract void writeRegionData(ByteBuf buffer, Map<K, V> entries);
}