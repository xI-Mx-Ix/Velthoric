/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.persistence;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.body.manager.VxBodyDataStore;
import net.xmx.velthoric.physics.body.manager.VxBodyManager;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.persistence.VxAbstractRegionStorage;
import net.xmx.velthoric.physics.persistence.VxRegionIndex;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the persistent storage of physics bodies on disk using a region-based file system.
 * This class handles serialization, deserialization, and asynchronous loading/saving of body data.
 * It uses a codec to separate serialization logic from storage management.
 *
 * @author xI-Mx-Ix
 */
public class VxBodyStorage extends VxAbstractRegionStorage<UUID, byte[]> {
    private final VxBodyManager bodyManager;
    private final VxBodyDataStore dataStore;
    private final ConcurrentMap<Long, List<UUID>> chunkToUuidIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CompletableFuture<VxBody>> pendingLoads = new ConcurrentHashMap<>();

    public VxBodyStorage(ServerLevel level, VxBodyManager bodyManager) {
        super(level, "body", "body");
        this.bodyManager = bodyManager;
        this.dataStore = bodyManager.getDataStore();
    }

    @Override
    protected VxRegionIndex createRegionIndex() {
        return new VxRegionIndex(storagePath, "body");
    }

    @Override
    protected void readRegionData(ByteBuf buffer, RegionData<UUID, byte[]> regionData) {
        FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(buffer);
        while (friendlyBuf.isReadable()) {
            UUID id = friendlyBuf.readUUID();
            byte[] data = friendlyBuf.readByteArray();
            regionData.entries.put(id, data);
            indexBodyData(id, data);
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
     * Stores a single body. This is a convenience method that wraps the batch-saving logic.
     *
     * @param body The body to store.
     */
    public void storeBody(VxBody body) {
        if (body == null || body.getDataStoreIndex() == -1) return;

        bodyManager.getPhysicsWorld().execute(() -> {
            int index = body.getDataStoreIndex();
            if (index == -1) return;

            byte[] snapshot = serializeBodyData(body, index);
            if (snapshot == null) return;

            ChunkPos chunkPos = bodyManager.getBodyChunkPos(index);
            RegionPos regionPos = new RegionPos(chunkPos.x >> 5, chunkPos.z >> 5);
            storeBodyBatch(regionPos, Map.of(body.getPhysicsId(), snapshot));
        });
    }

    /**
     * Stores a batch of pre-serialized body data snapshots, grouped by region.
     * This is the main entry point for batched saving operations.
     *
     * @param snapshotsByRegion A map where each key is a region position and the value is a map of body snapshots for that region.
     */
    public void storeBodyBatch(Map<RegionPos, Map<UUID, byte[]>> snapshotsByRegion) {
        snapshotsByRegion.forEach(this::storeBodyBatch);
    }

    /**
     * Stores a batch of body snapshots for a single region. This schedules the actual
     * I/O operation to be executed on the I/O worker thread.
     *
     * @param regionPos The position of the region where the data should be stored.
     * @param snapshotBatch A map of body UUIDs to their serialized data for this region.
     */
    public void storeBodyBatch(RegionPos regionPos, Map<UUID, byte[]> snapshotBatch) {
        if (snapshotBatch == null || snapshotBatch.isEmpty()) {
            return;
        }

        getRegion(regionPos).thenAcceptAsync(region -> {
            for (Map.Entry<UUID, byte[]> entry : snapshotBatch.entrySet()) {
                UUID bodyId = entry.getKey();
                byte[] data = entry.getValue();

                region.entries.put(bodyId, data);
                regionIndex.put(bodyId, regionPos);
                indexBodyData(bodyId, data);
            }
            region.dirty.set(true);
        }, ioExecutor).exceptionally(ex -> {
            VxMainClass.LOGGER.error("Failed to queue body batch for storage in region {}", regionPos, ex);
            return null;
        });
    }

    public void loadBodiesInChunk(ChunkPos chunkPos) {
        RegionPos regionPos = new RegionPos(chunkPos.x >> 5, chunkPos.z >> 5);
        getRegion(regionPos).thenRunAsync(() -> {
            List<UUID> idsToLoad = chunkToUuidIndex.get(chunkPos.toLong());
            if (idsToLoad == null || idsToLoad.isEmpty()) return;

            for (UUID id : List.copyOf(idsToLoad)) {
                if (bodyManager.getVxBody(id) != null || pendingLoads.containsKey(id)) {
                    continue;
                }
                loadBody(id);
            }
        }, ioExecutor).exceptionally(ex -> {
            VxMainClass.LOGGER.error("Failed to load bodies in chunk {}", chunkPos, ex);
            return null;
        });
    }

    public CompletableFuture<VxBody> loadBody(UUID id) {
        VxBody existingBody = bodyManager.getVxBody(id);
        if (existingBody != null) {
            return CompletableFuture.completedFuture(existingBody);
        }
        return pendingLoads.computeIfAbsent(id, this::loadBodyAsync);
    }

    private CompletableFuture<VxBody> loadBodyAsync(UUID id) {
        RegionPos regionPos = regionIndex.get(id);
        if (regionPos == null) {
            return CompletableFuture.completedFuture(null);
        }

        return getRegion(regionPos)
                .thenApplyAsync(region -> region.entries.get(id), ioExecutor)
                .thenApplyAsync(this::deserializeBody, ioExecutor)
                .thenComposeAsync(data -> {
                    if (data == null) {
                        return CompletableFuture.completedFuture(null);
                    }
                    CompletableFuture<VxBody> bodyFuture = new CompletableFuture<>();
                    bodyManager.getPhysicsWorld().execute(() -> {
                        try {
                            VxBody body = bodyManager.addSerializedBody(data);
                            bodyFuture.complete(body);
                        } catch (Exception e) {
                            bodyFuture.completeExceptionally(e);
                        }
                    });
                    return bodyFuture;
                })
                .whenComplete((body, ex) -> {
                    if (ex != null) {
                        VxMainClass.LOGGER.error("Exception loading physics body {}", id, ex);
                    }
                    pendingLoads.remove(id);
                });
    }

    public void removeData(UUID id) {
        RegionPos regionPos = regionIndex.get(id);
        if (regionPos == null) return;

        getRegion(regionPos).thenAcceptAsync(region -> {
            byte[] data = region.entries.remove(id);
            if (data != null) {
                region.dirty.set(true);
                deIndexBody(id, data);
                regionIndex.remove(id);
            }
        }, ioExecutor).exceptionally(ex -> {
            VxMainClass.LOGGER.error("Failed to remove data for body {}", id, ex);
            return null;
        });
    }

    @Nullable
    private VxSerializedBodyData deserializeBody(byte[] data) {
        if (data == null) return null;
        VxByteBuf buf = new VxByteBuf(Unpooled.wrappedBuffer(data));
        try {
            return VxBodyCodec.deserialize(buf);
        } finally {
            if (buf.refCnt() > 0) {
                buf.release();
            }
        }
    }

    @Nullable
    public byte[] serializeBodyData(VxBody body, int index) {
        ByteBuf buffer = Unpooled.buffer();
        VxByteBuf friendlyBuf = new VxByteBuf(buffer);
        try {
            VxBodyCodec.serialize(body, index, dataStore, friendlyBuf);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return data;
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Error during body serialization for {}", body.getPhysicsId(), e);
            return null;
        } finally {
            if (buffer.refCnt() > 0) {
                buffer.release();
            }
        }
    }

    private void indexBodyData(UUID id, byte[] data) {
        long chunkKey = getChunkKeyFromData(data);
        chunkToUuidIndex.computeIfAbsent(chunkKey, k -> new CopyOnWriteArrayList<>()).add(id);
    }

    private void deIndexBody(UUID id, byte[] data) {
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
            buf.readUUID();
            buf.readUtf();
            double posX = buf.readDouble();
            buf.readDouble();
            double posZ = buf.readDouble();
            int chunkX = SectionPos.posToSectionCoord(posX);
            int chunkZ = SectionPos.posToSectionCoord(posZ);
            return new ChunkPos(chunkX, chunkZ).toLong();
        } finally {
            if (buf.refCnt() > 0) {
                buf.release();
            }
        }
    }
}