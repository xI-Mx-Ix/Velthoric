package net.xmx.vortex.physics.object.physicsobject.manager;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.persistence.ObjectStorage;
import net.xmx.vortex.physics.object.physicsobject.registry.GlobalPhysicsObjectRegistry;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ObjectDataSystem {

    private final VxObjectManager objectManager;
    private ObjectStorage storage;
    private ServerLevel level;
    private ExecutorService loadingExecutor;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    public ObjectDataSystem(VxObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    public void initialize(ServerLevel level) {
        if (isInitialized.get()) return;
        this.level = level;
        int threadCount = Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
        this.loadingExecutor = Executors.newFixedThreadPool(threadCount, r -> new Thread(r, "Vortex Object-Loader"));
        this.storage = new ObjectStorage(level, objectManager);
        this.storage.loadFromFile();
        isInitialized.set(true);
    }

    public void loadObjectsInChunk(ChunkPos chunkPos) {
        if (!isInitialized.get()) return;
        List<UUID> idsToLoad = storage.getObjectIdsInChunk(chunkPos);
        if (idsToLoad == null || idsToLoad.isEmpty()) {
            return;
        }

        for (UUID id : new ObjectArrayList<>(idsToLoad)) {
            if (objectManager.getManagedObjects().containsKey(id)) {
                continue;
            }
            byte[] data = storage.takeObjectData(id);
            if (data == null) continue;

            CompletableFuture.runAsync(() -> {
                try {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

                    buf.markReaderIndex();
                    String typeId = buf.readUtf(32767);
                    buf.resetReaderIndex();

                    GlobalPhysicsObjectRegistry.RegistrationData regData = objectManager.getRegisteredObjectFactories().get(typeId);
                    if (regData == null) {
                        VxMainClass.LOGGER.error("No factory registered for PhysicsObject type: {}", typeId);
                        return;
                    }

                    IPhysicsObject obj = objectManager.createPhysicsObject(typeId, id, level, null, null);

                    if (obj == null) {
                        VxMainClass.LOGGER.error("Factory for {} returned null object.", typeId);
                        return;
                    }

                    obj.readData(buf);

                    level.getServer().execute(() -> {
                        objectManager.manageLoadedObject(obj);
                    });

                } catch (Exception e) {
                    VxMainClass.LOGGER.error("Failed to load physics object {}", id, e);
                }
            }, loadingExecutor);
        }
        storage.removeLoadedChunkData(chunkPos);
    }

    public void unloadObjectsInChunk(ChunkPos chunkPos) {
        if (!isInitialized.get()) return;
        new ObjectArrayList<>(objectManager.getManagedObjects().values()).forEach(obj -> {
            VxTransform transform = obj.getCurrentTransform();
            ChunkPos currentChunkPos = new ChunkPos(
                    (int) Math.floor(transform.getTranslation().x() / 16.0),
                    (int) Math.floor(transform.getTranslation().z() / 16.0)
            );
            if (currentChunkPos.equals(chunkPos)) {
                objectManager.unloadObject(obj.getPhysicsId());
            }
        });
    }

    public void saveAll(Collection<IPhysicsObject> activeObjects) {
        if (!isInitialized.get()) return;
        storage.saveToFile(activeObjects);
    }

    public void saveObject(IPhysicsObject object) {
        if (!isInitialized.get()) return;
        storage.storeObjectData(object);
    }

    public void removeObject(UUID id) {
        if (!isInitialized.get()) return;
        storage.removeObjectData(id);
    }

    public boolean hasObjectData(UUID id) {
        return isInitialized.get() && storage.hasData(id);
    }

    public void shutdown() {
        if (!isInitialized.getAndSet(false)) return;
        saveAll(objectManager.getManagedObjects().values());
        if (loadingExecutor != null) {
            loadingExecutor.shutdown();
        }
    }

    public boolean isObjectInChunk(UUID id, ChunkPos pos) {
        return storage.isObjectInChunk(id, pos);
    }

    public CompletableFuture<IPhysicsObject> loadObject(UUID id) {
        if (!isInitialized.get() || !storage.hasData(id)) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            byte[] data = storage.takeObjectData(id);
            if (data == null) return null;

            try {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
                buf.markReaderIndex();
                String typeId = buf.readUtf(32767);
                buf.resetReaderIndex();

                IPhysicsObject obj = objectManager.createPhysicsObject(typeId, id, level, null, null);
                if (obj == null) return null;

                obj.readData(buf);
                return obj;
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Failed to load physics object for constraint dependency {}", id, e);
                return null;
            }
        }, loadingExecutor).thenApply(obj -> {
            if (obj != null) {
                level.getServer().execute(() -> objectManager.manageLoadedObject(obj));
            }
            return obj;
        });
    }

}