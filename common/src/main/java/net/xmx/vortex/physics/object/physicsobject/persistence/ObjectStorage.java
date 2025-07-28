package net.xmx.vortex.physics.object.physicsobject.persistence;

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
import java.util.concurrent.ConcurrentHashMap;

public class ObjectStorage {
    private final Path dataFile;
    private final Map<UUID, byte[]> unloadedObjectsData = new ConcurrentHashMap<>();
    private final Long2ObjectMap<List<UUID>> unloadedChunkIndex = new Long2ObjectOpenHashMap<>();
    private final VxObjectManager objectManager;

    public ObjectStorage(ServerLevel level, VxObjectManager objectManager) {
        this.objectManager = objectManager;
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        Path dimensionRoot = DimensionType.getStorageFolder(level.dimension(), worldRoot);
        this.dataFile = dimensionRoot.resolve("vortex").resolve("bodies.bin");
    }

    public void loadFromFile() {
        if (!Files.exists(dataFile)) {
            return;
        }

        try (FileChannel channel = FileChannel.open(dataFile, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            if (fileSize == 0) return;

            ByteBuffer buffer = ByteBuffer.allocateDirect((int) fileSize);
            channel.read(buffer);
            buffer.flip();

            FriendlyByteBuf fileBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(buffer));
            while (fileBuf.isReadable()) {
                UUID id = fileBuf.readUUID();
                byte[] data = fileBuf.readByteArray();
                unloadedObjectsData.put(id, data);
            }
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to load physics objects from {}", dataFile, e);
        } catch (Exception e) {

            VxMainClass.LOGGER.error("Corrupted physics object data found in {}. File might be corrupt. Clearing data to prevent crash.", dataFile, e);
            unloadedObjectsData.clear();
        }

        rebuildChunkIndex();
    }

    private void rebuildChunkIndex() {
        unloadedChunkIndex.clear();
        VxTransform tempTransform = new VxTransform();
        unloadedObjectsData.forEach((id, data) -> {
            try {
                FriendlyByteBuf dataBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
                dataBuf.readUtf();
                tempTransform.fromBuffer(dataBuf);
                long chunkKey = ChunkPos.asLong(SectionPos.posToSectionCoord(tempTransform.getTranslation().x()), SectionPos.posToSectionCoord(tempTransform.getTranslation().z()));
                unloadedChunkIndex.computeIfAbsent(chunkKey, k -> new ObjectArrayList<>()).add(id);
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Failed to read transform for indexing object {}. Data might be corrupt.", id, e);
            }
        });
    }

    public void saveToFile(Collection<IPhysicsObject> activeObjects) {

        ByteBuf buffer = null;
        try {
            Files.createDirectories(dataFile.getParent());

            buffer = Unpooled.buffer();
            FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(buffer);

            for (IPhysicsObject obj : activeObjects) {
                if (obj.isRemoved()) continue;
                friendlyBuf.writeUUID(obj.getPhysicsId());
                ByteBuf objDataBuffer = Unpooled.buffer();
                try {
                    obj.writeData(new FriendlyByteBuf(objDataBuffer));

                    byte[] objData = new byte[objDataBuffer.readableBytes()];
                    objDataBuffer.readBytes(objData);
                    friendlyBuf.writeByteArray(objData);
                } finally {
                    if(objDataBuffer.refCnt() > 0) {
                        objDataBuffer.release();
                    }
                }
            }

            for (Map.Entry<UUID, byte[]> entry : unloadedObjectsData.entrySet()) {
                friendlyBuf.writeUUID(entry.getKey());
                friendlyBuf.writeByteArray(entry.getValue());
            }

            try (FileChannel channel = FileChannel.open(dataFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                if(buffer.readableBytes() > 0) {
                    channel.write(buffer.nioBuffer());
                } else {
                    channel.truncate(0);
                }
            }

        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to save physics objects to {}", dataFile, e);
        } finally {

            if (buffer != null && buffer.refCnt() > 0) {
                buffer.release();
            }
        }
    }

    public byte[] getObjectData(UUID id) {
        return unloadedObjectsData.get(id);
    }

    public List<UUID> getObjectIdsInChunk(ChunkPos chunkPos) {
        return unloadedChunkIndex.get(chunkPos.toLong());
    }

    public void removeObjectData(UUID id) {
        byte[] data = unloadedObjectsData.remove(id);
        if (data != null) {

            try {
                FriendlyByteBuf dataBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
                dataBuf.readUtf();
                VxTransform tempTransform = new VxTransform();
                tempTransform.fromBuffer(dataBuf);
                long chunkKey = ChunkPos.asLong(SectionPos.posToSectionCoord(tempTransform.getTranslation().x()), SectionPos.posToSectionCoord(tempTransform.getTranslation().z()));
                List<UUID> list = unloadedChunkIndex.get(chunkKey);
                if (list != null) {
                    list.remove(id);
                    if (list.isEmpty()) {
                        unloadedChunkIndex.remove(chunkKey);
                    }
                }
            } catch (Exception ignored) {

            }
        }
    }

    public void storeObjectData(IPhysicsObject object) {
        if (object == null || object.isRemoved()) return;

        ByteBuf buffer = Unpooled.buffer();
        try {
            FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(buffer);
            object.writeData(friendlyBuf);

            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);

            unloadedObjectsData.put(object.getPhysicsId(), data);
            long chunkKey = ChunkPos.asLong(SectionPos.posToSectionCoord(object.getCurrentTransform().getTranslation().x()), SectionPos.posToSectionCoord(object.getCurrentTransform().getTranslation().z()));
            unloadedChunkIndex.computeIfAbsent(chunkKey, k -> new ObjectArrayList<>()).add(object.getPhysicsId());
        } finally {
            if (buffer.refCnt() > 0) {
                buffer.release();
            }
        }
    }

    public void removeLoadedChunkData(ChunkPos chunkPos) {
        unloadedChunkIndex.remove(chunkPos.toLong());
    }

    public byte[] takeObjectData(UUID id) {
        return unloadedObjectsData.remove(id);
    }

    public boolean hasData(UUID id) {
        return unloadedObjectsData.containsKey(id) || objectManager.getObject(id).isPresent();
    }

    public boolean isObjectInChunk(UUID id, ChunkPos chunkPos) {
        byte[] data = unloadedObjectsData.get(id);
        if (data == null) return false;

        try {
            FriendlyByteBuf dataBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
            dataBuf.readUtf();
            VxTransform tempTransform = new VxTransform();
            tempTransform.fromBuffer(dataBuf);
            long chunkKey = ChunkPos.asLong(SectionPos.posToSectionCoord(tempTransform.getTranslation().x()), SectionPos.posToSectionCoord(tempTransform.getTranslation().z()));
            return chunkKey == chunkPos.toLong();
        } catch (Exception e) {
            return false;
        }
    }
}