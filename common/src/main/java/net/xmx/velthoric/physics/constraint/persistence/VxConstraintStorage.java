package net.xmx.velthoric.physics.constraint.persistence;

import com.github.stephengold.joltjni.enumerate.EConstraintSubType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.constraint.VxConstraint;
import net.xmx.velthoric.physics.constraint.manager.VxConstraintManager;
import net.xmx.velthoric.physics.object.manager.VxObjectManager;
import net.xmx.velthoric.physics.persistence.VxAbstractRegionStorage;
import net.xmx.velthoric.physics.persistence.VxRegionIndex;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class VxConstraintStorage extends VxAbstractRegionStorage<UUID, byte[]> {

    private final VxConstraintManager constraintManager;
    private final ConcurrentMap<Long, List<UUID>> chunkToUuidIndex = new ConcurrentHashMap<>();
    private ExecutorService loaderExecutor;

    public VxConstraintStorage(ServerLevel level, VxConstraintManager constraintManager) {
        super(level, "constraint", "constraint");
        this.constraintManager = constraintManager;
    }

    @Override
    protected VxRegionIndex createRegionIndex() {
        return new VxRegionIndex(storagePath, "constraint");
    }

    @Override
    public void initialize() {
        super.initialize();
        this.loaderExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Velthoric Constraint Loader"));
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
            indexConstraintData(id, data);
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

    public void storeConstraint(VxConstraint constraint) {
        if (constraint == null) return;
        constraintManager.getObjectManager().getObject(constraint.getBody1Id()).ifPresent(body1 -> {
            ChunkPos chunkPos = VxObjectManager.getObjectChunkPos(body1);
            byte[] data = serializeConstraintData(constraint, chunkPos);
            RegionPos regionPos = getRegionPos(chunkPos);

            getRegion(regionPos).thenAcceptAsync(region -> {
                region.entries.put(constraint.getConstraintId(), data);
                region.dirty.set(true);
                regionIndex.put(constraint.getConstraintId(), regionPos);
                indexConstraintData(constraint.getConstraintId(), data);
            }, loaderExecutor).exceptionally(ex -> {
                VxMainClass.LOGGER.error("Failed to store constraint {}", constraint.getConstraintId(), ex);
                return null;
            });
        });
    }

    public void loadConstraintsInChunk(ChunkPos chunkPos) {
        RegionPos regionPos = getRegionPos(chunkPos);
        getRegion(regionPos).thenRunAsync(() -> {
            List<UUID> idsToLoad = chunkToUuidIndex.get(chunkPos.toLong());
            if (idsToLoad == null || idsToLoad.isEmpty()) return;
            for (UUID id : List.copyOf(idsToLoad)) {
                if (constraintManager.hasActiveConstraint(id) || constraintManager.getDataSystem().isPending(id)) continue;
                loadConstraint(id);
            }
        }, loaderExecutor).exceptionally(ex -> {
            VxMainClass.LOGGER.error("Failed to load constraints in chunk {}", chunkPos, ex);
            return null;
        });
    }

    public void loadConstraint(UUID id) {
        CompletableFuture.supplyAsync(() -> {
            RegionPos regionPos = regionIndex.get(id);
            if (regionPos == null) return null;

            RegionData<UUID, byte[]> region = getRegion(regionPos).join();
            byte[] data = region.entries.get(id);
            if (data != null) {

                return deserializeConstraint(id, data);
            }
            return null;
        }, loaderExecutor).thenAcceptAsync(constraint -> {
            if (constraint != null) {
                constraintManager.addConstraintFromStorage(constraint);

                removeData(id);
            }
        }, level.getServer()).exceptionally(ex -> {
            VxMainClass.LOGGER.error("Exception loading physics constraint {}", id, ex);
            return null;
        });
    }

    public void removeData(UUID id) {
        RegionPos regionPos = regionIndex.get(id);
        if (regionPos == null) return;

        getRegion(regionPos).thenAcceptAsync(region -> {
            byte[] data = region.entries.remove(id);
            if (data != null) {
                region.dirty.set(true);
                deIndexConstraint(id, data);
                regionIndex.remove(id);
            }
        }, loaderExecutor).exceptionally(ex -> {
            VxMainClass.LOGGER.error("Failed to remove data for constraint {}", id, ex);
            return null;
        });
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
        chunkToUuidIndex.computeIfAbsent(chunkKey, k -> new CopyOnWriteArrayList<>()).add(id);
    }

    private void deIndexConstraint(UUID id, byte[] data) {
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
            return buf.readLong();
        } finally {
            if (buf.refCnt() > 0) {
                buf.release();
            }
        }
    }
}