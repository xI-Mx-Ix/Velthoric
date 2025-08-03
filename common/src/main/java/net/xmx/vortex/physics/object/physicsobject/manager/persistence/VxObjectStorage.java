package net.xmx.vortex.physics.object.physicsobject.manager.persistence;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.manager.VxObjectManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class VxObjectStorage {

    private final Path dataFile;
    private final Map<UUID, byte[]> unloadedObjectsData = new ConcurrentHashMap<>();
    private final Long2ObjectMap<List<UUID>> unloadedChunkIndex = new Long2ObjectOpenHashMap<>();
    private final Map<UUID, CompletableFuture<IPhysicsObject>> pendingLoads = new ConcurrentHashMap<>();
    private final VxObjectManager objectManager;
    private final ServerLevel level;
    private ExecutorService loaderExecutor;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    public VxObjectStorage(ServerLevel level, VxObjectManager objectManager) {
        this.objectManager = objectManager;
        this.level = level;
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        Path dimensionRoot = DimensionType.getStorageFolder(level.dimension(), worldRoot);
        this.dataFile = dimensionRoot.resolve("vortex").resolve("bodies.bin");
    }

    public void initialize() {
        if (isInitialized.getAndSet(true)) {
            return;
        }
        int threadCount = Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
        this.loaderExecutor = Executors.newFixedThreadPool(threadCount, r -> new Thread(r, "Vortex-Object-Loader"));
        this.loadFromFile();
    }

    public void shutdown() {
        if (!isInitialized.getAndSet(false)) {
            return;
        }
        if (loaderExecutor != null) {
            loaderExecutor.shutdown();
        }
    }

    private void loadFromFile() {
        if (!Files.exists(dataFile)) {
            return;
        }
        try (FileChannel channel = FileChannel.open(dataFile, StandardOpenOption.READ)) {
            if (channel.size() == 0) {
                return;
            }
            ByteBuffer buffer = ByteBuffer.allocateDirect((int) channel.size());
            channel.read(buffer);
            buffer.flip();
            FriendlyByteBuf fileBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(buffer));

            while (fileBuf.isReadable()) {
                unloadedObjectsData.put(fileBuf.readUUID(), fileBuf.readByteArray());
            }
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to load physics objects from {}", dataFile, e);
        }
        rebuildChunkIndex();
    }

    private void rebuildChunkIndex() {
        unloadedChunkIndex.clear();
        unloadedObjectsData.forEach((id, data) -> {
            try {
                FriendlyByteBuf dataBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
                dataBuf.readUtf();
                long chunkKey = getChunkKeyFromBuffer(dataBuf);
                unloadedChunkIndex.computeIfAbsent(chunkKey, k -> new ObjectArrayList<>()).add(id);
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Failed to index unloaded physics object {}", id, e);
            }
        });
    }

    public void saveAll(Collection<IPhysicsObject> activeObjects) {
        Map<UUID, byte[]> allObjectsToSave = new ConcurrentHashMap<>(unloadedObjectsData);

        activeObjects.forEach(obj -> {
            if (!obj.isRemoved()) {
                allObjectsToSave.put(obj.getPhysicsId(), serializeObjectData(obj));
            }
        });

        try {
            Files.createDirectories(dataFile.getParent());
            try (FileChannel channel = FileChannel.open(dataFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                if (allObjectsToSave.isEmpty()) {
                    channel.truncate(0);
                    return;
                }

                for (Map.Entry<UUID, byte[]> entry : allObjectsToSave.entrySet()) {
                    ByteBuf tempBuf = Unpooled.buffer();
                    FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(tempBuf);

                    friendlyBuf.writeUUID(entry.getKey());
                    friendlyBuf.writeByteArray(entry.getValue());

                    channel.write(tempBuf.nioBuffer());
                    tempBuf.release();
                }
            }
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to save physics objects to {}", dataFile, e);
        }
    }

    public void loadObjectsInChunk(ChunkPos chunkPos) {
        List<UUID> idsToLoad = unloadedChunkIndex.get(chunkPos.toLong());
        if (idsToLoad == null || idsToLoad.isEmpty()) {
            return;
        }
        for (UUID id : new ObjectArrayList<>(idsToLoad)) {
            if (objectManager.getObjectContainer().hasObject(id)) {
                continue;
            }
            loadObject(id);
        }
    }

    public CompletableFuture<IPhysicsObject> loadObject(UUID id) {
        if (objectManager.getObjectContainer().hasObject(id)) {
            return CompletableFuture.completedFuture(objectManager.getObjectContainer().get(id).orElse(null));
        }
        return pendingLoads.computeIfAbsent(id, objectId -> {
            if (!hasData(objectId)) {
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.supplyAsync(() -> {
                        byte[] data = takeData(objectId);
                        if (data == null) {
                            return null;
                        }
                        return deserializeObject(objectId, data);
                    }, loaderExecutor)
                    .thenApplyAsync(obj -> {
                        if (obj != null) {
                            objectManager.getObjectContainer().add(obj);
                            removeDataFromChunkIndex(obj.getPhysicsId(), VxObjectManager.getObjectChunkPos(obj));
                        }
                        return obj;
                    }, level.getServer())
                    .whenComplete((obj, ex) -> {
                        pendingLoads.remove(id);
                    });
        });
    }

    private IPhysicsObject deserializeObject(UUID id, byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            String typeId = buf.readUtf();
            IPhysicsObject obj = objectManager.getObjectRegistry().create(typeId, level);
            if (obj == null) {
                VxMainClass.LOGGER.error("Failed to create object of type {} during deserialization.", typeId);
                return null;
            }
            obj.setPhysicsId(id);
            obj.readSpawnData(buf);
            return obj;
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to deserialize physics object {}", id, e);
            return null;
        }
    }

    private byte[] serializeObjectData(IPhysicsObject object) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(buffer);
            friendlyBuf.writeUtf(object.getObjectTypeIdentifier());
            object.writeSpawnData(friendlyBuf);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return data;
        } finally {
            if (buffer.refCnt() > 0) {
                buffer.release();
            }
        }
    }

    private void updateChunkIndex(UUID id, byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        buf.readUtf();
        long chunkKey = getChunkKeyFromBuffer(buf);
        unloadedChunkIndex.computeIfAbsent(chunkKey, k -> new ObjectArrayList<>()).add(id);
    }

    public void saveData(IPhysicsObject object) {
        byte[] data = serializeObjectData(object);
        unloadedObjectsData.put(object.getPhysicsId(), data);
        updateChunkIndex(object.getPhysicsId(), data);
    }

    public void removeData(UUID id) {
        byte[] data = unloadedObjectsData.remove(id);
        if (data != null) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
            buf.readUtf();
            long chunkKey = getChunkKeyFromBuffer(buf);
            removeDataFromChunkIndex(id, new ChunkPos(chunkKey));
        }
    }

    private void removeDataFromChunkIndex(UUID id, ChunkPos chunkPos) {
        long chunkKey = chunkPos.toLong();
        List<UUID> list = unloadedChunkIndex.get(chunkKey);
        if (list != null) {
            list.remove(id);
            if (list.isEmpty()) {
                unloadedChunkIndex.remove(chunkKey);
            }
        }
    }

    private long getChunkKeyFromBuffer(FriendlyByteBuf buf) {
        VxTransform tempTransform = new VxTransform();
        tempTransform.fromBuffer(buf);
        return ChunkPos.asLong(SectionPos.posToSectionCoord(tempTransform.getTranslation().x()), SectionPos.posToSectionCoord(tempTransform.getTranslation().z()));
    }

    public byte[] takeData(UUID id) {
        return unloadedObjectsData.remove(id);
    }

    public boolean hasData(UUID id) {
        return unloadedObjectsData.containsKey(id);
    }
}