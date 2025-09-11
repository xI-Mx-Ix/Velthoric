/*
This file is part of Velthoric.
Licensed under LGPL 3.0.
*/
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
import net.xmx.velthoric.physics.persistence.VxRegionIndex;
import net.xmx.velthoric.physics.terrain.VxSectionPos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

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

    @Override
    protected VxRegionIndex createRegionIndex() {
        return null;
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
            int compressedDataLength = buffer.readInt();
            int uncompressedDataLength = buffer.readInt();

            if (compressedDataLength > 0 && buffer.isReadable(compressedDataLength)) {
                byte[] compressedData = new byte[compressedDataLength];
                buffer.readBytes(compressedData);

                Inflater inflater = new Inflater();
                inflater.setInput(compressedData);
                byte[] uncompressedData = new byte[uncompressedDataLength];
                try {
                    inflater.inflate(uncompressedData);
                    regionData.entries.put(packedPos, new ShapeEntry(hash, uncompressedData));
                } catch (DataFormatException e) {
                    VxMainClass.LOGGER.error("Failed to decompress shape data for {}", packedPos, e);
                } finally {
                    inflater.end();
                }
            }
        }
    }

    @Override
    protected void writeRegionData(ByteBuf buffer, Map<Long, ShapeEntry> entries) {
        Deflater deflater = new Deflater();
        byte[] compressionBuffer = new byte[4096];

        try {
            entries.forEach((packedPos, entry) -> {
                deflater.setInput(entry.data());
                deflater.finish();

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream(entry.data().length);
                while (!deflater.finished()) {
                    int count = deflater.deflate(compressionBuffer);
                    outputStream.write(compressionBuffer, 0, count);
                }
                byte[] compressedData = outputStream.toByteArray();
                deflater.reset();

                buffer.writeLong(packedPos);
                buffer.writeInt(entry.hash());
                buffer.writeInt(compressedData.length);
                buffer.writeInt(entry.data().length);
                buffer.writeBytes(compressedData);
            });
        } finally {
            deflater.end();
        }
    }

    public ShapeRefC getShape(VxSectionPos pos, int contentHash) {
        RegionPos regionPos = getRegionPos(pos);
        long packedPos = pack(pos);

        try {

            RegionData<Long, ShapeEntry> region = getRegion(regionPos).get();
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
        } catch (InterruptedException | ExecutionException e) {
            VxMainClass.LOGGER.error("Failed to get shape region for {}", pos, e);
            Thread.currentThread().interrupt();
        }
        return null;
    }

    public void storeShape(VxSectionPos pos, int contentHash, ShapeRefC shape) {
        RegionPos regionPos = getRegionPos(pos);

        byte[] shapeData;
        try (StringStream stringStream = new StringStream();
             StreamOutWrapper streamOut = new StreamOutWrapper(stringStream)) {
            shape.saveBinaryState(streamOut);
            shapeData = stringStream.str().getBytes();
        }

        if (shapeData.length > 0) {
            ShapeEntry newEntry = new ShapeEntry(contentHash, shapeData);
            getRegion(regionPos).thenAccept(region -> {
                ShapeEntry oldEntry = region.entries.put(pack(pos), newEntry);
                if (!newEntry.equals(oldEntry)) {
                    region.dirty.set(true);
                }
            });
        }
    }

    public void removeShape(VxSectionPos pos) {
        RegionPos regionPos = getRegionPos(pos);
        getRegion(regionPos).thenAccept(region -> {
            if (region.entries.remove(pack(pos)) != null) {
                region.dirty.set(true);
            }
        });
    }
}