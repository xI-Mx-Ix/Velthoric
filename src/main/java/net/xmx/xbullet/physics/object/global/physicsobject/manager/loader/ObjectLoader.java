package net.xmx.xbullet.physics.object.global.physicsobject.manager.loader;

import com.github.stephengold.joltjni.RVec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.XBulletSavedData;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.manager.ObjectManager;
import net.xmx.xbullet.physics.object.rigidphysicsobject.RigidPhysicsObject;
import net.xmx.xbullet.physics.object.rigidphysicsobject.pcmd.AddRigidBodyCommand;
import net.xmx.xbullet.physics.object.softphysicsobject.SoftPhysicsObject;
import net.xmx.xbullet.physics.object.softphysicsobject.pcmd.AddSoftBodyCommand;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class ObjectLoader {

    private final ObjectManager objectManager;
    private final ObjectDataSystem dataSystem;
    private final Map<UUID, CompletableFuture<IPhysicsObject>> pendingLoads = new ConcurrentHashMap<>();
    private ExecutorService loadingExecutor;

    ObjectLoader(ObjectManager manager, ObjectDataSystem dataSystem) {
        this.objectManager = manager;
        this.dataSystem = dataSystem;
    }

    void initialize() {
        if (this.loadingExecutor != null) {
            this.loadingExecutor.shutdownNow();
        }
        int threadCount = Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
        this.loadingExecutor = Executors.newFixedThreadPool(threadCount, r -> new Thread(r, "XBullet-ObjectLoader-Pool"));
    }

    void loadObjectsInChunk(ChunkPos chunkPos, XBulletSavedData savedData) {
        for (Map.Entry<UUID, CompoundTag> entry : savedData.getAllObjectEntries()) {
            UUID id = entry.getKey();
            if (pendingLoads.containsKey(id) || objectManager.getManagedObjects().containsKey(id)) {
                continue;
            }
            if (isObjectInChunk(entry.getValue(), chunkPos)) {
                scheduleObjectLoad(id, false, savedData);
            }
        }
    }

    CompletableFuture<IPhysicsObject> scheduleObjectLoad(UUID objectId, boolean initiallyActive, XBulletSavedData savedData) {
        return pendingLoads.computeIfAbsent(objectId, id -> {
            Optional<CompoundTag> objTagOpt = savedData.getObjectData(id);
            if (objTagOpt.isEmpty()) {
                return CompletableFuture.failedFuture(new IllegalStateException("No saved data for object " + id));
            }

            CompoundTag objTag = objTagOpt.get();
            String typeId = objTag.getString("objectTypeIdentifier");
            if (typeId.isEmpty() || !objectManager.getRegisteredObjectFactories().containsKey(typeId)) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("No factory for type " + typeId));
            }

            CompletableFuture<IPhysicsObject> future = CompletableFuture.supplyAsync(() -> {
                PhysicsTransform transform = new PhysicsTransform();
                if (objTag.contains("transform", 10)) {
                    transform.fromNbt(objTag.getCompound("transform"));
                }
                return objectManager.createPhysicsObject(typeId, id, objectManager.getManagedLevel(), transform, objTag);
            }, loadingExecutor).thenApplyAsync(obj -> {
                if (obj != null) {
                    if (obj instanceof RigidPhysicsObject rpo) {
                        AddRigidBodyCommand.queue(objectManager.getPhysicsWorld(), rpo, initiallyActive);
                    } else if (obj instanceof SoftPhysicsObject spo) {
                        AddSoftBodyCommand.queue(objectManager.getPhysicsWorld(), spo, initiallyActive);
                    }
                    objectManager.manageNewObject(obj, false);
                }
                return obj;
            }, objectManager.getManagedLevel().getServer());

            future.whenComplete((res, ex) -> {
                if (ex != null) {
                    XBullet.LOGGER.error("Failed to load physics object {}", id, ex);
                }
                pendingLoads.remove(id);
            });

            return future;
        });
    }
    
    void unloadObjectsInChunk(ChunkPos chunkPos) {
        new ArrayList<>(objectManager.getManagedObjects().values()).forEach(obj -> {
            RVec3 pos = obj.getCurrentTransform().getTranslation();
            if (pos.xx() >= chunkPos.getMinBlockX() && pos.xx() < chunkPos.getMaxBlockX() &&
                pos.zz() >= chunkPos.getMinBlockZ() && pos.zz() < chunkPos.getMaxBlockZ()) {
                objectManager.removeObject(obj.getPhysicsId(), false);
            }
        });
    }

    void cancelLoad(UUID id) {
        Optional.ofNullable(pendingLoads.remove(id)).ifPresent(f -> f.cancel(true));
    }
    
    void shutdown() {
        if (loadingExecutor != null) {
            loadingExecutor.shutdownNow();
        }
        pendingLoads.values().forEach(f -> f.cancel(true));
        pendingLoads.clear();
    }
    
    private boolean isObjectInChunk(CompoundTag objTag, ChunkPos chunkPos) {
        if (objTag.contains("transform", 10)) {
            CompoundTag transformTag = objTag.getCompound("transform");
            if (transformTag.contains("pos", 9)) {
                ListTag posList = transformTag.getList("pos", Tag.TAG_DOUBLE);
                if (posList.size() == 3) {
                    double x = posList.getDouble(0);
                    double z = posList.getDouble(2);
                    return ((int) Math.floor(x / 16.0)) == chunkPos.x &&
                            ((int) Math.floor(z / 16.0)) == chunkPos.z;
                }
            }
        }
        return false;
    }

    public int getPendingLoadCount() {
        return pendingLoads.size();
    }
}