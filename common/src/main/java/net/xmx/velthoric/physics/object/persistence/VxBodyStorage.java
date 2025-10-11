/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.persistence;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.manager.VxObjectDataStore;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.object.type.VxBody;
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
 * Manages the persistent storage of physics objects on disk using a region-based file system.
 * This class handles serialization, deserialization, and asynchronous loading/saving of object data.
 * It uses a codec to separate serialization logic from storage management.
 *
 * @author xI-Mx-Ix
 */
public class VxBodyStorage extends VxAbstractRegionStorage<UUID, byte[]> {
    private final VxObjectManager objectManager;
    private final VxObjectDataStore dataStore;
    private final ConcurrentMap<Long, List<UUID>> chunkToUuidIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CompletableFuture<VxBody>> pendingLoads = new ConcurrentHashMap<>();

    public VxBodyStorage(ServerLevel level, VxObjectManager objectManager) {
        super(level, "body", "body");
        this.objectManager = objectManager;
        this.dataStore = objectManager.getDataStore();
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
            indexObjectData(id, data);
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
     * Stores a collection of objects by grouping them by region and saving each region in a single batch operation.
     * This is much more efficient than saving each object individually.
     *
     * @param objects The collection of VxBody objects to store.
     */
    public void storeObjects(Collection<VxBody> objects) {
        if (objects == null || objects.isEmpty()) return;

        Map<RegionPos, List<VxBody>> objectsByRegion = objects.stream()
                .filter(Objects::nonNull)
                .filter(obj -> obj.getDataStoreIndex() != -1)
                .collect(Collectors.groupingBy(obj -> {
                    ChunkPos chunkPos = objectManager.getObjectChunkPos(obj.getDataStoreIndex());
                    return new RegionPos(chunkPos.x >> 5, chunkPos.z >> 5);
                }));

        objectsByRegion.forEach((regionPos, regionObjects) -> {
            getRegion(regionPos).thenAcceptAsync(region -> {
                for (VxBody object : regionObjects) {
                    int index = object.getDataStoreIndex();
                    if (index == -1) continue; // Should already be filtered, but as a safeguard

                    byte[] data = serializeObjectData(object, index);
                    region.entries.put(object.getPhysicsId(), data);
                    regionIndex.put(object.getPhysicsId(), regionPos);
                    indexObjectData(object.getPhysicsId(), data);
                }
                region.dirty.set(true);
            }, ioExecutor).exceptionally(ex -> {
                VxMainClass.LOGGER.error("Failed to store object batch in region {}", regionPos, ex);
                return null;
            });
        });
    }

    /**
     * Stores a single object. For performance, prefer using storeObjects for multiple objects.
     * @param object The object to store.
     */
    public void storeObject(VxBody object) {
        if (object == null || object.getDataStoreIndex() == -1) return;
        storeObjects(Collections.singletonList(object));
    }

    public void loadObjectsInChunk(ChunkPos chunkPos) {
        RegionPos regionPos = new RegionPos(chunkPos.x >> 5, chunkPos.z >> 5);
        getRegion(regionPos).thenRunAsync(() -> {
            List<UUID> idsToLoad = chunkToUuidIndex.get(chunkPos.toLong());
            if (idsToLoad == null || idsToLoad.isEmpty()) return;

            for (UUID id : List.copyOf(idsToLoad)) {
                if (objectManager.getObject(id) != null || pendingLoads.containsKey(id)) {
                    continue;
                }
                loadObject(id);
            }
        }, ioExecutor).exceptionally(ex -> {
            VxMainClass.LOGGER.error("Failed to load objects in chunk {}", chunkPos, ex);
            return null;
        });
    }

    public CompletableFuture<VxBody> loadObject(UUID id) {
        VxBody existingObject = objectManager.getObject(id);
        if (existingObject != null) {
            return CompletableFuture.completedFuture(existingObject);
        }
        return pendingLoads.computeIfAbsent(id, this::loadObjectAsync);
    }

    private CompletableFuture<VxBody> loadObjectAsync(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
                    RegionPos regionPos = regionIndex.get(id);
                    if (regionPos == null) return null;
                    RegionData<UUID, byte[]> region = getRegion(regionPos).join();
                    return region.entries.get(id);
                }, ioExecutor)
                .thenApplyAsync(this::deserializeObject, ioExecutor)
                .thenComposeAsync(data -> {
                    if (data == null) {
                        return CompletableFuture.completedFuture(null);
                    }
                    CompletableFuture<VxBody> bodyFuture = new CompletableFuture<>();
                    objectManager.getPhysicsWorld().execute(() -> {
                        try {
                            VxBody body = objectManager.addSerializedBody(data);
                            bodyFuture.complete(body);
                        } catch (Exception e) {
                            bodyFuture.completeExceptionally(e);
                        }
                    });
                    return bodyFuture;
                })
                .whenComplete((obj, ex) -> {
                    if (ex != null) {
                        VxMainClass.LOGGER.error("Exception loading physics object {}", id, ex);
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
                deIndexObject(id, data);
                regionIndex.remove(id);
            }
        }, ioExecutor).exceptionally(ex -> {
            VxMainClass.LOGGER.error("Failed to remove data for object {}", id, ex);
            return null;
        });
    }

    /**
     * Deserializes a raw byte array into a structured {@link VxSerializedBodyData} object using the VxBodyCodec.
     *
     * @param data The raw byte data from storage.
     * @return The deserialized data, or null on failure.
     */
    @Nullable
    private VxSerializedBodyData deserializeObject(byte[] data) {
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

    /**
     * Serializes a {@link VxBody} and its current physics state into a byte array using the VxBodyCodec.
     *
     * @param object The object to serialize.
     * @param index  The object's index in the data store.
     * @return The serialized byte array.
     */
    private byte[] serializeObjectData(VxBody object, int index) {
        ByteBuf buffer = Unpooled.buffer();
        VxByteBuf friendlyBuf = new VxByteBuf(buffer);
        try {
            VxBodyCodec.serialize(object, index, dataStore, friendlyBuf);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return data;
        } finally {
            if (buffer.refCnt() > 0) {
                buffer.release();
            }
        }
    }

    private void indexObjectData(UUID id, byte[] data) {
        long chunkKey = getChunkKeyFromData(data);
        chunkToUuidIndex.computeIfAbsent(chunkKey, k -> new CopyOnWriteArrayList<>()).add(id);
    }

    private void deIndexObject(UUID id, byte[] data) {
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
            // This part still needs to partially read the data to find the position for indexing.
            // This is acceptable as it's a storage-level concern.
            buf.readUUID(); // Skip ID
            buf.readUtf(); // Skip Type ID
            // Read position directly instead of creating a full transform object
            double posX = buf.readDouble();
            buf.readDouble(); // Skip posY
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