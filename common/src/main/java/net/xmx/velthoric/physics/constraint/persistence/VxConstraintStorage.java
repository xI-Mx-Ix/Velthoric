/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.constraint.persistence;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.constraint.VxConstraint;
import net.xmx.velthoric.physics.constraint.manager.VxConstraintManager;
import net.xmx.velthoric.physics.persistence.VxAbstractRegionStorage;
import net.xmx.velthoric.physics.persistence.VxIOProcessor;
import net.xmx.velthoric.physics.persistence.VxRegionIndex;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optimized storage system for physics constraints.
 * <p>
 * This class inherits the "Hot Map" capabilities from {@link VxAbstractRegionStorage},
 * allowing for instant updates and removals of constraints in memory.
 *
 * @author xI-Mx-Ix
 */
public class VxConstraintStorage extends VxAbstractRegionStorage<UUID, byte[]> {

    private final VxConstraintManager constraintManager;
    private final Long2ObjectMap<Set<UUID>> chunkToUuidIndex = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());

    public VxConstraintStorage(ServerLevel level, VxConstraintManager constraintManager) {
        super(level, "constraint", "constraint");
        this.constraintManager = constraintManager;
    }

    @Override
    protected VxRegionIndex createRegionIndex(VxIOProcessor processor) {
        return new VxRegionIndex(storagePath, "constraint", processor);
    }

    /**
     * Instantly removes constraint data from memory.
     * Persistence is deferred.
     *
     * @param id The UUID of the constraint.
     */
    public void removeData(UUID id) {
        removeInMemory(id);
    }

    /**
     * Stores a batch of constraints instantly in memory.
     *
     * @param snapshotsByRegion Map of region pos -> constraint data.
     */
    public void storeConstraintBatch(Map<RegionPos, Map<UUID, byte[]>> snapshotsByRegion) {
        if (snapshotsByRegion == null) return;

        snapshotsByRegion.forEach((pos, batch) -> {
            for (Map.Entry<UUID, byte[]> entry : batch.entrySet()) {
                putInMemory(entry.getKey(), entry.getValue(), pos);
                updateChunkIndex(entry.getKey(), getChunkKeyFromData(entry.getValue()));
            }
        });
    }

    /**
     * Serializes a constraint into a byte array for persistence.
     * <p>
     * This method is exposed so the manager can snapshot constraints safely on the physics thread
     * before handing them off to the storage system.
     *
     * @param constraint The constraint to serialize.
     * @param pos        The chunk position associated with this constraint.
     * @return The serialized data byte array.
     */
    public byte[] serializeConstraintData(VxConstraint constraint, ChunkPos pos) {
        ByteBuf buffer = Unpooled.buffer();
        FriendlyByteBuf buf = new FriendlyByteBuf(buffer);
        try {
            buf.writeLong(pos.toLong());
            VxConstraintCodec.serialize(constraint, buf);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return data;
        } finally {
            if (buffer.refCnt() > 0) buffer.release();
        }
    }

    /**
     * Loads all constraints for a specific chunk into memory and instantiates them.
     *
     * @param chunkPos The position of the chunk.
     */
    public void loadConstraintsInChunk(ChunkPos chunkPos) {
        RegionPos regionPos = new RegionPos(chunkPos.x >> 5, chunkPos.z >> 5);

        loadRegionAsync(regionPos).thenRun(() -> {
            Set<UUID> ids = chunkToUuidIndex.get(chunkPos.toLong());
            if (ids == null) return;

            for (UUID id : ids) {
                if (!constraintManager.hasActiveConstraint(id)) {
                    loadConstraint(id);
                }
            }
        }).exceptionally(e -> {
            VxMainClass.LOGGER.error("Failed to load constraints in chunk {}", chunkPos, e);
            return null;
        });
    }

    private void loadConstraint(UUID id) {
        byte[] data = getFromMemory(id);
        if (data != null) {
            instantiateConstraint(id, data);
        }
    }

    private void instantiateConstraint(UUID id, byte[] data) {
        constraintManager.getBodyManager().getPhysicsWorld().execute(() -> {
            try {
                // Skip the ChunkKey header (long)
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
                buf.readLong();

                VxConstraint constraint = VxConstraintCodec.deserialize(id, buf);
                constraintManager.addConstraintFromStorage(constraint);
                buf.release();
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Failed to deserialize constraint {}", id, e);
            }
        });
    }

    private void updateChunkIndex(UUID id, long chunkKey) {
        chunkToUuidIndex.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet()).add(id);
    }

    @Override
    protected void readRegionData(ByteBuf buffer, Map<UUID, byte[]> target) {
        FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(buffer);
        while (friendlyBuf.isReadable()) {
            UUID id = friendlyBuf.readUUID();
            byte[] data = friendlyBuf.readByteArray();
            target.put(id, data);

            long chunkKey = getChunkKeyFromData(data);
            updateChunkIndex(id, chunkKey);
        }
    }

    @Override
    protected void writeRegionData(ByteBuf buffer, Map<UUID, byte[]> source) {
        FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(buffer);
        for (Map.Entry<UUID, byte[]> entry : source.entrySet()) {
            friendlyBuf.writeUUID(entry.getKey());
            friendlyBuf.writeByteArray(entry.getValue());
        }
    }

    private long getChunkKeyFromData(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            return buf.readLong();
        } catch (Exception e) {
            return 0;
        } finally {
            if (buf.refCnt() > 0) buf.release();
        }
    }
}