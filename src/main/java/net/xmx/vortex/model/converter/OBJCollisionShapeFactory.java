package net.xmx.vortex.model.converter;

import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.ConvexHullShapeSettings;
import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class OBJCollisionShapeFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(OBJCollisionShapeFactory.class);

    private static final ResourceManagerProvider RESOURCE_MANAGER_PROVIDER =
            DistExecutor.unsafeRunForDist(
                    () -> ClientResourceManagerProvider::new,
                    () -> ServerResourceManagerProvider::new
            );

    private static final int MAX_HULL_VERTICES = 256;

    private static final class CollisionShapeKey {
        private final ResourceLocation modelLocation;
        private final Vec3 scale;

        public CollisionShapeKey(ResourceLocation modelLocation, Vec3 scale) {
            this.modelLocation = modelLocation;
            this.scale = new Vec3(scale);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CollisionShapeKey that = (CollisionShapeKey) o;
            return modelLocation.equals(that.modelLocation) && scale.equals(that.scale);
        }

        @Override
        public int hashCode() {
            return Objects.hash(modelLocation, scale);
        }
    }

    private static final LoadingCache<CollisionShapeKey, ConstShape> COLLISION_SHAPE_CACHE = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build(new CacheLoader<>() {
                @Override
                public @NotNull ConstShape load(@NotNull CollisionShapeKey key) throws Exception {
                    return createSimplifiedShapeFromObj(key.modelLocation, key.scale);
                }
            });

    public static ConstShape getCollisionShape(ResourceLocation modelLocation) {
        return getCollisionShape(modelLocation, new Vec3(1f, 1f, 1f));
    }

    public static ConstShape getCollisionShape(ResourceLocation modelLocation, Vec3 scale) {
        try {
            CollisionShapeKey key = new CollisionShapeKey(modelLocation, scale);
            return COLLISION_SHAPE_CACHE.get(key);
        } catch (ExecutionException e) {
            LOGGER.error("Error while creating/retrieving collision shape for OBJ: {}", modelLocation, e.getCause() != null ? e.getCause() : e);
            return createFallbackShape(scale);
        } catch (Exception e) {
            LOGGER.error("Unexpected error while accessing cache for OBJ: {}", modelLocation, e);
            return createFallbackShape(scale);
        }
    }

    private static ConstShape createFallbackShape(Vec3 scale) {

        return new BoxShape(0.1f * scale.getX(), 0.1f * scale.getY(), 0.1f * scale.getZ());
    }

    private interface ResourceManagerProvider {
        ResourceManager get();
    }

    private static class ClientResourceManagerProvider implements ResourceManagerProvider {
        @Override
        public ResourceManager get() {
            return Minecraft.getInstance().getResourceManager();
        }
    }

    private static class ServerResourceManagerProvider implements ResourceManagerProvider {
        @Override
        public ResourceManager get() {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                LOGGER.error("Attempted to get Server ResourceManager, but server instance is null.");
                throw new IllegalStateException("Server resource manager not available.");
            }
            return server.getResourceManager();
        }
    }

    private static ConstShape createSimplifiedShapeFromObj(ResourceLocation modelLocation, Vec3 scale) throws IOException {
        LOGGER.debug("Creating collision shape for OBJ: {} with scale {}", modelLocation, scale);
        long startTime = System.nanoTime();

        ResourceManager resourceManager = RESOURCE_MANAGER_PROVIDER.get();
        Optional<Resource> resourceOptional = resourceManager.getResource(modelLocation);

        if (resourceOptional.isEmpty()) {
            throw new IOException("OBJ resource not found: " + modelLocation);
        }

        List<Vec3> vertices = new ArrayList<>();
        try (InputStream inputStream = resourceOptional.get().open();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("v ")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 4) {
                        try {
                            float x = Float.parseFloat(parts[1]) * scale.getX();
                            float y = Float.parseFloat(parts[2]) * scale.getY();
                            float z = Float.parseFloat(parts[3]) * scale.getZ();
                            vertices.add(new Vec3(x, y, z));
                        } catch (NumberFormatException e) {
                            LOGGER.warn("Invalid vertex format in OBJ {} (line '{}'). Skipped.", modelLocation, line);
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("IOException while reading OBJ: {}", modelLocation, e);
            throw e;
        } catch (Exception e) {
            LOGGER.error("Unexpected error while parsing OBJ: {}", modelLocation, e);
            throw new IOException("Parsing failed for " + modelLocation, e);
        }

        if (vertices.isEmpty()) {
            LOGGER.warn("No vertices found in OBJ {}. Using fallback shape.", modelLocation);
            return createFallbackShape(scale);
        }

        int originalVertexCount = vertices.size();
        List<Vec3> finalVertices = vertices;
        if (originalVertexCount > MAX_HULL_VERTICES) {
            LOGGER.debug("Simplifying collision shape for {} from {} to approx. {} vertices.", modelLocation, originalVertexCount, MAX_HULL_VERTICES);
            finalVertices = simplifyVertices(vertices, MAX_HULL_VERTICES);
        }

        FloatBuffer vertexBuffer = Jolt.newDirectFloatBuffer(finalVertices.size() * 3);
        for (Vec3 vertex : finalVertices) {
            vertex.put(vertexBuffer);
        }
        vertexBuffer.flip();

        try (ConvexHullShapeSettings settings = new ConvexHullShapeSettings(finalVertices.size(), vertexBuffer);
             ShapeResult result = settings.create()) {

            if (result.hasError()) {
                throw new IOException("Failed to create Jolt convex hull shape for " + modelLocation + ": " + result.getError());
            }

            long endTime = System.nanoTime();
            LOGGER.debug("Finalizing ConvexHullShape for {} in {} ms ({} final Vertices, {} original)",
                    modelLocation, (endTime - startTime) / 1_000_000.0, finalVertices.size(), originalVertexCount);

            return result.get().getPtr();
        }
    }

    private static List<Vec3> simplifyVertices(List<Vec3> originalVertices, int targetCount) {
        if (originalVertices.size() <= targetCount || targetCount <= 0) {
            return originalVertices;
        }

        List<Vec3> simplifiedVertices = new ArrayList<>(targetCount);
        int originalSize = originalVertices.size();
        double step = (double) originalSize / targetCount;

        for (int i = 0; i < targetCount; i++) {
            int index = (int) Math.round(i * step);
            if (index < originalSize) {
                simplifiedVertices.add(originalVertices.get(index));
            }
        }

        if (!simplifiedVertices.isEmpty() && !simplifiedVertices.get(simplifiedVertices.size() - 1).equals(originalVertices.get(originalSize - 1))) {
            simplifiedVertices.set(simplifiedVertices.size() - 1, originalVertices.get(originalSize - 1));
        }

        if (simplifiedVertices.isEmpty() && !originalVertices.isEmpty()) {
            simplifiedVertices.add(originalVertices.get(0));
        }

        return simplifiedVertices;
    }

    public static void clearCache() {
        LOGGER.debug("Clearing OBJ CollisionShape cache (previous size: {})...", COLLISION_SHAPE_CACHE.size());
        COLLISION_SHAPE_CACHE.invalidateAll();
        LOGGER.debug("OBJ CollisionShape cache cleared.");
    }
}