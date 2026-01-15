/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.persistence;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.body.manager.VxBodyManager;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.persistence.VxAbstractRegionStorage;
import net.xmx.velthoric.physics.persistence.VxIOProcessor;
import net.xmx.velthoric.physics.persistence.VxRegionIndex;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the persistent storage of physics bodies using the optimized Hot-Map architecture.
 * <p>
 * <b>Performance:</b>
 * Operations like {@link #storeBody} and {@link #removeData} are now instant memory operations.
 * This resolves the performance bottleneck when removing massive amounts of bodies (e.g. 20k) via commands.
 *
 * @author xI-Mx-Ix
 */
public class VxBodyStorage extends VxAbstractRegionStorage<UUID, byte[]> {

    private final VxBodyManager bodyManager;

    /**
     * Auxiliary index mapping ChunkPos(long) -> Set of UUIDs.
     * This is required to efficiently implement {@link #loadBodiesInChunk(ChunkPos)}.
     * It is kept in sync with the main storage map.
     */
    private final Long2ObjectMap<Set<UUID>> chunkToUuidIndex = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());

    /**
     * Set of UUIDs currently queued for loading to prevent duplicate requests.
     */
    private final Set<UUID> pendingLoads = ConcurrentHashMap.newKeySet();

    public VxBodyStorage(ServerLevel level, VxBodyManager bodyManager) {
        super(level, "body", "body");
        this.bodyManager = bodyManager;
    }

    @Override
    protected VxRegionIndex createRegionIndex(VxIOProcessor processor) {
        return new VxRegionIndex(storagePath, "body", processor);
    }

    /**
     * Stores a body instantly in the hot map.
     * <p>
     * The body data is serialized on the calling thread (usually the Server Thread or Physics Thread),
     * and the result is placed into the in-memory map. The actual disk write is deferred to the next flush cycle.
     *
     * @param body The body to store.
     */
    public void storeBody(VxBody body) {
        if (body == null || body.getDataStoreIndex() == -1) return;

        // Perform serialization immediately to capture current state
        byte[] data = serializeBodyData(body);
        if (data == null) return;

        ChunkPos chunkPos = bodyManager.getBodyChunkPos(body.getDataStoreIndex());
        RegionPos regionPos = new RegionPos(chunkPos.x >> 5, chunkPos.z >> 5);

        // Instant RAM Update (marks region dirty)
        putInMemory(body.getPhysicsId(), data, regionPos);

        // Update auxiliary chunk index
        updateChunkIndex(body.getPhysicsId(), chunkPos.toLong());
    }

    /**
     * Removes a body instantly from the hot map.
     * <p>
     * This method executes in nanoseconds, enabling massive bulk removals without lag.
     * The persistence index is updated immediately in RAM, but the file on disk remains unchanged
     * until the next save cycle.
     *
     * @param id The UUID of the body to remove.
     */
    public void removeData(UUID id) {
        // Instant RAM Remove
        removeInMemory(id);

        // Note: We do not eagerly iterate chunkToUuidIndex to remove the ID here for performance reasons.
        // It is a lazy index; if we try to load this ID later, the load will fail gracefully (returns null).
    }

    /**
     * Bulk storage method, typically used when chunks are unloaded.
     * Updates the RAM state for multiple bodies at once.
     *
     * @param snapshotsByRegion Map of regions to body data.
     */
    public void storeBodyBatch(Map<RegionPos, Map<UUID, byte[]>> snapshotsByRegion) {
        if (snapshotsByRegion == null) return;

        snapshotsByRegion.forEach((regionPos, batch) -> {
            for (Map.Entry<UUID, byte[]> entry : batch.entrySet()) {
                putInMemory(entry.getKey(), entry.getValue(), regionPos);
                // Note: Assuming chunkToUuidIndex is managed by individual body updates or load logic.
            }
        });
    }

    /**
     * Triggers the loading of all bodies within a specific chunk.
     *
     * @param chunkPos The position of the chunk.
     */
    public void loadBodiesInChunk(ChunkPos chunkPos) {
        RegionPos regionPos = new RegionPos(chunkPos.x >> 5, chunkPos.z >> 5);

        // Ensure the entire region is loaded into RAM (async I/O if needed)
        loadRegionAsync(regionPos).thenRun(() -> {
            Set<UUID> ids = chunkToUuidIndex.get(chunkPos.toLong());
            if (ids == null || ids.isEmpty()) return;

            for (UUID id : ids) {
                // If body not active and not currently loading, trigger load
                if (bodyManager.getVxBody(id) == null && pendingLoads.add(id)) {
                    loadBody(id);
                }
            }
        }).exceptionally(e -> {
            VxMainClass.LOGGER.error("Error loading chunk bodies {}", chunkPos, e);
            return null;
        });
    }

    /**
     * Instantiates a body from the hot map.
     *
     * @param id The UUID of the body.
     */
    public void loadBody(UUID id) {
        // Try get directly from RAM (Instant if region loaded)
        byte[] data = getFromMemory(id);

        if (data != null) {
            instantiateBody(data);
            pendingLoads.remove(id);
        } else {
            // Edge case: Index says body exists, but region wasn't loaded?
            // Trigger load to be safe.
            if (regionIndex != null) {
                RegionPos pos = regionIndex.get(id);
                if (pos != null) {
                    loadRegionAsync(pos).thenAccept(map -> {
                        byte[] deferredData = map.get(id);
                        if (deferredData != null) instantiateBody(deferredData);
                        pendingLoads.remove(id);
                    });
                } else {
                    pendingLoads.remove(id);
                }
            }
        }
    }

    /**
     * Deserializes and adds the body to the physics world on the main thread.
     */
    private void instantiateBody(byte[] data) {
        bodyManager.getPhysicsWorld().execute(() -> {
            VxSerializedBodyData sbd = deserializeBody(data);
            if (sbd != null) {
                bodyManager.addSerializedBody(sbd);
            }
        });
    }

    // --- Internal Helpers ---

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

            // Rebuild auxiliary index on load
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

    private VxSerializedBodyData deserializeBody(byte[] data) {
        VxByteBuf buf = new VxByteBuf(Unpooled.wrappedBuffer(data));
        try {
            return VxBodyCodec.deserialize(buf);
        } finally {
            if (buf.refCnt() > 0) buf.release();
        }
    }

    /**
     * Serializes a body into a byte array for persistence.
     * <p>
     * This method is public to allow the manager to create snapshots on the main thread
     * before passing them to the batched storage system.
     *
     * @param body The body to serialize.
     * @return The byte array containing the serialized body data, or null if serialization failed.
     */
    public byte @Nullable [] serializeBodyData(VxBody body) {
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(256);
        VxByteBuf friendlyBuf = new VxByteBuf(buffer);
        try {
            VxBodyCodec.serialize(body, friendlyBuf);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return data;
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Error during body serialization for {}", body.getPhysicsId(), e);
            return null;
        } finally {
            if (buffer.refCnt() > 0) buffer.release();
        }
    }

    private long getChunkKeyFromData(byte[] data) {
        // Quick peek logic to get position without full deserialization
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            buf.readUUID();
            buf.readUtf();
            double x = buf.readDouble(); // pos x
            buf.readDouble(); // y
            double z = buf.readDouble(); // pos z
            // Calculate chunk key
            return ChunkPos.asLong((int) x >> 4, (int) z >> 4);
        } catch (Exception e) {
            return 0;
        } finally {
            if (buf.refCnt() > 0) buf.release();
        }
    }
}