package net.xmx.velthoric.physics.persistence;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.persistence.VxAbstractRegionStorage.RegionPos;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class VxRegionIndex {

    private final Path indexPath;
    private final ConcurrentHashMap<UUID, RegionPos> index = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public VxRegionIndex(Path storagePath, String indexName) {
        this.indexPath = storagePath.resolve(indexName + ".vxidx");
    }

    public void load() {
        if (!Files.exists(indexPath)) return;

        try {
            byte[] fileBytes = Files.readAllBytes(indexPath);
            if (fileBytes.length == 0) return;

            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(fileBytes));
            while (buffer.isReadable()) {
                UUID id = buffer.readUUID();
                int x = buffer.readInt();
                int z = buffer.readInt();
                index.put(id, new RegionPos(x, z));
            }
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to load region index from {}", indexPath, e);
        }
    }

    public void save() {
        if (!dirty.getAndSet(false)) return;

        if (index.isEmpty()) {
            try {
                Files.deleteIfExists(indexPath);
            } catch (IOException e) {
                VxMainClass.LOGGER.error("Failed to delete empty region index file {}", indexPath, e);
            }
            return;
        }

        ByteBuf masterBuf = Unpooled.buffer();
        try {
            FriendlyByteBuf friendlyMasterBuf = new FriendlyByteBuf(masterBuf);
            index.forEach((id, pos) -> {
                friendlyMasterBuf.writeUUID(id);
                friendlyMasterBuf.writeInt(pos.x());
                friendlyMasterBuf.writeInt(pos.z());
            });

            Files.createDirectories(indexPath.getParent());
            try (FileChannel channel = FileChannel.open(indexPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                channel.write(masterBuf.nioBuffer());
            }
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to save region index to {}", indexPath, e);
        } finally {
            if (masterBuf.refCnt() > 0) {
                masterBuf.release();
            }
        }
    }

    public void put(UUID id, RegionPos pos) {
        RegionPos oldPos = index.put(id, pos);
        if (!pos.equals(oldPos)) {
            dirty.set(true);
        }
    }

    public RegionPos get(UUID id) {
        return index.get(id);
    }

    public void remove(UUID id) {
        if (index.remove(id) != null) {
            dirty.set(true);
        }
    }
}