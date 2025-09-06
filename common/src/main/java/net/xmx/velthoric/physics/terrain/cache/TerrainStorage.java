package net.xmx.velthoric.physics.terrain.cache;

import com.github.stephengold.joltjni.Shape;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.StreamInWrapper;
import com.github.stephengold.joltjni.StreamOutWrapper;
import com.github.stephengold.joltjni.std.StringStream;
import io.netty.buffer.ByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.persistence.VxAbstractRegionStorage;
import net.xmx.velthoric.physics.terrain.VxSectionPos;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public class TerrainStorage extends VxAbstractRegionStorage<Long, TerrainStorage.ShapeEntry> {

    public record ShapeEntry(int hash, byte[] data) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ShapeEntry that = (ShapeEntry) o;
            return hash == that.hash && Arrays.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(hash);
            result = 31 * result + Arrays.hashCode(data);
            return result;
        }
    }

    public TerrainStorage(ServerLevel level) {
        super(level, "terrain", "terrain");
    }

    private RegionPos getRegionPos(VxSectionPos pos) {
        return new RegionPos(pos.x() >> 5, pos.z() >> 5);
    }

    private long pack(VxSectionPos pos) {
        return ((long) pos.x() & 0xFFFFF_FFFFFL) << 40 |
                ((long) pos.z() & 0xFFFFF) << 20 |
                ((long) pos.y() & 0xFFFFF);
    }

    @Override
    protected void readRegionData(ByteBuf buffer, RegionData<Long, ShapeEntry> regionData) {
        while (buffer.readableBytes() > 0) {
            long packedPos = buffer.readLong();
            int hash = buffer.readInt();
            int dataLength = buffer.readInt();

            if (dataLength > 0 && buffer.isReadable(dataLength)) {
                byte[] data = new byte[dataLength];
                buffer.readBytes(data);
                regionData.entries.put(packedPos, new ShapeEntry(hash, data));
            }
        }
    }

    @Override
    protected void writeRegionData(ByteBuf buffer, Map<Long, ShapeEntry> entries) {
        entries.forEach((packedPos, entry) -> {
            buffer.writeLong(packedPos);
            buffer.writeInt(entry.hash());
            buffer.writeInt(entry.data().length);
            buffer.writeBytes(entry.data());
        });
    }

    public ShapeRefC getShape(VxSectionPos pos, int contentHash) {
        RegionPos regionPos = getRegionPos(pos);
        RegionData<Long, ShapeEntry> region = loadedRegions.computeIfAbsent(regionPos, this::loadRegion);
        long packedPos = pack(pos);

        ShapeEntry entry = region.entries.get(packedPos);
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
                    region.entries.remove(packedPos);
                    region.dirty.set(true);
                }
            }
        }
        return null;
    }

    public void storeShape(VxSectionPos pos, int contentHash, ShapeRefC shape) {
        RegionPos regionPos = getRegionPos(pos);
        RegionData<Long, ShapeEntry> region = loadedRegions.computeIfAbsent(regionPos, this::loadRegion);

        byte[] shapeData;
        try (StringStream stringStream = new StringStream();
             StreamOutWrapper streamOut = new StreamOutWrapper(stringStream)) {
            shape.saveBinaryState(streamOut);
            shapeData = stringStream.str().getBytes();
        }

        if (shapeData.length > 0) {
            ShapeEntry newEntry = new ShapeEntry(contentHash, shapeData);
            ShapeEntry oldEntry = region.entries.put(pack(pos), newEntry);
            if (!newEntry.equals(oldEntry)) {
                region.dirty.set(true);
            }
        }
    }

    public void removeShape(VxSectionPos pos) {
        RegionPos regionPos = getRegionPos(pos);
        RegionData<Long, ShapeEntry> region = loadedRegions.computeIfAbsent(regionPos, this::loadRegion);
        if (region.entries.remove(pack(pos)) != null) {
            region.dirty.set(true);
        }
    }
}