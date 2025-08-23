package net.xmx.velthoric.physics.terrain.cache;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.readonly.ConstShape;
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
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TerrainStorage {

    private final Path dataFile;
    private final TerrainSystem terrainSystem;
    private final TerrainShapeCache shapeCache;

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

            ConstShape shape = shapeRef.getPtr();

            int numTriangles = shape.countDebugTriangles();
            if (numTriangles == 0) continue;

            int numFloats = numTriangles * 3 * 3;
            FloatBuffer buffer = Jolt.newDirectFloatBuffer(numFloats);
            shape.copyDebugTriangles(buffer);

            float[] vertexData = new float[numFloats];
            buffer.get(0, vertexData);

            friendlyMasterBuf.writeInt(hash);
            friendlyMasterBuf.writeInt(vertexData.length);
            for (float f : vertexData) {
                friendlyMasterBuf.writeFloat(f);
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

        int loadedCount = 0;
        try (FileChannel channel = FileChannel.open(dataFile, StandardOpenOption.READ)) {
            if (channel.size() == 0) return;

            ByteBuffer nioBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            ByteBuf buffer = Unpooled.wrappedBuffer(nioBuffer);
            FriendlyByteBuf fileBuf = new FriendlyByteBuf(buffer);

            while (fileBuf.isReadable()) {
                int hash = fileBuf.readInt();

                // Manually read the float array
                int arrayLength = fileBuf.readInt();
                float[] vertexData = new float[arrayLength];
                for (int i = 0; i < arrayLength; i++) {
                    vertexData[i] = fileBuf.readFloat();
                }

                if (vertexData.length == 0 || vertexData.length % 9 != 0) {
                    VxMainClass.LOGGER.warn("Invalid vertex data for shape with hash {}", hash);
                    continue;
                }

                List<Triangle> triangles = new ArrayList<>(vertexData.length / 9);
                MeshShapeSettings settings = null;
                ShapeResult result = null;

                try {
                    for (int i = 0; i < vertexData.length; i += 9) {
                        Float3 v1 = new Float3(vertexData[i], vertexData[i + 1], vertexData[i + 2]);
                        Float3 v2 = new Float3(vertexData[i + 3], vertexData[i + 4], vertexData[i + 5]);
                        Float3 v3 = new Float3(vertexData[i + 6], vertexData[i + 7], vertexData[i + 8]);
                        triangles.add(new Triangle(v1, v2, v3));
                    }

                    settings = new MeshShapeSettings(triangles);
                    result = settings.create();

                    if (result.isValid()) {
                        shapeCache.put(hash, result.get());
                        loadedCount++;
                    } else {
                        VxMainClass.LOGGER.warn("Failed to load shape from mesh data with hash {}: {}", hash, result.getError());
                        if (result.get() != null) result.get().close();
                    }
                } finally {
                    if (result != null) result.close();
                    if (settings != null) settings.close();
                    for (Triangle t : triangles) {
                        t.close();
                    }
                }
            }
        } catch (IOException e) {
            VxMainClass.LOGGER.error("Failed to load terrain cache from {}", dataFile, e);
        }

        if (loadedCount > 0) {
            VxMainClass.LOGGER.info("Loaded {} terrain shapes from cache for dimension {}", loadedCount, terrainSystem.getLevel().dimension().location());
        }
    }
}