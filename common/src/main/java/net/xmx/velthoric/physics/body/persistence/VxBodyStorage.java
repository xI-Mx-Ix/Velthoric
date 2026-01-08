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
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.body.manager.VxBodyManager;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.persistence.VxAbstractRegionStorage;
import net.xmx.velthoric.physics.persistence.VxRegionIndex;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Manages the persistent storage of physics bodies on disk using a region-based file system.
 * <p>
 * This class handles the translation between high-level {@link VxBody} objects and their
 * binary representation. It leverages the {@link VxAbstractRegionStorage} for thread-safe,
 * sequential I/O operations and maintains a fastutil-based index mapping chunks to body UUIDs
 * to support efficient chunk loading.
 *
 * @author xI-Mx-Ix
 */
public class VxBodyStorage extends VxAbstractRegionStorage<UUID, byte[]> {

    private final VxBodyManager bodyManager;

    /**
     * An index mapping a Chunk Position (as a long key) to a Set of Body UUIDs contained within that chunk.
     * <p>
     * Implementation details:
     * <ul>
     *     <li>Uses {@link Long2ObjectMap} (fastutil) to avoid boxing overhead for chunk keys.</li>
     *     <li>The outer map is synchronized to handle concurrent chunk access.</li>
     *     <li>The values are {@link java.util.concurrent.ConcurrentHashMap#newKeySet()}, allowing
     *     O(1) concurrent adds/removes without the O(N) copy overhead of CopyOnWriteArrayList.</li>
     * </ul>
     */
    private final Long2ObjectMap<Set<UUID>> chunkToUuidIndex = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());

    /**
     * Tracks bodies currently being loaded to prevent duplicate asynchronous requests
     * for the same body ID.
     */
    private final ConcurrentMap<UUID, CompletableFuture<VxBody>> pendingLoads = new ConcurrentHashMap<>();

    /**
     * Constructs a new storage manager for physics bodies.
     *
     * @param level       The server level this storage belongs to.
     * @param bodyManager The body manager instance.
     */
    public VxBodyStorage(ServerLevel level, VxBodyManager bodyManager) {
        super(level, "body", "body");
        this.bodyManager = bodyManager;
    }

    @Override
    protected VxRegionIndex createRegionIndex() {
        return new VxRegionIndex(storagePath, "body");
    }

    @Override
    protected void readRegionData(ByteBuf buffer, Map<UUID, byte[]> regionData) {
        FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(buffer);
        while (friendlyBuf.isReadable()) {
            UUID id = friendlyBuf.readUUID();
            byte[] data = friendlyBuf.readByteArray();

            regionData.put(id, data);
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
     * The serialization occurs on the calling thread (usually the physics or server thread).
     *
     * @param body The body to store.
     */
    public void storeBody(VxBody body) {
        if (body == null || body.getDataStoreIndex() == -1) return;

        // Ensure we are in a valid state to read body data
        bodyManager.getPhysicsWorld().execute(() -> {
            byte[] snapshot = serializeBodyData(body);
            if (snapshot == null) return;

            // Calculate chunk pos for region determination
            ChunkPos chunkPos = bodyManager.getBodyChunkPos(body.getDataStoreIndex());
            RegionPos regionPos = new RegionPos(chunkPos.x >> 5, chunkPos.z >> 5);

            storeBodyBatch(regionPos, Map.of(body.getPhysicsId(), snapshot));
        });
    }

    /**
     * Stores a batch of pre-serialized body data snapshots, grouped by region.
     * <p>
     * This method returns a combined future that completes when all data in the batch
     * has been passed to the underlying I/O worker.
     *
     * @param snapshotsByRegion A map where each key is a region position and the value is a map of body snapshots for that region.
     * @return A CompletableFuture that completes when the storage operation is fully queued.
     */
    public CompletableFuture<Void> storeBodyBatch(Map<RegionPos, Map<UUID, byte[]>> snapshotsByRegion) {
        if (snapshotsByRegion == null || snapshotsByRegion.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        snapshotsByRegion.forEach((pos, batch) -> futures.add(storeBodyBatch(pos, batch)));

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Stores a batch of body snapshots for a single region.
     * <p>
     * This method ensures the region is loaded into memory, updates the in-memory map,
     * updates the spatial index, and then triggers a save operation via the I/O worker.
     *
     * @param regionPos     The position of the region where the data should be stored.
     * @param snapshotBatch A map of body UUIDs to their serialized data for this region.
     * @return A CompletableFuture tracking the operation.
     */
    public CompletableFuture<Void> storeBodyBatch(RegionPos regionPos, Map<UUID, byte[]> snapshotBatch) {
        if (snapshotBatch == null || snapshotBatch.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        // 1. Ensure the region map is loaded (or create a new one)
        return getRegion(regionPos).thenCompose(map -> {
            // 2. Update the in-memory state
            for (Map.Entry<UUID, byte[]> entry : snapshotBatch.entrySet()) {
                UUID bodyId = entry.getKey();
                byte[] data = entry.getValue();

                map.put(bodyId, data);

                // Update side indices
                if (regionIndex != null) regionIndex.put(bodyId, regionPos);
                indexBodyData(bodyId, data);
            }

            // 3. Trigger the write-behind persistence
            return saveRegion(regionPos);
        }).exceptionally(ex -> {
            VxMainClass.LOGGER.error("Failed to queue body batch for storage in region {}", regionPos, ex);
            return null;
        });
    }

    /**
     * Triggers the loading of all bodies known to exist within the specified chunk.
     *
     * @param chunkPos The position of the chunk to load.
     */
    public void loadBodiesInChunk(ChunkPos chunkPos) {
        RegionPos regionPos = new RegionPos(chunkPos.x >> 5, chunkPos.z >> 5);

        // Ensure region is loaded first
        getRegion(regionPos).thenAccept(map -> {
            Set<UUID> idsToLoad = chunkToUuidIndex.get(chunkPos.toLong());
            if (idsToLoad == null || idsToLoad.isEmpty()) return;

            // Iterate over a safe copy or the iterator of the ConcurrentSet
            // ConcurrentKeySet iterator is weakly consistent and safe to use here.
            for (UUID id : idsToLoad) {
                // Check if already loaded or currently loading
                if (bodyManager.getVxBody(id) != null || pendingLoads.containsKey(id)) {
                    continue;
                }

                // Proceed to load individual body
                loadBody(id);
            }
        }).exceptionally(ex -> {
            VxMainClass.LOGGER.error("Failed to load bodies in chunk {}", chunkPos, ex);
            return null;
        });
    }

    /**
     * Initiates the asynchronous loading of a body by its UUID.
     *
     * @param id The UUID of the body to load.
     * @return A CompletableFuture that completes with the loaded body, or null if not found.
     */
    public CompletableFuture<VxBody> loadBody(UUID id) {
        VxBody existingBody = bodyManager.getVxBody(id);
        if (existingBody != null) {
            return CompletableFuture.completedFuture(existingBody);
        }

        // Check if a load is already in progress to avoid duplicates
        CompletableFuture<VxBody> pending = pendingLoads.get(id);
        if (pending != null) {
            return pending;
        }

        if (regionIndex == null) return CompletableFuture.completedFuture(null);

        RegionPos regionPos = regionIndex.get(id);
        if (regionPos == null) {
            return CompletableFuture.completedFuture(null);
        }

        // Atomically start the load operation if one isn't already present.
        return pendingLoads.computeIfAbsent(id, k -> startLoadAsync(k, regionPos));
    }

    /**
     * Helper method to perform the actual load logic, chained from the computeIfAbsent.
     *
     * @param id        The UUID of the body.
     * @param regionPos The region where the body data is stored.
     * @return A future for the loaded body.
     */
    private CompletableFuture<VxBody> startLoadAsync(UUID id, RegionPos regionPos) {
        return getRegion(regionPos)
                .thenApply(map -> map.get(id)) // Extract binary data from region map
                .thenApply(this::deserializeBody) // Deserialize to intermediate record
                .thenCompose(data -> {
                    if (data == null) {
                        return CompletableFuture.completedFuture(null);
                    }
                    CompletableFuture<VxBody> bodyFuture = new CompletableFuture<>();

                    // Body creation and registration must happen on the server/physics thread
                    // to ensure thread-safety with the managers.
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
                    // Clean up the pending map
                    pendingLoads.remove(id);
                });
    }

    /**
     * Removes a body's data from the persistent storage.
     *
     * @param id The UUID of the body to remove.
     */
    public void removeData(UUID id) {
        if (regionIndex == null) return;
        RegionPos regionPos = regionIndex.get(id);
        if (regionPos == null) return;

        getRegion(regionPos).thenCompose(map -> {
            byte[] data = map.remove(id);
            if (data != null) {
                deIndexBody(id, data);
                regionIndex.remove(id);
                // Trigger save to persist the removal
                return saveRegion(regionPos);
            }
            return CompletableFuture.completedFuture(null);
        }).exceptionally(ex -> {
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

    /**
     * Serializes the body into a byte array.
     *
     * @param body The body to serialize.
     * @return The byte array snapshot, or null if serialization failed.
     */
    public byte @Nullable [] serializeBodyData(VxBody body) {
        // Use pooled allocator to reduce GC pressure during serialization
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
            if (buffer.refCnt() > 0) {
                buffer.release();
            }
        }
    }

    /**
     * Updates the chunk-to-UUID index for a specific body.
     * <p>
     * This method retrieves the chunk position from the serialized data and adds the body's UUID
     * to the corresponding Set. Since we use a {@link java.util.concurrent.ConcurrentHashMap.KeySetView},
     * the add operation is O(1) and fully thread-safe, unlike CopyOnWriteArrayList.
     *
     * @param id   The UUID of the body.
     * @param data The serialized data containing the position.
     */
    private void indexBodyData(UUID id, byte[] data) {
        long chunkKey = getChunkKeyFromData(data);

        // computeIfAbsent is atomic on the synchronized map.
        // The lambda creates a thread-safe Concurrent Set if the key is new.
        chunkToUuidIndex.computeIfAbsent(chunkKey, (long k) -> ConcurrentHashMap.newKeySet()).add(id);
    }

    /**
     * Removes a body from the chunk-to-UUID index.
     * <p>
     * Uses atomic computation to remove the ID and cleans up the map entry if the set becomes empty.
     *
     * @param id   The UUID of the body.
     * @param data The serialized data used to locate the chunk.
     */
    private void deIndexBody(UUID id, byte[] data) {
        long chunkKey = getChunkKeyFromData(data);

        // Atomic update: retrieve the set, remove the ID, and remove the entry if empty.
        chunkToUuidIndex.computeIfPresent(chunkKey, (key, set) -> {
            set.remove(id);
            return set.isEmpty() ? null : set;
        });
    }

    /**
     * Parses the binary data to extract the position and convert it to a chunk key.
     *
     * @param data The serialized body data.
     * @return The long representation of the ChunkPos.
     */
    private long getChunkKeyFromData(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            // Structure: UUID (16 bytes) -> Type String -> InternalPersistenceData
            buf.readUUID();
            buf.readUtf();

            // InternalPersistenceData starts with Position (3 doubles)
            double posX = buf.readDouble();
            buf.readDouble(); // Y
            double posZ = buf.readDouble(); // Z

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