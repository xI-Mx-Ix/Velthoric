package net.xmx.velthoric.physics.object.manager.persistence;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class VxObjectStorage {

    private final Path dataFile;
    private final VxObjectManager objectManager;
    private final ServerLevel level;

    private final ConcurrentMap<UUID, byte[]> unloadedObjectsData = new ConcurrentHashMap<>();

    private final ConcurrentMap<Long, List<UUID>> unloadedChunkIndex = new ConcurrentHashMap<>();

    private final ConcurrentMap<UUID, CompletableFuture<VxAbstractBody>> pendingLoads = new ConcurrentHashMap<>();

    private ExecutorService loaderExecutor;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    public VxObjectStorage(ServerLevel level, VxObjectManager objectManager) {
        this.objectManager = objectManager;
        this.level = level;
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        Path dimensionRoot = DimensionType.getStorageFolder(level.dimension(), worldRoot);
        this.dataFile = dimensionRoot.resolve("velthoric").resolve("bodies.bin");
    }

    public void initialize() {
        if (isInitialized.getAndSet(true)) return;

        int threadCount = Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
        this.loaderExecutor = Executors.newFixedThreadPool(threadCount, r -> new Thread(r, "Velthoric Object Loader"));

        loadFromFile();
    }

    public void shutdown() {
        if (!isInitialized.getAndSet(false)) return;

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

    private void loadFromFile() {
        if (!Files.exists(dataFile)) return;

        try (FileChannel channel = FileChannel.open(dataFile, StandardOpenOption.READ)) {
            if (channel.size() == 0) return;

            ByteBuf buffer = Unpooled.wrappedBuffer(channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()));
            FriendlyByteBuf fileBuf = new FriendlyByteBuf(buffer);
            while (fileBuf.isReadable()) {
                UUID id = fileBuf.readUUID();
                byte[] data = fileBuf.readByteArray();
                unloadedObjectsData.put(id, data);
            }
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to load physics objects from {}", dataFile, e);
        }
        rebuildChunkIndex();
    }

    private void rebuildChunkIndex() {
        unloadedChunkIndex.clear();
        unloadedObjectsData.forEach(this::indexObjectData);
    }

    public void saveToFile() {
        if (unloadedObjectsData.isEmpty()) {
            try {
                if(Files.exists(dataFile)) {
                    Files.delete(dataFile);
                }
            } catch (IOException e) {
                VxMainClass.LOGGER.error("Failed to delete empty physics objects file {}", dataFile, e);
            }
            return;
        }

        ByteBuf masterBuf = Unpooled.buffer();
        try {
            FriendlyByteBuf friendlyMasterBuf = new FriendlyByteBuf(masterBuf);
            for (Map.Entry<UUID, byte[]> entry : unloadedObjectsData.entrySet()) {
                friendlyMasterBuf.writeUUID(entry.getKey());
                friendlyMasterBuf.writeByteArray(entry.getValue());
            }

            Files.createDirectories(dataFile.getParent());
            try (FileChannel channel = FileChannel.open(dataFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                channel.write(masterBuf.nioBuffer());
            }
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to save physics objects to {}", dataFile, e);
        } finally {
            if (masterBuf.refCnt() > 0) {
                masterBuf.release();
            }
        }
    }

    public void storeObject(VxAbstractBody object) {
        if (object == null) return;
        byte[] data = serializeObjectData(object);
        unloadedObjectsData.put(object.getPhysicsId(), data);
        indexObjectData(object.getPhysicsId(), data);
    }

    public void loadObjectsInChunk(ChunkPos chunkPos) {
        List<UUID> idsToLoad = unloadedChunkIndex.get(chunkPos.toLong());
        if (idsToLoad == null || idsToLoad.isEmpty()) return;

        for (UUID id : List.copyOf(idsToLoad)) {
            if (objectManager.getObjectContainer().hasObject(id) || pendingLoads.containsKey(id)) {
                continue;
            }
            loadObject(id);
        }
    }

    public CompletableFuture<VxAbstractBody> loadObject(UUID id) {
        if (objectManager.getObjectContainer().hasObject(id)) {
            return CompletableFuture.completedFuture(objectManager.getObjectContainer().get(id).orElse(null));
        }

        return pendingLoads.computeIfAbsent(id, objectId ->
                CompletableFuture.supplyAsync(() -> {
                            byte[] data = unloadedObjectsData.remove(objectId);
                            if (data == null) return null;

                            deIndexObject(objectId, data);
                            return deserializeObject(objectId, data);
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
                        })
        );
    }

    public void removeData(UUID id) {
        byte[] data = unloadedObjectsData.remove(id);
        if (data != null) {
            deIndexObject(id, data);
        }
    }

    private VxAbstractBody deserializeObject(UUID id, byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            String typeId = buf.readUtf();
            VxAbstractBody obj = objectManager.getObjectRegistry().create(typeId, objectManager.getPhysicsWorld(), id);
            if (obj == null) {
                VxMainClass.LOGGER.error("Failed to create object of type {} with ID {} during deserialization. Registry might be missing this type.", typeId, id);
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
        FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(buffer);
        try {
            friendlyBuf.writeUtf(object.getType().getTypeId());
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
        unloadedChunkIndex.computeIfAbsent(chunkKey, k -> new CopyOnWriteArrayList<>()).add(id);
    }

    private void deIndexObject(UUID id, byte[] data) {
        long chunkKey = getChunkKeyFromData(data);
        List<UUID> idList = unloadedChunkIndex.get(chunkKey);
        if (idList != null) {
            idList.remove(id);
            if (idList.isEmpty()) {
                unloadedChunkIndex.remove(chunkKey);
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