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
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class VxAbstractRegionStorage<K, V> {

    private ExecutorService ioExecutor;

    public record RegionPos(int x, int z) {}

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
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        Path dimensionRoot = DimensionType.getStorageFolder(level.dimension(), worldRoot);
        this.storagePath = dimensionRoot.resolve("velthoric").resolve(storageSubFolder);
    }

    protected abstract VxRegionIndex createRegionIndex();

    public void initialize() {
        this.regionIndex = createRegionIndex();
        int threadCount = Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
        this.ioExecutor = Executors.newFixedThreadPool(threadCount, r -> new Thread(r, "Velthoric-Persistence-IO-" + filePrefix));

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
        saveDirtyRegions();
        if (regionIndex != null) {
            regionIndex.save();
        }
        loadedRegions.clear();

        if (ioExecutor != null) {
            ioExecutor.shutdown();
            try {
                if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    ioExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                ioExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public void saveDirtyRegions() {
        loadedRegions.forEach((pos, future) -> {
            if (future.isDone() && !future.isCompletedExceptionally()) {
                RegionData<K, V> data = future.getNow(null);
                if (data != null && data.dirty.get() && data.saving.compareAndSet(false, true)) {
                    CompletableFuture.runAsync(() -> {
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
                }
            }
        });
        if (regionIndex != null) {
            regionIndex.save();
        }
    }

    protected abstract void readRegionData(ByteBuf buffer, RegionData<K, V> regionData);
    protected abstract void writeRegionData(ByteBuf buffer, Map<K, V> entries);

    private Path getRegionFile(RegionPos pos) {
        return storagePath.resolve(String.format("%s.%d.%d.vxdat", filePrefix, pos.x(), pos.z()));
    }

    protected CompletableFuture<RegionData<K, V>> getRegion(RegionPos pos) {
        return loadedRegions.computeIfAbsent(pos, p ->
                CompletableFuture.supplyAsync(() -> loadRegionFromFile(p), ioExecutor)
        );
    }

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
            data.dirty.set(true);
        } finally {
            if (buffer.refCnt() > 0) {
                buffer.release();
            }
        }
    }
}