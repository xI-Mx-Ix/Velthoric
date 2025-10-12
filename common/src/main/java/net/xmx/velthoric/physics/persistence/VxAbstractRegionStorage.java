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
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

/**
 * An abstract base class for a region-based file storage system with corruption resistance.
 * This system groups data into region files and uses atomic write operations (write-to-temp, then rename)
 * and checksum validation to ensure data integrity even in case of a server crash.
 *
 * @param <K> The type of the key used to identify entries within a region.
 * @param <V> The type of the value to be stored.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxAbstractRegionStorage<K, V> {

    protected final ExecutorService ioExecutor;

    public record RegionPos(int x, int z) {}

    public static class RegionData<K, V> {
        public final ConcurrentHashMap<K, V> entries = new ConcurrentHashMap<>();
        public final AtomicBoolean dirty = new AtomicBoolean(false);
        public final AtomicBoolean saving = new AtomicBoolean(false);
    }

    protected final Path storagePath;
    private final String filePrefix;
    protected final ConcurrentHashMap<RegionPos, RegionData<K, V>> loadedRegions = new ConcurrentHashMap<>();
    protected final ServerLevel level;
    protected VxRegionIndex regionIndex;

    public VxAbstractRegionStorage(ServerLevel level, String storageSubFolder, String filePrefix) {
        this.level = level;
        this.filePrefix = filePrefix;
        this.ioExecutor = VxPersistenceManager.getExecutor();
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        Path dimensionRoot = DimensionType.getStorageFolder(level.dimension(), worldRoot);
        this.storagePath = dimensionRoot.resolve("velthoric").resolve(storageSubFolder);
    }

    protected abstract VxRegionIndex createRegionIndex();

    public void initialize() {
        this.regionIndex = createRegionIndex();
        try {
            Files.createDirectories(storagePath);
            if (regionIndex != null) {
                regionIndex.load();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create region storage directory: " + storagePath, e);
        }
    }

    public void shutdown() {
        loadedRegions.clear();
    }

    public CompletableFuture<Void> saveDirtyRegions() {
        List<CompletableFuture<Void>> saveFutures = new ArrayList<>();
        loadedRegions.forEach((pos, data) -> {
            if (data.dirty.get() && data.saving.compareAndSet(false, true)) {
                CompletableFuture<Void> saveTask = CompletableFuture.runAsync(() -> {
                    try {
                        if (data.dirty.compareAndSet(true, false)) {
                            saveRegionToFile(pos, data);
                        }
                    } finally {
                        data.saving.set(false);
                    }
                }, ioExecutor).exceptionally(ex -> {
                    data.saving.set(false);
                    VxMainClass.LOGGER.error("Exception in save task for region {}-{}", filePrefix, pos, ex);
                    return null;
                });
                saveFutures.add(saveTask);
            }
        });

        if (regionIndex != null) {
            regionIndex.save();
        }
        return CompletableFuture.allOf(saveFutures.toArray(new CompletableFuture[0]));
    }

    protected CompletableFuture<RegionData<K, V>> getRegion(RegionPos pos) {
        RegionData<K, V> existingData = loadedRegions.get(pos);
        if (existingData != null) {
            return CompletableFuture.completedFuture(existingData);
        }

        return CompletableFuture.supplyAsync(() ->
                loadedRegions.computeIfAbsent(pos, p -> loadRegionFromFile(p)), ioExecutor
        );
    }

    /**
     * Loads a single region from a file on disk.
     * Before reading, it cleans up any stale temporary files. It then validates the
     * file's integrity using a checksum. If the checksum is invalid, the file is
     * considered corrupt and an empty region is loaded instead.
     *
     * @param pos The position of the region to load.
     * @return The loaded RegionData.
     */
    private RegionData<K, V> loadRegionFromFile(RegionPos pos) {
        Path regionFile = getRegionFile(pos);
        RegionData<K, V> regionData = new RegionData<>();

        Path tempFile = regionFile.resolveSibling(regionFile.getFileName() + ".tmp");
        try {
            // Clean up temporary file from a previous crash, if it exists.
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            VxMainClass.LOGGER.warn("Could not delete stale temp file {}", tempFile, e);
        }

        if (!Files.exists(regionFile)) {
            return regionData;
        }

        try {
            byte[] fileBytes = Files.readAllBytes(regionFile);
            if (fileBytes.length < 8) { // A valid file must contain at least an 8-byte checksum.
                VxMainClass.LOGGER.warn("Region file {} is too small to be valid, ignoring.", regionFile);
                return regionData;
            }

            ByteBuf buffer = Unpooled.wrappedBuffer(fileBytes);
            try {
                long storedChecksum = buffer.readLong();
                ByteBuf dataSlice = buffer.slice(); // Get a view of the rest of the buffer without copying.

                // Verify the data against the stored checksum.
                CRC32 crc = new CRC32();
                crc.update(dataSlice.nioBuffer());

                if (storedChecksum != crc.getValue()) {
                    VxMainClass.LOGGER.error("Checksum mismatch for region file {}. The file may be corrupt. Loading as empty.", regionFile);
                    return new RegionData<>(); // Return empty data to prevent using corrupt data.
                }

                // If checksum is valid, proceed with deserialization.
                readRegionData(dataSlice, regionData);
            } finally {
                if (buffer.refCnt() > 0) {
                    buffer.release();
                }
            }
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to load region file {}", regionFile, e);
        }
        return regionData;
    }

    /**
     * Saves a single region to a file on disk using an atomic write-and-rename strategy.
     * The data is first written to a temporary file. If the write is successful, the
     * temporary file is atomically renamed to the final region file name.
     *
     * @param pos  The position of the region to save.
     * @param data The RegionData to save.
     */
    private void saveRegionToFile(RegionPos pos, RegionData<K, V> data) {
        Path regionFile = getRegionFile(pos);
        Path tempFile = regionFile.resolveSibling(regionFile.getFileName() + ".tmp");

        if (data.entries.isEmpty()) {
            try {
                Files.deleteIfExists(regionFile);
                Files.deleteIfExists(tempFile); // Also clean up any lingering temp file.
            } catch (IOException e) {
                VxMainClass.LOGGER.error("Failed to delete empty region file {}", regionFile, e);
            }
            return;
        }

        ByteBuf dataBuffer = Unpooled.buffer();
        try {
            writeRegionData(dataBuffer, data.entries);
            if (dataBuffer.readableBytes() == 0) return;

            // Calculate a checksum of the serialized data.
            CRC32 crc = new CRC32();
            crc.update(dataBuffer.nioBuffer());
            long checksum = crc.getValue();

            // Prepare a final buffer with the checksum followed by the data.
            ByteBuffer finalBuffer = ByteBuffer.allocate(8 + dataBuffer.readableBytes());
            finalBuffer.putLong(checksum);
            finalBuffer.put(dataBuffer.nioBuffer());
            finalBuffer.flip();

            // Write the final buffer to a temporary file.
            Files.createDirectories(regionFile.getParent());
            try (FileChannel channel = FileChannel.open(tempFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                channel.write(finalBuffer);
            }

            // Atomically move the temporary file to the final destination, replacing the old one.
            Files.move(tempFile, regionFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to save region file {}", regionFile, e);
            data.dirty.set(true); // If saving failed, mark as dirty again to retry on the next save cycle.
        } finally {
            if (dataBuffer.refCnt() > 0) {
                dataBuffer.release();
            }
        }
    }

    /**
     * Returns the region index associated with this storage.
     *
     * @return The VxRegionIndex instance.
     */
    public VxRegionIndex getRegionIndex() {
        return regionIndex;
    }

    private Path getRegionFile(RegionPos pos) {
        return storagePath.resolve(String.format("%s.%d.%d.vxdat", filePrefix, pos.x(), pos.z()));
    }

    protected abstract void readRegionData(ByteBuf buffer, RegionData<K, V> regionData);

    protected abstract void writeRegionData(ByteBuf buffer, Map<K, V> entries);
}