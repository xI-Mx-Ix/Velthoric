/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.constraint.persistence;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.constraint.VxConstraint;
import net.xmx.velthoric.physics.constraint.manager.VxConstraintManager;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.persistence.VxAbstractRegionStorage;
import net.xmx.velthoric.physics.persistence.VxRegionIndex;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages persistent storage for physics constraints.
 * This class handles serialization, deserialization, and asynchronous loading/saving
 * of constraint data using a region-based file system. It uses a codec to separate
 * serialization logic from storage management.
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
     * Stores a collection of constraints by grouping them by region and saving each region in a single batch operation.
     *
     * @param constraints The constraints to store.
     */
    public void storeConstraints(Collection<VxConstraint> constraints) {
        if (constraints == null || constraints.isEmpty()) return;

        Map<RegionPos, List<VxConstraint>> constraintsByRegion = new HashMap<>();
        for (VxConstraint constraint : constraints) {
            if (constraint == null) continue;
            VxBody body1 = constraintManager.getObjectManager().getObject(constraint.getBody1Id());
            if (body1 != null) {
                int index = body1.getBodyHandle().getDataStoreIndex();
                if (index == -1) continue;
                ChunkPos chunkPos = constraintManager.getObjectManager().getObjectChunkPos(index);
                RegionPos regionPos = new RegionPos(chunkPos.x >> 5, chunkPos.z >> 5);
                constraintsByRegion.computeIfAbsent(regionPos, k -> new ArrayList<>()).add(constraint);
            }
        }

        constraintsByRegion.forEach((regionPos, regionConstraints) -> {
            getRegion(regionPos).thenAcceptAsync(region -> {
                for (VxConstraint constraint : regionConstraints) {
                    VxBody body1 = constraintManager.getObjectManager().getObject(constraint.getBody1Id());
                    if (body1 == null || body1.getBodyHandle().getDataStoreIndex() == -1) continue;

                    ChunkPos chunkPos = constraintManager.getObjectManager().getObjectChunkPos(body1.getBodyHandle().getDataStoreIndex());
                    byte[] data = serializeConstraintData(constraint, chunkPos);
                    region.entries.put(constraint.getConstraintId(), data);
                    regionIndex.put(constraint.getConstraintId(), regionPos);
                    indexConstraintData(constraint.getConstraintId(), data);
                }
                region.dirty.set(true);
            }, ioExecutor).exceptionally(ex -> {
                VxMainClass.LOGGER.error("Failed to store constraint batch in region {}", regionPos, ex);
                return null;
            });
        });
    }

    /**
     * Stores a single constraint. For better performance, use storeConstraints when saving multiple constraints.
     *
     * @param constraint The constraint to store.
     */
    public void storeConstraint(VxConstraint constraint) {
        if (constraint == null) return;
        storeConstraints(Collections.singletonList(constraint));
    }

    /**
     * Initiates loading for all constraints associated with a given chunk.
     *
     * @param chunkPos The position of the chunk to load constraints for.
     */
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
        CompletableFuture.supplyAsync(() -> {
            RegionPos regionPos = regionIndex.get(id);
            if (regionPos == null) return null;

            RegionData<UUID, byte[]> region = getRegion(regionPos).join();
            byte[] data = region.entries.get(id);
            if (data != null) {
                return deserializeConstraint(id, data);
            }
            return null;
        }, ioExecutor).thenAcceptAsync(constraint -> {
            if (constraint != null) {
                constraintManager.addConstraintFromStorage(constraint);
            }
        }, level.getServer()).exceptionally(ex -> {
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
     *
     * @param id   The UUID of the constraint.
     * @param data The raw byte data.
     * @return The deserialized VxConstraint, or null on failure.
     */
    private VxConstraint deserializeConstraint(UUID id, byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            buf.readLong(); // Skip chunk key, which is storage-specific metadata.
            return VxConstraintCodec.deserialize(id, buf);
        } finally {
            if (buf.refCnt() > 0) {
                buf.release();
            }
        }
    }

    /**
     * Serializes a constraint into a byte array using the VxConstraintCodec.
     *
     * @param constraint The constraint to serialize.
     * @param pos        The chunk position, used as storage-specific metadata.
     * @return The serialized byte array.
     */
    private byte[] serializeConstraintData(VxConstraint constraint, ChunkPos pos) {
        ByteBuf buffer = Unpooled.buffer();
        FriendlyByteBuf buf = new FriendlyByteBuf(buffer);
        try {
            buf.writeLong(pos.toLong()); // Write storage-specific metadata first.
            VxConstraintCodec.serialize(constraint, buf);

            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return data;
        } finally {
            if (buffer.refCnt() > 0) {
                buffer.release();
            }
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
            if (buf.refCnt() > 0) {
                buf.release();
            }
        }
    }
}