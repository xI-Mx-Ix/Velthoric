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
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class VxAbstractRegionStorage<K, V> {

    public record RegionPos(int x, int z) {}

    public static class RegionData<K, V> {
        public final ConcurrentHashMap<K, V> entries = new ConcurrentHashMap<>();
        public final AtomicBoolean dirty = new AtomicBoolean(false);
    }

    protected final Path storagePath;
    private final String filePrefix;
    protected final ConcurrentHashMap<RegionPos, RegionData<K, V>> loadedRegions = new ConcurrentHashMap<>();
    protected final ServerLevel level;
    protected final VxRegionIndex regionIndex;

    public VxAbstractRegionStorage(ServerLevel level, String storageSubFolder, String filePrefix) {
        this.level = level;
        this.filePrefix = filePrefix;
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        Path dimensionRoot = DimensionType.getStorageFolder(level.dimension(), worldRoot);
        this.storagePath = dimensionRoot.resolve("velthoric").resolve(storageSubFolder);
        this.regionIndex = createRegionIndex();
    }

    protected VxRegionIndex createRegionIndex() {
        return null;
    }

    public void initialize() {
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
    }

    public void saveDirtyRegions() {
        loadedRegions.entrySet().parallelStream()
                .filter(entry -> entry.getValue().dirty.get())
                .forEach(entry -> saveRegion(entry.getKey(), entry.getValue()));
        if (regionIndex != null) {
            regionIndex.save();
        }
    }

    protected abstract void readRegionData(ByteBuf buffer, RegionData<K, V> regionData);
    protected abstract void writeRegionData(ByteBuf buffer, Map<K, V> entries);

    private Path getRegionFile(RegionPos pos) {
        return storagePath.resolve(String.format("%s.%d.%d.vxdat", filePrefix, pos.x(), pos.z()));
    }

    protected RegionData<K, V> loadRegion(RegionPos pos) {
        Path regionFile = getRegionFile(pos);
        RegionData<K, V> regionData = new RegionData<>();
        if (!Files.exists(regionFile)) {
            return regionData;
        }

        try (FileChannel channel = FileChannel.open(regionFile, StandardOpenOption.READ)) {
            if (channel.size() > 0) {
                ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
                ByteBuf byteBuf = Unpooled.wrappedBuffer(buffer);
                readRegionData(byteBuf, regionData);
            }
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to load region file {}", regionFile, e);
        }
        return regionData;
    }

    protected void saveRegion(RegionPos pos, RegionData<K, V> data) {
        if (!data.dirty.get()) {
            return;
        }

        Path regionFile = getRegionFile(pos);

        if (data.entries.isEmpty()) {
            try {
                Files.deleteIfExists(regionFile);
                data.dirty.set(false);
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
                data.dirty.set(false);
            }
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to save region file {}", regionFile, e);
        } finally {
            if (buffer.refCnt() > 0) {
                buffer.release();
            }
        }
    }
}