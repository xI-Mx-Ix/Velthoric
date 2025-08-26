package net.xmx.velthoric.physics.constraint.persistence;

import com.github.stephengold.joltjni.enumerate.EConstraintSubType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.constraint.VxConstraint;
import net.xmx.velthoric.physics.constraint.manager.VxConstraintManager;
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

public class VxConstraintStorage {

    private final Path dataFile;
    private final VxConstraintManager constraintManager;
    private final ServerLevel level;
    private final ConcurrentMap<UUID, byte[]> unloadedConstraintsData = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, List<UUID>> unloadedChunkIndex = new ConcurrentHashMap<>();
    private ExecutorService loaderExecutor;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    public VxConstraintStorage(ServerLevel level, VxConstraintManager constraintManager) {
        this.constraintManager = constraintManager;
        this.level = level;
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        Path dimensionRoot = DimensionType.getStorageFolder(level.dimension(), worldRoot);
        this.dataFile = dimensionRoot.resolve("velthoric").resolve("constraints.vxdat");
    }

    public void initialize() {
        if (isInitialized.getAndSet(true)) return;
        this.loaderExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Velthoric Constraint Loader"));
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
        try {
            byte[] fileBytes = Files.readAllBytes(dataFile);
            if (fileBytes.length == 0) return;

            ByteBuf buffer = Unpooled.wrappedBuffer(fileBytes);
            FriendlyByteBuf fileBuf = new FriendlyByteBuf(buffer);
            while (fileBuf.isReadable()) {
                UUID id = fileBuf.readUUID();
                byte[] data = fileBuf.readByteArray();
                unloadedConstraintsData.put(id, data);
            }
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to load physics constraints from {}", dataFile, e);
        }
        rebuildChunkIndex();
    }

    private void rebuildChunkIndex() {
        unloadedChunkIndex.clear();
        unloadedConstraintsData.forEach(this::indexConstraintData);
    }

    public synchronized void saveToFile() {
        ByteBuf masterBuf = Unpooled.buffer();
        try {
            FriendlyByteBuf friendlyMasterBuf = new FriendlyByteBuf(masterBuf);
            for (Map.Entry<UUID, byte[]> entry : unloadedConstraintsData.entrySet()) {
                friendlyMasterBuf.writeUUID(entry.getKey());
                friendlyMasterBuf.writeByteArray(entry.getValue());
            }

            if (masterBuf.readableBytes() > 0) {
                Files.createDirectories(dataFile.getParent());
                try (FileChannel channel = FileChannel.open(dataFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    channel.write(masterBuf.nioBuffer());
                }
            } else {
                Files.deleteIfExists(dataFile);
            }
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to save physics constraints to {}", dataFile, e);
        } finally {
            if (masterBuf.refCnt() > 0) {
                masterBuf.release();
            }
        }
    }

    public void storeConstraint(VxConstraint constraint) {
        if (constraint == null) return;
        constraintManager.getObjectManager().getObject(constraint.getBody1Id()).ifPresent(body1 -> {
            ChunkPos chunkPos = VxObjectManager.getObjectChunkPos(body1);
            byte[] data = serializeConstraintData(constraint, chunkPos);
            unloadedConstraintsData.put(constraint.getConstraintId(), data);
            indexConstraintData(constraint.getConstraintId(), data);
        });
    }

    public void loadConstraintsInChunk(ChunkPos chunkPos) {
        List<UUID> idsToLoad = unloadedChunkIndex.get(chunkPos.toLong());
        if (idsToLoad == null || idsToLoad.isEmpty()) return;
        for (UUID id : List.copyOf(idsToLoad)) {
            if (constraintManager.hasActiveConstraint(id) || constraintManager.getDataSystem().isPending(id)) continue;
            loadConstraint(id);
        }
    }

    public void loadConstraint(UUID id) {
        CompletableFuture.runAsync(() -> {
            byte[] data = unloadedConstraintsData.remove(id);
            if (data == null) return;
            deIndexConstraint(id, data);
            VxConstraint constraint = deserializeConstraint(id, data);
            level.getServer().execute(() -> constraintManager.addConstraintFromStorage(constraint));
        }, loaderExecutor).exceptionally(ex -> {
            VxMainClass.LOGGER.error("Exception loading physics constraint {}", id, ex);
            return null;
        });
    }

    public void removeData(UUID id) {
        byte[] data = unloadedConstraintsData.remove(id);
        if (data != null) {
            deIndexConstraint(id, data);
        }
    }

    private VxConstraint deserializeConstraint(UUID id, byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            buf.readLong();
            UUID body1Id = buf.readUUID();
            UUID body2Id = buf.readUUID();
            EConstraintSubType subType = EConstraintSubType.values()[buf.readInt()];
            byte[] settingsData = buf.readByteArray();
            return new VxConstraint(id, body1Id, body2Id, settingsData, subType);
        } finally {
            if (buf.refCnt() > 0) {
                buf.release();
            }
        }
    }

    private byte[] serializeConstraintData(VxConstraint constraint, ChunkPos pos) {
        ByteBuf buffer = Unpooled.buffer();
        FriendlyByteBuf buf = new FriendlyByteBuf(buffer);
        try {
            buf.writeLong(pos.toLong());
            buf.writeUUID(constraint.getBody1Id());
            buf.writeUUID(constraint.getBody2Id());
            buf.writeInt(constraint.getSubType().ordinal());
            buf.writeByteArray(constraint.getSettingsData());

            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return data;
        } finally {
            if (buffer.refCnt() > 0) {
                buffer.release();
            }
        }
    }

    private void indexConstraintData(UUID id, byte[] data) {
        long chunkKey = getChunkKeyFromData(data);
        unloadedChunkIndex.computeIfAbsent(chunkKey, k -> new CopyOnWriteArrayList<>()).add(id);
    }

    private void deIndexConstraint(UUID id, byte[] data) {
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
            return buf.readLong();
        } finally {
            if (buf.refCnt() > 0) {
                buf.release();
            }
        }
    }
}