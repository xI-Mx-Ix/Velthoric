package net.xmx.velthoric.physics.terrain.cache;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.github.stephengold.joltjni.std.StringStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.physics.terrain.TerrainSystem;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TerrainStorage {

    private final Path dataFile;
    private final TerrainSystem terrainSystem;
    private final TerrainShapeCache shapeCache;

    private final Map<Integer, byte[]> preloadedShapes = new ConcurrentHashMap<>();

    public TerrainStorage(ServerLevel level, TerrainSystem terrainSystem, TerrainShapeCache shapeCache) {
        this.terrainSystem = terrainSystem;
        this.shapeCache = shapeCache;
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        Path dimensionRoot = DimensionType.getStorageFolder(level.dimension(), worldRoot);
        this.dataFile = dimensionRoot.resolve("velthoric").resolve("terrain.bin");
    }

    public void initialize() {
        loadFromFile();
    }

    public void saveToFile() {
        Map<Integer, ShapeRefC> entries = shapeCache.getEntries();
        if (entries.isEmpty()) {
            try {
                Files.deleteIfExists(dataFile);
            } catch (IOException e) {
                VxMainClass.LOGGER.error("Failed to delete empty terrain cache file {}", dataFile, e);
            }
            return;
        }

        ByteBuf masterBuf = Unpooled.buffer();
        FriendlyByteBuf friendlyMasterBuf = new FriendlyByteBuf(masterBuf);

        for (Map.Entry<Integer, ShapeRefC> entry : entries.entrySet()) {
            Integer hash = entry.getKey();
            ShapeRefC shapeRef = entry.getValue();
            if (shapeRef == null || shapeRef.getPtr() == null) continue;

            try (StringStream stringStream = new StringStream();
                 StreamOutWrapper streamOut = new StreamOutWrapper(stringStream)) {

                shapeRef.saveBinaryState(streamOut);
                byte[] shapeData = stringStream.str().getBytes();

                if (shapeData.length > 0) {
                    friendlyMasterBuf.writeInt(hash);
                    friendlyMasterBuf.writeInt(shapeData.length);
                    friendlyMasterBuf.writeBytes(shapeData);
                }
            }
        }

        if (masterBuf.readableBytes() > 0) {
            try {
                Files.createDirectories(dataFile.getParent());
                try (FileChannel channel = FileChannel.open(dataFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    channel.write(masterBuf.nioBuffer());
                }
            } catch (IOException e) {
                VxMainClass.LOGGER.error("Failed to save terrain cache to {}", dataFile, e);
            }
        }

        if (masterBuf.refCnt() > 0) {
            masterBuf.release();
        }
    }

    private void loadFromFile() {
        if (!Files.exists(dataFile)) return;
        preloadedShapes.clear();

        try (FileChannel channel = FileChannel.open(dataFile, StandardOpenOption.READ)) {
            if (channel.size() == 0) return;

            ByteBuffer nioBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            ByteBuf buffer = Unpooled.wrappedBuffer(nioBuffer);
            FriendlyByteBuf fileBuf = new FriendlyByteBuf(buffer);

            while (fileBuf.isReadable()) {
                int hash = fileBuf.readInt();
                int dataLength = fileBuf.readInt();

                if (dataLength > 0 && fileBuf.isReadable(dataLength)) {
                    byte[] shapeData = new byte[dataLength];
                    fileBuf.readBytes(shapeData);
                    preloadedShapes.put(hash, shapeData);
                }
            }
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to load terrain cache from {}", dataFile, e);
        }

        if (!preloadedShapes.isEmpty()) {
            VxMainClass.LOGGER.info("Pre-loaded {} serialized terrain shapes from cache for dimension {}", preloadedShapes.size(), terrainSystem.getLevel().dimension().location());
        }
    }

    public ShapeRefC shapeFromPreload(int hash) {
        byte[] shapeData = preloadedShapes.get(hash);
        if (shapeData == null) {
            return null;
        }

        try (StringStream stringStream = new StringStream(new String(shapeData));
             StreamInWrapper streamIn = new StreamInWrapper(stringStream)) {

            try (ShapeResult result = Shape.sRestoreFromBinaryState(streamIn)) {
                if (result.isValid()) {
                    return result.get();
                } else {
                    VxMainClass.LOGGER.warn("Failed to deserialize shape with hash {}: {}", hash, result.getError());
                    result.get();
                    result.get().close();
                }
            }
        }
        return null;
    }
}