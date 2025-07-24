package net.xmx.vortex.physics.constraint.persistence;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.physics.constraint.IConstraint;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ConstraintStorage {
    private final Path dataFile;
    private final Map<UUID, byte[]> unloadedConstraintsData = new ConcurrentHashMap<>();

    public ConstraintStorage(ServerLevel level) {
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        Path dimensionRoot = DimensionType.getStorageFolder(level.dimension(), worldRoot);
        this.dataFile = dimensionRoot.resolve("vortex").resolve("constraints.bin");
    }

    public void loadFromFile() {
        unloadedConstraintsData.clear();
        if (!Files.exists(dataFile)) return;

        try (FileChannel channel = FileChannel.open(dataFile, StandardOpenOption.READ)) {
            if (channel.size() == 0) return;

            ByteBuffer buffer = ByteBuffer.allocateDirect((int) channel.size());
            channel.read(buffer);
            buffer.flip();

            FriendlyByteBuf fileBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(buffer));
            while (fileBuf.isReadable()) {
                UUID id = fileBuf.readUUID();
                byte[] data = fileBuf.readByteArray();
                unloadedConstraintsData.put(id, data);
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to load constraints from {}. File might be corrupt.", dataFile, e);
            unloadedConstraintsData.clear();
        }
    }

    public void saveToFile(Collection<IConstraint> activeConstraints) {
        Map<UUID, byte[]> allData = new ConcurrentHashMap<>(unloadedConstraintsData);
        for (IConstraint c : activeConstraints) {
            ByteBuf buffer = Unpooled.buffer();
            try {
                c.save(new FriendlyByteBuf(buffer));
                byte[] data = new byte[buffer.readableBytes()];
                buffer.readBytes(data);
                allData.put(c.getId(), data);
            } finally {
                ReferenceCountUtil.release(buffer);
            }
        }

        ByteBuf fileBuffer = Unpooled.buffer();
        try {
            Files.createDirectories(dataFile.getParent());
            FriendlyByteBuf buf = new FriendlyByteBuf(fileBuffer);

            for (Map.Entry<UUID, byte[]> entry : allData.entrySet()) {
                buf.writeUUID(entry.getKey());
                buf.writeByteArray(entry.getValue());
            }

            try (FileChannel channel = FileChannel.open(dataFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                if (fileBuffer.readableBytes() > 0) {
                    channel.write(fileBuffer.nioBuffer());
                } else {
                    channel.truncate(0);
                }
            }
        } catch(IOException e) {
            VxMainClass.LOGGER.error("Failed to save constraints to {}", dataFile, e);
        } finally {
            ReferenceCountUtil.release(fileBuffer);
        }
    }

    public Map<UUID, byte[]> getUnloadedConstraintsData() {
        return unloadedConstraintsData;
    }

    /**
     * Ruft die Daten ab und entfernt sie aus dem In-Memory-Cache, um doppeltes Laden zu verhindern.
     * @param id Die UUID des Constraints.
     * @return Die serialisierten Daten oder null, wenn nicht vorhanden.
     */
    public byte[] takeConstraintData(UUID id) {
        return unloadedConstraintsData.remove(id);
    }

    public void storeConstraintData(IConstraint constraint) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            FriendlyByteBuf buf = new FriendlyByteBuf(buffer);
            constraint.save(buf);
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            unloadedConstraintsData.put(constraint.getId(), data);
        } finally {
            ReferenceCountUtil.release(buffer);
        }
    }

    public void removeConstraintData(UUID id) {
        unloadedConstraintsData.remove(id);
    }

    public void clearData(){
        unloadedConstraintsData.clear();
    }
}