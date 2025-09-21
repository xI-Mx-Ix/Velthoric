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
import net.xmx.velthoric.physics.object.manager.VxObjectDataStore;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.persistence.VxAbstractRegionStorage;
import net.xmx.velthoric.physics.persistence.VxRegionIndex;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Manages the persistent storage of physics objects on disk using a region-based file system.
 * This class handles serialization, deserialization, and asynchronous loading/saving of object data.
 *
 * @author xI-Mx-Ix
 */
public class VxObjectStorage extends VxAbstractRegionStorage<UUID, byte[]> {

    /**
     * A record representing the fully deserialized data of a physics body from storage.
     *
     * @param typeId            The {@link ResourceLocation} identifying the object's type.
     * @param id                The unique ID of the object instance.
     * @param transform         The last known transform (position and rotation).
     * @param linearVelocity    The last known linear velocity.
     * @param angularVelocity   The last known angular velocity.
     * @param persistenceData   A buffer containing custom implementation-specific data.
     */
    public record SerializedBodyData(
            ResourceLocation typeId,
            UUID id,
            VxTransform transform,
            Vec3 linearVelocity,
            Vec3 angularVelocity,
            VxByteBuf persistenceData
    ) {}

    private final VxObjectManager objectManager;
    private final VxObjectDataStore dataStore;
    /** A spatial index mapping chunk keys to the UUIDs of objects within them. */
    private final ConcurrentMap<Long, List<UUID>> chunkToUuidIndex = new ConcurrentHashMap<>();
    /** A map to track ongoing asynchronous load operations to prevent duplicate loads. */
    private final ConcurrentMap<UUID, CompletableFuture<VxBody>> pendingLoads = new ConcurrentHashMap<>();
    /** A dedicated thread pool for handling I/O-intensive loading operations. */
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

    /**
     * Calculates the region file position for a given chunk position.
     *
     * @param chunkPos The chunk position.
     * @return The corresponding region position.
     */
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
     * Stores a collection of physics objects to disk asynchronously.
     *
     * @param objects The objects to store.
     */
    public void storeObjects(Collection<VxBody> objects) {
        for (VxBody object : objects) {
            storeObject(object);
        }
    }

    /**
     * Stores a single physics object to disk asynchronously.
     *
     * @param object The object to store.
     */
    public void storeObject(VxBody object) {
        if (object == null) return;
        int index = object.getDataStoreIndex();
        if (index == -1) return; // Cannot store an object not in the data store.

        byte[] data = serializeObjectData(object, index);
        ChunkPos chunkPos = objectManager.getObjectChunkPos(index);
        RegionPos regionPos = getRegionPos(chunkPos);

        // Asynchronously get the region, then write the data.
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
     * Initiates the loading of all stored physics objects within a specific chunk.
     *
     * @param chunkPos The position of the chunk to load objects for.
     */
    public void loadObjectsInChunk(ChunkPos chunkPos) {
        RegionPos regionPos = getRegionPos(chunkPos);
        getRegion(regionPos).thenRunAsync(() -> {
            List<UUID> idsToLoad = chunkToUuidIndex.get(chunkPos.toLong());
            if (idsToLoad == null || idsToLoad.isEmpty()) return;

            for (UUID id : List.copyOf(idsToLoad)) {
                // Skip objects that are already loaded or are in the process of loading.
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
     * If the object is already loaded, returns a completed future.
     *
     * @param id The UUID of the object to load.
     * @return A {@link CompletableFuture} that will complete with the loaded object, or null if not found.
     */
    public CompletableFuture<VxBody> loadObject(UUID id) {
        VxBody existingObject = objectManager.getObject(id);
        if (existingObject != null) {
            return CompletableFuture.completedFuture(existingObject);
        }
        // computeIfAbsent ensures that the load operation is only started once per ID.
        return pendingLoads.computeIfAbsent(id, this::loadObjectAsync);
    }

    /**
     * The asynchronous loading pipeline for a single object.
     *
     * @param id The UUID of the object.
     * @return A future that completes with the loaded {@link VxBody}.
     */
    private CompletableFuture<VxBody> loadObjectAsync(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
                    // Find the region file where the object is stored.
                    RegionPos regionPos = regionIndex.get(id);
                    if (regionPos == null) return null;

                    // Load the region data and get the raw byte array for the object.
                    RegionData<UUID, byte[]> region = getRegion(regionPos).join();
                    return region.entries.get(id);
                }, loaderExecutor)
                .thenApplyAsync(this::deserializeObject, loaderExecutor) // Deserialize on loader thread.
                .thenComposeAsync(data -> {
                    if (data == null) {
                        return CompletableFuture.completedFuture(null);
                    }
                    // Schedule the final addition to the world on the main physics thread.
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
                    // Remove from pending loads map regardless of success or failure.
                    pendingLoads.remove(id);
                });
    }

    /**
     * Removes an object's data from the storage files asynchronously.
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

    /**
     * Deserializes a raw byte array into a structured {@link SerializedBodyData} object.
     *
     * @param data The raw byte data from storage.
     * @return The deserialized data, or null on failure.
     */
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

            // Sanity check for corrupt velocity data.
            if (!linearVelocity.isFinite() || linearVelocity.isNan() || angularVelocity.isNan() || !angularVelocity.isFinite()) {
                VxMainClass.LOGGER.warn("Deserialized invalid velocity for object {}. Resetting to zero.", id);
                linearVelocity.set(0, 0, 0);
                angularVelocity.set(0, 0, 0);
            }

            // The rest of the buffer contains the custom persistence data.
            VxByteBuf persistenceData = new VxByteBuf(buf.readBytes(buf.readableBytes()));

            return new SerializedBodyData(typeId, id, transform, linearVelocity, angularVelocity, persistenceData);
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to deserialize physics object from data", e);
            return null;
        } finally {
            if (buf.refCnt() > 0) {
                buf.release();
            }
        }
    }

    /**
     * Serializes a {@link VxBody} and its current physics state into a byte array for storage.
     *
     * @param object The object to serialize.
     * @param index  The object's index in the data store.
     * @return The serialized byte array.
     */
    private byte[] serializeObjectData(VxBody object, int index) {
        ByteBuf buffer = Unpooled.buffer();
        VxByteBuf friendlyBuf = new VxByteBuf(buffer);
        try {
            friendlyBuf.writeUUID(object.getPhysicsId());
            friendlyBuf.writeUtf(object.getType().getTypeId().toString());

            // Write transform from data store.
            friendlyBuf.writeDouble(dataStore.posX[index]);
            friendlyBuf.writeDouble(dataStore.posY[index]);
            friendlyBuf.writeDouble(dataStore.posZ[index]);
            friendlyBuf.writeFloat(dataStore.rotX[index]);
            friendlyBuf.writeFloat(dataStore.rotY[index]);
            friendlyBuf.writeFloat(dataStore.rotZ[index]);
            friendlyBuf.writeFloat(dataStore.rotW[index]);

            // Write velocities from data store.
            friendlyBuf.writeFloat(dataStore.velX[index]);
            friendlyBuf.writeFloat(dataStore.velY[index]);
            friendlyBuf.writeFloat(dataStore.velZ[index]);
            friendlyBuf.writeFloat(dataStore.angVelX[index]);
            friendlyBuf.writeFloat(dataStore.angVelY[index]);
            friendlyBuf.writeFloat(dataStore.angVelZ[index]);

            // Let the object write its custom persistence data.
            object.writePersistenceData(friendlyBuf);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return data;
        } finally {
            if (buffer.refCnt() > 0) {
                buffer.release();
            }
        }
    }

    /**
     * Adds an entry to the chunk-to-UUID spatial index.
     *
     * @param id   The object's UUID.
     * @param data The object's serialized data, used to extract its position.
     */
    private void indexObjectData(UUID id, byte[] data) {
        long chunkKey = getChunkKeyFromData(data);
        chunkToUuidIndex.computeIfAbsent(chunkKey, k -> new CopyOnWriteArrayList<>()).add(id);
    }

    /**
     * Removes an entry from the chunk-to-UUID spatial index.
     *
     * @param id   The object's UUID.
     * @param data The object's serialized data, used to extract its position.
     */
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

    /**
     * Quickly reads the chunk position from serialized object data without fully deserializing it.
     *
     * @param data The serialized byte array.
     * @return The long-encoded chunk key.
     */
    private long getChunkKeyFromData(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            // Skip UUID and type ID string.
            buf.readUUID();
            buf.readUtf();
            // Read just the transform.
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