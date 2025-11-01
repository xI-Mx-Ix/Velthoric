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
import java.util.stream.Collectors;

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
     * Stores a collection of bodies by delegating each one to the single-store method.
     * @param bodies The collection of VxBody instances to store.
     */
    public void storeBodies(Collection<VxBody> bodies) {
        if (bodies == null || bodies.isEmpty()) return;
        for (VxBody body : bodies) {
            storeBody(body);
        }
    }

    /**
     * Stores a single body. This is now the primary method for saving.
     * It asynchronously retrieves the correct region, serializes the body,
     * and adds it to the in-memory representation of the region, marking it as dirty.
     * The actual file write happens later in a batched operation.
     *
     * @param body The body to store.
     */
    public void storeBody(VxBody body) {
        if (body == null || body.getDataStoreIndex() == -1) return;

        ChunkPos chunkPos = bodyManager.getBodyChunkPos(body.getDataStoreIndex());
        RegionPos regionPos = new RegionPos(chunkPos.x >> 5, chunkPos.z >> 5);

        // Retrieve the region asynchronously (will load if not in memory)
        getRegion(regionPos).thenAcceptAsync(region -> {
            int index = body.getDataStoreIndex();
            // Re-check in case the body was removed in the meantime
            if (index == -1) return;

            // Serialize the body and add it to the region's in-memory data
            byte[] data = serializeBodyData(body, index);
            region.entries.put(body.getPhysicsId(), data);

            // Update the global index
            regionIndex.put(body.getPhysicsId(), regionPos);

            // Update the chunk->UUID index for fast loading
            indexBodyData(body.getPhysicsId(), data);

            // Mark the region as "dirty" so it will be written in the next save operation
            region.dirty.set(true);
        }, ioExecutor).exceptionally(ex -> {
            VxMainClass.LOGGER.error("Failed to queue body {} for storage in region {}", body.getPhysicsId(), regionPos, ex);
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

    private byte[] serializeBodyData(VxBody body, int index) {
        ByteBuf buffer = Unpooled.buffer();
        VxByteBuf friendlyBuf = new VxByteBuf(buffer);
        try {
            VxBodyCodec.serialize(body, index, dataStore, friendlyBuf);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return data;
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