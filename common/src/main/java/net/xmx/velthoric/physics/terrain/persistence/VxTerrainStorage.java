/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.terrain.persistence;

import io.netty.buffer.ByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.persistence.VxAbstractRegionStorage;
import net.xmx.velthoric.physics.persistence.VxRegionIndex;
import net.xmx.velthoric.physics.terrain.chunk.VxSectionPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * Manages the persistent storage of terrain shape data on disk.
 * It uses a region-based file system to efficiently store and retrieve the geometric
 * data (a list of boxes) required to reconstruct a chunk's physics shape. All disk
 * operations are performed asynchronously.
 *
 * @author xI-Mx-Ix
 */
public class VxTerrainStorage extends VxAbstractRegionStorage<Long, VxTerrainStorage.ShapeEntry> {

    /**
     * A record representing the geometric data for a single box.
     */
    public record BoxData(float px, float py, float pz, float hx, float hy, float hz) {}

    /**
     * A record representing the stored shape data, which includes the content hash
     * and a list of all boxes that make up the shape.
     */
    public record ShapeEntry(int hash, List<BoxData> boxes) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ShapeEntry that = (ShapeEntry) o;
            return hash == that.hash && Objects.equals(boxes, that.boxes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hash, boxes);
        }
    }

    public VxTerrainStorage(ServerLevel level) {
        super(level, "terrain", "vx_terrain_geom"); // Changed suffix to avoid conflicts with old format
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
            int boxCount = buffer.readInt();

            if (boxCount > 0 && buffer.isReadable(boxCount * 6 * 4)) {
                List<BoxData> boxes = new ArrayList<>(boxCount);
                for (int i = 0; i < boxCount; i++) {
                    boxes.add(new BoxData(
                            buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), // position
                            buffer.readFloat(), buffer.readFloat(), buffer.readFloat()  // half-extents
                    ));
                }
                regionData.entries.put(packedPos, new ShapeEntry(hash, boxes));
            }
        }
    }

    @Override
    protected void writeRegionData(ByteBuf buffer, Map<Long, ShapeEntry> entries) {
        entries.forEach((packedPos, entry) -> {
            buffer.writeLong(packedPos);
            buffer.writeInt(entry.hash());
            buffer.writeInt(entry.boxes().size());
            for (BoxData box : entry.boxes()) {
                buffer.writeFloat(box.px());
                buffer.writeFloat(box.py());
                buffer.writeFloat(box.pz());
                buffer.writeFloat(box.hx());
                buffer.writeFloat(box.hy());
                buffer.writeFloat(box.hz());
            }
        });
    }

    /**
     * Retrieves the geometric data for a shape from disk storage if its content hash matches.
     *
     * @param pos         The position of the chunk section.
     * @param contentHash The expected hash of the chunk's content.
     * @return The list of box data, or null if not found or if the hash mismatches.
     */
    public List<BoxData> getShapeData(VxSectionPos pos, int contentHash) {
        RegionPos regionPos = getRegionPos(pos);
        long packedPos = pack(pos);

        try {
            RegionData<Long, ShapeEntry> region = getRegion(regionPos).get();
            ShapeEntry entry = region.entries.get(packedPos);
            if (entry != null && entry.hash() == contentHash) {
                return entry.boxes();
            }
        } catch (InterruptedException | ExecutionException e) {
            VxMainClass.LOGGER.error("Failed to get shape region for {}", pos, e);
            Thread.currentThread().interrupt();
        }
        return null;
    }

    /**
     * Asynchronously stores geometric shape data to disk.
     *
     * @param pos         The position of the chunk section.
     * @param contentHash The hash of the chunk's content.
     * @param boxes       The list of box data to store.
     */
    public void storeShapeData(VxSectionPos pos, int contentHash, List<BoxData> boxes) {
        if (boxes.isEmpty()) {
            removeShape(pos);
            return;
        }

        RegionPos regionPos = getRegionPos(pos);
        ShapeEntry newEntry = new ShapeEntry(contentHash, boxes);

        getRegion(regionPos).thenAccept(region -> {
            ShapeEntry oldEntry = region.entries.put(pack(pos), newEntry);
            if (!newEntry.equals(oldEntry)) {
                region.dirty.set(true);
            }
        });
    }

    /**
     * Asynchronously removes shape data from disk storage.
     *
     * @param pos The position of the chunk section whose shape data should be removed.
     */
    public void removeShape(VxSectionPos pos) {
        RegionPos regionPos = getRegionPos(pos);
        getRegion(regionPos).thenAccept(region -> {
            if (region.entries.remove(pack(pos)) != null) {
                region.dirty.set(true);
            }
        });
    }
}