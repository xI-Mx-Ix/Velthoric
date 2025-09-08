package net.xmx.velthoric.physics.object.persistence;

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
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.object.manager.registry.VxObjectRegistry;
import net.xmx.velthoric.physics.persistence.VxAbstractRegionStorage;
import net.xmx.velthoric.physics.persistence.VxRegionIndex;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class VxObjectStorage extends VxAbstractRegionStorage<UUID, byte[]> {

    private final VxObjectManager objectManager;
    private final ConcurrentMap<Long, List<UUID>> chunkToUuidIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CompletableFuture<VxAbstractBody>> pendingLoads = new ConcurrentHashMap<>();
    private ExecutorService loaderExecutor;

    public VxObjectStorage(ServerLevel level, VxObjectManager objectManager) {
        super(level, "body", "body");
        this.objectManager = objectManager;
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

    public void storeObject(VxAbstractBody object) {
        if (object == null) return;
        byte[] data = serializeObjectData(object);
        ChunkPos chunkPos = VxObjectManager.getObjectChunkPos(object);
        RegionPos regionPos = getRegionPos(chunkPos);
        RegionData<UUID, byte[]> region = loadedRegions.computeIfAbsent(regionPos, this::loadRegion);

        region.entries.put(object.getPhysicsId(), data);
        region.dirty.set(true);
        regionIndex.put(object.getPhysicsId(), regionPos);
        indexObjectData(object.getPhysicsId(), data);
    }

    public void loadObjectsInChunk(ChunkPos chunkPos) {
        RegionPos regionPos = getRegionPos(chunkPos);
        loadedRegions.computeIfAbsent(regionPos, this::loadRegion);

        List<UUID> idsToLoad = chunkToUuidIndex.get(chunkPos.toLong());
        if (idsToLoad == null || idsToLoad.isEmpty()) return;

        for (UUID id : List.copyOf(idsToLoad)) {
            if (objectManager.getObject(id).isPresent() || pendingLoads.containsKey(id)) {
                continue;
            }
            loadObject(id);
        }
    }

    public CompletableFuture<VxAbstractBody> loadObject(UUID id) {
        if (objectManager.getObject(id).isPresent()) {
            return CompletableFuture.completedFuture(objectManager.getObject(id).orElse(null));
        }
        return pendingLoads.computeIfAbsent(id, this::loadObjectAsync);
    }

    private CompletableFuture<VxAbstractBody> loadObjectAsync(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
                    RegionPos regionPos = regionIndex.get(id);
                    if (regionPos == null) return null;

                    RegionData<UUID, byte[]> region = loadedRegions.computeIfAbsent(regionPos, this::loadRegion);
                    byte[] data = region.entries.remove(id);

                    if (data != null) {
                        region.dirty.set(true);
                        deIndexObject(id, data);
                        return deserializeObject(id, data);
                    }
                    return null;
                }, loaderExecutor)
                .thenApplyAsync(obj -> {
                    if (obj != null) {
                        objectManager.reAddObjectToWorld(obj);
                    }
                    return obj;
                }, level.getServer())
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

        RegionData<UUID, byte[]> region = loadedRegions.computeIfAbsent(regionPos, this::loadRegion);
        byte[] data = region.entries.remove(id);

        if (data != null) {
            region.dirty.set(true);
            deIndexObject(id, data);
            regionIndex.remove(id);
        }
    }

    private VxAbstractBody deserializeObject(UUID id, byte[] data) {
        VxByteBuf buf = new VxByteBuf(Unpooled.wrappedBuffer(data));
        try {
            ResourceLocation typeId = new ResourceLocation(buf.readUtf());
            VxAbstractBody obj = VxObjectRegistry.getInstance().create(typeId, objectManager.getPhysicsWorld(), id);
            if (obj == null) {
                VxMainClass.LOGGER.error("Failed to create object of type {} with ID {} during deserialization.", typeId, id);
                return null;
            }
            obj.getGameTransform().fromBuffer(buf);
            obj.readCreationData(buf);
            return obj;
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to deserialize physics object {}", id, e);
            return null;
        } finally {
            if (buf.refCnt() > 0) {
                buf.release();
            }
        }
    }

    private byte[] serializeObjectData(VxAbstractBody object) {
        ByteBuf buffer = Unpooled.buffer();
        VxByteBuf friendlyBuf = new VxByteBuf(buffer);
        try {
            friendlyBuf.writeUtf(object.getType().getTypeId().toString());
            object.getGameTransform().toBuffer(friendlyBuf);
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
            buf.readUtf();
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