/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.physics.constraint.persistence;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.timtaran.interactivemc.physics.init.VxMainClass;
import net.timtaran.interactivemc.physics.physics.constraint.VxConstraint;
import net.timtaran.interactivemc.physics.physics.constraint.manager.VxConstraintManager;
import net.timtaran.interactivemc.physics.physics.persistence.VxAbstractRegionStorage;
import net.timtaran.interactivemc.physics.physics.persistence.VxRegionIndex;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages persistent storage for physics constraints.
 * This class handles serialization, deserialization, and asynchronous loading/saving
 * of constraint data using a region-based file system.
 *
 * @author xI-Mx-Ix
 */
public class VxConstraintStorage extends VxAbstractRegionStorage<UUID, byte[]> {

    private final VxConstraintManager constraintManager;
    private final ConcurrentMap<Long, List<UUID>> chunkToUuidIndex = new ConcurrentHashMap<>();

    public VxConstraintStorage(ServerLevel level, VxConstraintManager constraintManager) {
        super(level, "constraint", "constraint");
        this.constraintManager = constraintManager;
    }

    @Override
    protected VxRegionIndex createRegionIndex() {
        return new VxRegionIndex(storagePath, "constraint");
    }

    @Override
    protected void readRegionData(ByteBuf buffer, RegionData<UUID, byte[]> regionData) {
        FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(buffer);
        while (friendlyBuf.isReadable()) {
            UUID id = friendlyBuf.readUUID();
            byte[] data = friendlyBuf.readByteArray();
            regionData.entries.put(id, data);
            indexConstraintData(id, data);
        }
    }

    @Override
    protected void writeRegionData(ByteBuf buffer, Map<UUID, byte[]> entries) {
        FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(buffer);
        for (Map.Entry<UUID, byte[]> entry : entries.entrySet()) {
            friendlyBuf.writeUUID(entry.getKey());
            friendlyBuf.writeByteArray(entry.getValue());
        }
    }

    /**
     * Stores a batch of pre-serialized constraint data snapshots, grouped by region.
     *
     * @param snapshotsByRegion A map where each key is a region position and the value is a map of snapshots for that region.
     */
    public void storeConstraintBatch(Map<RegionPos, Map<UUID, byte[]>> snapshotsByRegion) {
        snapshotsByRegion.forEach(this::storeConstraintBatch);
    }

    /**
     * Stores a batch of constraint snapshots for a single region. This schedules the actual
     * I/O operation to be executed on the I/O worker thread and triggers an async save.
     *
     * @param regionPos The position of the region.
     * @param snapshotBatch A map of constraint UUIDs to their serialized data.
     */
    public void storeConstraintBatch(RegionPos regionPos, Map<UUID, byte[]> snapshotBatch) {
        if (snapshotBatch == null || snapshotBatch.isEmpty()) {
            return;
        }

        getRegion(regionPos).thenAcceptAsync(region -> {
            for (Map.Entry<UUID, byte[]> entry : snapshotBatch.entrySet()) {
                UUID constraintId = entry.getKey();
                byte[] data = entry.getValue();

                region.entries.put(constraintId, data);
                regionIndex.put(constraintId, regionPos);
                indexConstraintData(constraintId, data);
            }
            region.dirty.set(true);
            // Trigger an asynchronous save for this region now, instead of waiting for a full flush.
            saveRegion(regionPos);
        }, ioExecutor).exceptionally(ex -> {
            VxMainClass.LOGGER.error("Failed to store constraint batch in region {}", regionPos, ex);
            return null;
        });
    }

    public void loadConstraintsInChunk(ChunkPos chunkPos) {
        RegionPos regionPos = new RegionPos(chunkPos.x >> 5, chunkPos.z >> 5);
        getRegion(regionPos).thenRunAsync(() -> {
            List<UUID> idsToLoad = chunkToUuidIndex.get(chunkPos.toLong());
            if (idsToLoad == null || idsToLoad.isEmpty()) return;
            for (UUID id : List.copyOf(idsToLoad)) {
                if (constraintManager.hasActiveConstraint(id) || constraintManager.getDataSystem().isPending(id)) continue;
                loadConstraint(id);
            }
        }, ioExecutor).exceptionally(ex -> {
            VxMainClass.LOGGER.error("Failed to load constraints in chunk {}", chunkPos, ex);
            return null;
        });
    }

    /**
     * Asynchronously loads a single constraint by its UUID.
     *
     * @param id The UUID of the constraint to load.
     */
    public void loadConstraint(UUID id) {
        RegionPos regionPos = regionIndex.get(id);
        if (regionPos == null) return;

        getRegion(regionPos)
                .thenApplyAsync(region -> {
                    byte[] data = region.entries.get(id);
                    return data != null ? deserializeConstraint(id, data) : null;
                }, ioExecutor)
                .thenAcceptAsync(constraint -> {
                    if (constraint != null) {
                        constraintManager.addConstraintFromStorage(constraint);
                    }
                }, level.getServer())
                .exceptionally(ex -> {
                    VxMainClass.LOGGER.error("Exception loading physics constraint {}", id, ex);
                    return null;
                });
    }

    /**
     * Removes a constraint's data from storage files asynchronously.
     *
     * @param id The UUID of the constraint to remove.
     */
    public void removeData(UUID id) {
        RegionPos regionPos = regionIndex.get(id);
        if (regionPos == null) return;

        getRegion(regionPos).thenAcceptAsync(region -> {
            byte[] data = region.entries.remove(id);
            if (data != null) {
                region.dirty.set(true);
                deIndexConstraint(id, data);
                regionIndex.remove(id);
            }
        }, ioExecutor).exceptionally(ex -> {
            VxMainClass.LOGGER.error("Failed to remove data for constraint {}", id, ex);
            return null;
        });
    }

    /**
     * Deserializes constraint data from a byte array using the VxConstraintCodec.
     */
    private VxConstraint deserializeConstraint(UUID id, byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            buf.readLong();
            return VxConstraintCodec.deserialize(id, buf);
        } finally {
            if (buf.refCnt() > 0) buf.release();
        }
    }

    /**
     * Serializes a constraint into a byte array using the VxConstraintCodec.
     */
    @Nullable
    public byte[] serializeConstraintData(VxConstraint constraint, ChunkPos pos) {
        ByteBuf buffer = Unpooled.buffer();
        FriendlyByteBuf buf = new FriendlyByteBuf(buffer);
        try {
            buf.writeLong(pos.toLong());
            VxConstraintCodec.serialize(constraint, buf);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return data;
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Error during constraint serialization for {}", constraint.getConstraintId(), e);
            return null;
        }
        finally {
            if (buffer.refCnt() > 0) buffer.release();
        }
    }

    private void indexConstraintData(UUID id, byte[] data) {
        long chunkKey = getChunkKeyFromData(data);
        chunkToUuidIndex.computeIfAbsent(chunkKey, k -> new CopyOnWriteArrayList<>()).add(id);
    }

    private void deIndexConstraint(UUID id, byte[] data) {
        long chunkKey = getChunkKeyFromData(data);
        List<UUID> idList = chunkToUuidIndex.get(chunkKey);
        if (idList != null) {
            idList.remove(id);
            if (idList.isEmpty()) {
                chunkToUuidIndex.remove(chunkKey);
            }
        }
    }

    private long getChunkKeyFromData(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            return buf.readLong();
        } finally {
            if (buf.refCnt() > 0) buf.release();
        }
    }
}