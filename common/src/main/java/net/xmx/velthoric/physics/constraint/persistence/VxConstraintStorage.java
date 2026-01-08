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
import net.xmx.velthoric.physics.persistence.VxRegionIndex;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages persistent storage for physics constraints.
 * <p>
 * This class extends the {@link VxAbstractRegionStorage} to provide a robust, region-based
 * storage solution for constraints. It ensures that constraints are loaded and saved
 * asynchronously via the I/O worker infrastructure, preventing main-thread blocking
 * and race conditions.
 * <p>
 * Optimized to use {@link Long2ObjectMap} and Concurrent Sets for efficient index management
 * without the overhead of array copying.
 *
 * @author xI-Mx-Ix
 */
public class VxConstraintStorage extends VxAbstractRegionStorage<UUID, byte[]> {

    private final VxConstraintManager constraintManager;

    /**
     * An index mapping a Chunk Position (as a long key) to a Set of Constraint UUIDs contained within that chunk.
     * <p>
     * Implementation details:
     * <ul>
     *     <li>Uses {@link Long2ObjectMap} (fastutil) to avoid boxing overhead for chunk keys.</li>
     *     <li>The outer map is synchronized to handle concurrent chunk access.</li>
     *     <li>The values are {@link java.util.concurrent.ConcurrentHashMap#newKeySet()}, allowing
     *     O(1) concurrent adds/removes.</li>
     * </ul>
     */
    private final Long2ObjectMap<Set<UUID>> chunkToUuidIndex = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());

    public VxConstraintStorage(ServerLevel level, VxConstraintManager constraintManager) {
        super(level, "constraint", "constraint");
        this.constraintManager = constraintManager;
    }

    @Override
    protected VxRegionIndex createRegionIndex() {
        return new VxRegionIndex(storagePath, "constraint");
    }

    @Override
    protected void readRegionData(ByteBuf buffer, Map<UUID, byte[]> regionData) {
        FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(buffer);
        while (friendlyBuf.isReadable()) {
            UUID id = friendlyBuf.readUUID();
            byte[] data = friendlyBuf.readByteArray();

            regionData.put(id, data);
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
     * @return A future completing when all constraints are successfully queued for storage.
     */
    public CompletableFuture<Void> storeConstraintBatch(Map<RegionPos, Map<UUID, byte[]>> snapshotsByRegion) {
        if (snapshotsByRegion == null || snapshotsByRegion.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        snapshotsByRegion.forEach((pos, batch) -> futures.add(storeConstraintBatch(pos, batch)));
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Stores a batch of constraint snapshots for a single region.
     * <p>
     * Updates the in-memory region map and triggers a write-behind save via the I/O worker.
     *
     * @param regionPos     The position of the region.
     * @param snapshotBatch A map of constraint UUIDs to their serialized data.
     * @return A future tracking the insertion operation.
     */
    public CompletableFuture<Void> storeConstraintBatch(RegionPos regionPos, Map<UUID, byte[]> snapshotBatch) {
        if (snapshotBatch == null || snapshotBatch.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        // 1. Ensure region is loaded
        return getRegion(regionPos).thenCompose(map -> {
            // 2. Update memory state
            for (Map.Entry<UUID, byte[]> entry : snapshotBatch.entrySet()) {
                UUID constraintId = entry.getKey();
                byte[] data = entry.getValue();

                map.put(constraintId, data);

                if (regionIndex != null) regionIndex.put(constraintId, regionPos);
                indexConstraintData(constraintId, data);
            }

            // 3. Trigger persistence
            return saveRegion(regionPos);
        }).exceptionally(ex -> {
            VxMainClass.LOGGER.error("Failed to store constraint batch in region {}", regionPos, ex);
            return null;
        });
    }

    /**
     * Triggers the loading of constraints associated with a specific chunk.
     *
     * @param chunkPos The position of the chunk.
     */
    public void loadConstraintsInChunk(ChunkPos chunkPos) {
        RegionPos regionPos = new RegionPos(chunkPos.x >> 5, chunkPos.z >> 5);

        getRegion(regionPos).thenAccept(map -> {
            Set<UUID> idsToLoad = chunkToUuidIndex.get(chunkPos.toLong());
            if (idsToLoad == null || idsToLoad.isEmpty()) return;

            // Iterate over the concurrent set directly.
            // ConcurrentKeySet iterator is safe for this operation.
            for (UUID id : idsToLoad) {
                // Check if already active or pending in the data system
                if (constraintManager.hasActiveConstraint(id) || constraintManager.getDataSystem().isPending(id)) continue;

                loadConstraint(id);
            }
        }).exceptionally(ex -> {
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
        if (regionIndex == null) return;
        RegionPos regionPos = regionIndex.get(id);
        if (regionPos == null) return;

        getRegion(regionPos)
                .thenApply(map -> {
                    byte[] data = map.get(id);
                    return data != null ? deserializeConstraint(id, data) : null;
                })
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
     * Removes a constraint's data from persistent storage.
     *
     * @param id The UUID of the constraint to remove.
     */
    public void removeData(UUID id) {
        if (regionIndex == null) return;
        RegionPos regionPos = regionIndex.get(id);
        if (regionPos == null) return;

        getRegion(regionPos).thenCompose(map -> {
            byte[] data = map.remove(id);
            if (data != null) {
                deIndexConstraint(id, data);
                regionIndex.remove(id);
                return saveRegion(regionPos);
            }
            return CompletableFuture.completedFuture(null);
        }).exceptionally(ex -> {
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
            buf.readLong(); // Read chunk key header
            return VxConstraintCodec.deserialize(id, buf);
        } finally {
            if (buf.refCnt() > 0) buf.release();
        }
    }

    /**
     * Serializes a constraint into a byte array using the VxConstraintCodec.
     */
    public byte @Nullable [] serializeConstraintData(VxConstraint constraint, ChunkPos pos) {
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
        } finally {
            if (buffer.refCnt() > 0) buffer.release();
        }
    }

    /**
     * Updates the chunk-to-UUID index for a specific constraint.
     * <p>
     * Uses atomic computation to add the UUID to a thread-safe Set, replacing the slow CopyOnWrite list logic.
     *
     * @param id   The UUID of the constraint.
     * @param data The serialized data containing the chunk key.
     */
    private void indexConstraintData(UUID id, byte[] data) {
        long chunkKey = getChunkKeyFromData(data);
        chunkToUuidIndex.computeIfAbsent(chunkKey, (long k) -> ConcurrentHashMap.newKeySet()).add(id);
    }

    /**
     * Removes a constraint from the chunk-to-UUID index.
     * <p>
     * Atomically removes the UUID and cleans up the map entry if the set is empty.
     *
     * @param id   The UUID of the constraint.
     * @param data The serialized data containing the chunk key.
     */
    private void deIndexConstraint(UUID id, byte[] data) {
        long chunkKey = getChunkKeyFromData(data);
        chunkToUuidIndex.computeIfPresent(chunkKey, (key, set) -> {
            set.remove(id);
            return set.isEmpty() ? null : set;
        });
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