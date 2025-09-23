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
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An abstract base class for a region-based file storage system.
 * This system groups data into region files (similar to Minecraft's region format)
 * to optimize disk I/O. It handles asynchronous loading and saving of regions
 * using a shared, externally provided I/O executor.
 *
 * @param <K> The type of the key used to identify entries within a region.
 * @param <V> The type of the value to be stored.
 *
 * @author xI-Mx-Ix
 */
public abstract class VxAbstractRegionStorage<K, V> {

    /** The shared executor for handling all file I/O operations asynchronously. */
    protected final ExecutorService ioExecutor;

    /** Represents the coordinates of a region file. */
    public record RegionPos(int x, int z) {}

    /** A container for the in-memory data of a single region. */
    public static class RegionData<K, V> {
        public final ConcurrentHashMap<K, V> entries = new ConcurrentHashMap<>();
        public final AtomicBoolean dirty = new AtomicBoolean(false);
        public final AtomicBoolean saving = new AtomicBoolean(false);
    }

    protected final Path storagePath;
    private final String filePrefix;
    protected final ConcurrentHashMap<RegionPos, CompletableFuture<RegionData<K, V>>> loadedRegions = new ConcurrentHashMap<>();
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

    /**
     * Subclasses must implement this to create their specific type of region index.
     * @return A new {@link VxRegionIndex} instance, or null if not used.
     */
    protected abstract VxRegionIndex createRegionIndex();

    /**
     * Initializes the storage system, creating directories and loading the index.
     */
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

    /**
     * Shuts down the storage system, saving all pending changes.
     * This does NOT shut down the shared I/O executor.
     */
    public void shutdown() {
        CompletableFuture<Void> saveFuture = saveDirtyRegions();
        try {
            saveFuture.join(); // Block until all pending saves for this storage are complete.
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Error waiting for dirty regions to save during shutdown", e);
        }

        if (regionIndex != null) {
            regionIndex.save();
        }
        loadedRegions.clear();
    }

    /**
     * Asynchronously saves all regions that have been marked as dirty.
     * @return A CompletableFuture that completes when all save operations have finished.
     */
    public CompletableFuture<Void> saveDirtyRegions() {
        List<CompletableFuture<Void>> saveFutures = new CopyOnWriteArrayList<>();
        loadedRegions.forEach((pos, future) -> {
            if (future.isDone() && !future.isCompletedExceptionally()) {
                RegionData<K, V> data = future.getNow(null);
                if (data != null && data.dirty.get() && data.saving.compareAndSet(false, true)) {
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
                        VxMainClass.LOGGER.error("Exception in save task for region {}", pos, ex);
                        return null;
                    });
                    saveFutures.add(saveTask);
                }
            }
        });

        if (regionIndex != null) {
            regionIndex.save();
        }
        return CompletableFuture.allOf(saveFutures.toArray(new CompletableFuture[0]));
    }

    /**
     * Gets a future for the data of a specific region. If the region is not in the cache,
     * it will be loaded from disk asynchronously.
     * @param pos The position of the region to get.
     * @return A CompletableFuture that will complete with the region's data.
     */
    protected CompletableFuture<RegionData<K, V>> getRegion(RegionPos pos) {
        return loadedRegions.computeIfAbsent(pos, p ->
                CompletableFuture.supplyAsync(() -> loadRegionFromFile(p), ioExecutor)
        );
    }

    /**
     * Loads a single region file from disk.
     * @param pos The position of the region to load.
     * @return The loaded RegionData.
     */
    private RegionData<K, V> loadRegionFromFile(RegionPos pos) {
        Path regionFile = getRegionFile(pos);
        RegionData<K, V> regionData = new RegionData<>();
        if (!Files.exists(regionFile)) {
            return regionData;
        }

        try {
            byte[] fileBytes = Files.readAllBytes(regionFile);
            if (fileBytes.length > 0) {
                ByteBuf byteBuf = Unpooled.wrappedBuffer(fileBytes);
                readRegionData(byteBuf, regionData);
            }
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to load region file {}", regionFile, e);
        }
        return regionData;
    }

    /**
     * Saves a single region's data to a file.
     * @param pos  The position of the region to save.
     * @param data The data to be saved.
     */
    private void saveRegionToFile(RegionPos pos, RegionData<K, V> data) {
        Path regionFile = getRegionFile(pos);
        if (data.entries.isEmpty()) {
            try {
                Files.deleteIfExists(regionFile);
            } catch (IOException e) {
                VxMainClass.LOGGER.error("Failed to delete empty region file {}", regionFile, e);
            }
            return;
        }

        ByteBuf buffer = Unpooled.buffer();
        try {
            writeRegionData(buffer, data.entries);
            if (buffer.readableBytes() > 0) {
                Files.createDirectories(regionFile.getParent());
                try (FileChannel channel = FileChannel.open(regionFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    channel.write(buffer.nioBuffer());
                }
            }
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to save region file {}", regionFile, e);
            data.dirty.set(true); // Retry later
        } finally {
            if (buffer.refCnt() > 0) {
                buffer.release();
            }
        }
    }

    private Path getRegionFile(RegionPos pos) {
        return storagePath.resolve(String.format("%s.%d.%d.vxdat", filePrefix, pos.x(), pos.z()));
    }

    protected abstract void readRegionData(ByteBuf buffer, RegionData<K, V> regionData);

    protected abstract void writeRegionData(ByteBuf buffer, Map<K, V> entries);
}