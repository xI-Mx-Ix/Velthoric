package net.xmx.velthoric.physics.terrain.cache;

import com.github.stephengold.joltjni.Shape;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.StreamInWrapper;
import com.github.stephengold.joltjni.StreamOutWrapper;
import com.github.stephengold.joltjni.std.StringStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.terrain.VxSectionPos;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class TerrainStorage {

    private record RegionPos(int x, int z) {}
    private record ShapeEntry(int hash, byte[] data) {}

    private static class RegionData {
        final ConcurrentHashMap<Long, ShapeEntry> shapes = new ConcurrentHashMap<>();
        final AtomicBoolean dirty = new AtomicBoolean(false);
    }

    private final Path storagePath;
    private final ConcurrentHashMap<RegionPos, RegionData> loadedRegions = new ConcurrentHashMap<>();

    public TerrainStorage(ServerLevel level) {
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        Path dimensionRoot = DimensionType.getStorageFolder(level.dimension(), worldRoot);
        this.storagePath = dimensionRoot.resolve("velthoric").resolve("terrain");
    }

    public void initialize() {
        try {
            Files.createDirectories(storagePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create terrain storage directory", e);
        }
    }

    public void shutdown() {
        loadedRegions.entrySet().parallelStream().forEach(entry -> {
            saveRegion(entry.getKey(), entry.getValue());
        });
    }

    public void saveDirtyRegions() {
        var dirtyRegions = loadedRegions.entrySet().parallelStream()
                .filter(entry -> entry.getValue().dirty.get())
                .toList();

        if (!dirtyRegions.isEmpty()) {
            dirtyRegions.forEach(entry -> saveRegion(entry.getKey(), entry.getValue()));
        }
    }

    public ShapeRefC getShape(VxSectionPos pos, int contentHash) {
        RegionPos regionPos = getRegionPos(pos);
        RegionData region = loadedRegions.computeIfAbsent(regionPos, this::loadRegion);
        long packedPos = pack(pos);

        ShapeEntry entry = region.shapes.get(packedPos);
        if (entry == null || entry.hash() != contentHash) {
            return null;
        }

        try (StringStream stringStream = new StringStream(new String(entry.data()));
             StreamInWrapper streamIn = new StreamInWrapper(stringStream)) {
            try (ShapeResult result = Shape.sRestoreFromBinaryState(streamIn)) {
                if (result.isValid()) {
                    return result.get();
                } else {
                    VxMainClass.LOGGER.warn("Failed to deserialize shape from storage for {}: {}", pos, result.getError());
                }
            }
        }
        return null;
    }

    public void storeShape(VxSectionPos pos, int contentHash, ShapeRefC shape) {
        RegionPos regionPos = getRegionPos(pos);
        RegionData region = loadedRegions.computeIfAbsent(regionPos, this::loadRegion);

        byte[] shapeData;
        try (StringStream stringStream = new StringStream();
             StreamOutWrapper streamOut = new StreamOutWrapper(stringStream)) {
            shape.saveBinaryState(streamOut);
            shapeData = stringStream.str().getBytes();
        }

        if (shapeData.length > 0) {
            ShapeEntry newEntry = new ShapeEntry(contentHash, shapeData);
            ShapeEntry oldEntry = region.shapes.put(pack(pos), newEntry);
            if (!newEntry.equals(oldEntry)) {
                region.dirty.set(true);
            }
        }
    }

    public void removeShape(VxSectionPos pos) {
        RegionPos regionPos = getRegionPos(pos);
        RegionData region = loadedRegions.computeIfAbsent(regionPos, this::loadRegion);
        if (region.shapes.remove(pack(pos)) != null) {
            region.dirty.set(true);
        }
    }

    private RegionPos getRegionPos(VxSectionPos pos) {
        return new RegionPos(pos.x() >> 5, pos.z() >> 5);
    }

    private Path getRegionFile(RegionPos pos) {
        return storagePath.resolve(String.format("terrain.%d.%d.vxdat", pos.x(), pos.z()));
    }

    private long pack(VxSectionPos pos) {
        return (long) pos.x() << 40 | ((long) pos.z() & 0xFFFFF) << 20 | ((long) pos.y() & 0xFFFFF);
    }

    private RegionData loadRegion(RegionPos pos) {
        Path regionFile = getRegionFile(pos);
        RegionData regionData = new RegionData();
        if (!Files.exists(regionFile)) {
            return regionData;
        }

        try (FileChannel channel = FileChannel.open(regionFile, StandardOpenOption.READ)) {
            ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            ByteBuf byteBuf = Unpooled.wrappedBuffer(buffer);

            while (byteBuf.readableBytes() > 0) {
                long packedPos = byteBuf.readLong();
                int hash = byteBuf.readInt();
                int dataLength = byteBuf.readInt();

                if (dataLength > 0 && byteBuf.isReadable(dataLength)) {
                    byte[] data = new byte[dataLength];
                    byteBuf.readBytes(data);
                    regionData.shapes.put(packedPos, new ShapeEntry(hash, data));
                }
            }
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to load terrain region file {}", regionFile, e);
        }
        return regionData;
    }

    private void saveRegion(RegionPos pos, RegionData data) {
        if (!data.dirty.get()) {
            return;
        }

        Path regionFile = getRegionFile(pos);
        ByteBuf buffer = Unpooled.buffer();

        data.shapes.forEach((packedPos, entry) -> {
            buffer.writeLong(packedPos);
            buffer.writeInt(entry.hash());
            buffer.writeInt(entry.data().length);
            buffer.writeBytes(entry.data());
        });

        try {
            if (buffer.readableBytes() > 0) {
                try (FileChannel channel = FileChannel.open(regionFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    channel.write(buffer.nioBuffer());
                }
                data.dirty.set(false);
            } else {
                Files.deleteIfExists(regionFile);
            }
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to save terrain region file {}", regionFile, e);
        } finally {
            if (buffer.refCnt() > 0) {
                buffer.release();
            }
        }
    }
}