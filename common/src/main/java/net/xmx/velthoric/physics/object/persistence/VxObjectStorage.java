/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.persistence;

import com.github.stephengold.joltjni.Vec3;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxBody;
import net.xmx.velthoric.physics.object.manager.VxObjectDataStore;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.persistence.VxAbstractRegionStorage;
import net.xmx.velthoric.physics.persistence.VxRegionIndex;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Handles the serialization and file I/O for physics objects.
 * It stores objects in a region-based format for efficient chunk loading/unloading.
 *
 * @author xI-Mx-Ix
 */
public class VxObjectStorage extends VxAbstractRegionStorage<UUID, byte[]> {

    /**
     * A data-transfer object holding the complete state of a deserialized body.
     */
    public record SerializedBodyData(
            ResourceLocation typeId,
            UUID id,
            VxTransform transform,
            Vec3 linearVelocity,
            Vec3 angularVelocity,
            VxByteBuf customData
    ) {}

    private final VxObjectManager objectManager;
    private final VxObjectDataStore dataStore;
    private final ConcurrentMap<Long, List<UUID>> chunkToUuidIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CompletableFuture<VxBody>> pendingLoads = new ConcurrentHashMap<>();
    private ExecutorService loaderExecutor;

    public VxObjectStorage(ServerLevel level, VxObjectManager objectManager) {
        super(level, "body", "body");
        this.objectManager = objectManager;
        this.dataStore = objectManager.getDataStore();
    }

    @Override
    protected VxRegionIndex createRegionIndex() {
        return new VxRegionIndex(storagePath, "body");
    }

    @Override
    public void initialize() {
        super.initialize();
        int threadCount = Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
        this.loaderExecutor = Executors.newFixedThreadPool(threadCount, r -> new Thread(r, "Velthoric Object Loader"));
    }

    @Override
    public void shutdown() {
        super.shutdown();
        if (loaderExecutor != null) {
            loaderExecutor.shutdown();
            try {
                if (!loaderExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    loaderExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                loaderExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private RegionPos getRegionPos(ChunkPos chunkPos) {
        return new RegionPos(chunkPos.x >> 5, chunkPos.z >> 5);
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
     * Serializes and stores a physics object to its corresponding region file.
     *
     * @param object The object to store.
     */
    public void storeObject(VxBody object) {
        if (object == null) return;
        int index = object.getDataStoreIndex();
        if (index == -1) return;

        byte[] data = serializeObjectData(object, index);
        ChunkPos chunkPos = objectManager.getObjectChunkPos(index);
        RegionPos regionPos = getRegionPos(chunkPos);

        getRegion(regionPos).thenAcceptAsync(region -> {
            region.entries.put(object.getPhysicsId(), data);
            region.dirty.set(true);
            regionIndex.put(object.getPhysicsId(), regionPos);
            indexObjectData(object.getPhysicsId(), data);
        }, loaderExecutor).exceptionally(ex -> {
            VxMainClass.LOGGER.error("Failed to store object {}", object.getPhysicsId(), ex);
            return null;
        });
    }

    /**
     * Initiates loading for all objects within a specific chunk.
     *
     * @param chunkPos The position of the chunk to load objects from.
     */
    public void loadObjectsInChunk(ChunkPos chunkPos) {
        RegionPos regionPos = getRegionPos(chunkPos);
        getRegion(regionPos).thenRunAsync(() -> {
            List<UUID> idsToLoad = chunkToUuidIndex.get(chunkPos.toLong());
            if (idsToLoad == null || idsToLoad.isEmpty()) return;

            for (UUID id : List.copyOf(idsToLoad)) {
                if (objectManager.getObject(id) != null || pendingLoads.containsKey(id)) {
                    continue;
                }
                loadObject(id);
            }
        }, loaderExecutor).exceptionally(ex -> {
            VxMainClass.LOGGER.error("Failed to load objects in chunk {}", chunkPos, ex);
            return null;
        });
    }

    /**
     * Asynchronously loads a single physics object by its UUID.
     *
     * @param id The UUID of the object to load.
     * @return A CompletableFuture that will complete with the loaded object, or null if not found.
     */
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
                }, loaderExecutor)
                .thenApplyAsync(this::deserializeObject, loaderExecutor)
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

    /**
     * Removes an object's data from the storage.
     *
     * @param id The UUID of the object to remove.
     */
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
        }, loaderExecutor).exceptionally(ex -> {
            VxMainClass.LOGGER.error("Failed to remove data for object {}", id, ex);
            return null;
        });
    }

    @Nullable
    private SerializedBodyData deserializeObject(byte[] data) {
        if (data == null) return null;
        VxByteBuf buf = new VxByteBuf(Unpooled.wrappedBuffer(data));
        try {
            UUID id = buf.readUUID();
            ResourceLocation typeId = new ResourceLocation(buf.readUtf());

            VxTransform transform = new VxTransform();
            transform.fromBuffer(buf);

            Vec3 linearVelocity = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
            Vec3 angularVelocity = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());

            if (!linearVelocity.isFinite() || linearVelocity.isNan() || angularVelocity.isNan() || !angularVelocity.isFinite()) {
                VxMainClass.LOGGER.warn("Deserialized invalid velocity for object {}. Resetting to zero.", id);
                linearVelocity.set(0, 0, 0);
                angularVelocity.set(0, 0, 0);
            }

            // The rest of the buffer is custom data
            VxByteBuf customData = new VxByteBuf(buf.readBytes(buf.readableBytes()));

            return new SerializedBodyData(typeId, id, transform, linearVelocity, angularVelocity, customData);
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to deserialize physics object from data", e);
            return null;
        } finally {
            if (buf.refCnt() > 0) {
                buf.release();
            }
        }
    }

    private byte[] serializeObjectData(VxBody object, int index) {
        ByteBuf buffer = Unpooled.buffer();
        VxByteBuf friendlyBuf = new VxByteBuf(buffer);
        try {
            friendlyBuf.writeUUID(object.getPhysicsId());
            friendlyBuf.writeUtf(object.getType().getTypeId().toString());

            // Write transform from data store
            friendlyBuf.writeDouble(dataStore.posX[index]);
            friendlyBuf.writeDouble(dataStore.posY[index]);
            friendlyBuf.writeDouble(dataStore.posZ[index]);
            friendlyBuf.writeFloat(dataStore.rotX[index]);
            friendlyBuf.writeFloat(dataStore.rotY[index]);
            friendlyBuf.writeFloat(dataStore.rotZ[index]);
            friendlyBuf.writeFloat(dataStore.rotW[index]);

            // Write velocities from data store
            friendlyBuf.writeFloat(dataStore.velX[index]);
            friendlyBuf.writeFloat(dataStore.velY[index]);
            friendlyBuf.writeFloat(dataStore.velZ[index]);
            friendlyBuf.writeFloat(dataStore.angVelX[index]);
            friendlyBuf.writeFloat(dataStore.angVelY[index]);
            friendlyBuf.writeFloat(dataStore.angVelZ[index]);

            object.writeCreationData(friendlyBuf);
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
            buf.readUUID(); // Skip UUID
            buf.readUtf(); // Skip typeId
            VxTransform tempTransform = new VxTransform();
            tempTransform.fromBuffer(buf);
            var translation = tempTransform.getTranslation();
            int chunkX = SectionPos.posToSectionCoord(translation.xx());
            int chunkZ = SectionPos.posToSectionCoord(translation.zz());
            return new ChunkPos(chunkX, chunkZ).toLong();
        } finally {
            if (buf.refCnt() > 0) {
                buf.release();
            }
        }
    }
}