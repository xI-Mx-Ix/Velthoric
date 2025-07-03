package net.xmx.xbullet.physics.object.global.physicsobject.manager;

import com.github.stephengold.joltjni.RVec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.rigidphysicsobject.RigidPhysicsObject;
import net.xmx.xbullet.physics.object.rigidphysicsobject.pcmd.AddRigidBodyCommand;
import net.xmx.xbullet.physics.object.softphysicsobject.SoftPhysicsObject;
import net.xmx.xbullet.physics.object.softphysicsobject.pcmd.AddSoftBodyCommand;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PhysicsObjectLoader {

    private final PhysicsObjectManager objManager;
    private final Map<UUID, CompletableFuture<IPhysicsObject>> pendingLoads = new ConcurrentHashMap<>();
    private final ExecutorService loadingExecutor;

    public PhysicsObjectLoader(PhysicsObjectManager manager) {
        this.objManager = manager;
        int threadCount = Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
        this.loadingExecutor = Executors.newFixedThreadPool(threadCount, r -> new Thread(r, "XBullet-ObjectLoader-Pool"));
    }

    public void loadObjectsInChunk(ChunkPos chunkPos) {
        if (!objManager.isInitialized() || objManager.getSavedData() == null) {
            return;
        }

        for (Map.Entry<UUID, CompoundTag> entry : objManager.getSavedData().getAllObjectEntries()) {
            UUID id = entry.getKey();
            if (pendingLoads.containsKey(id) || objManager.getManagedObjects().containsKey(id)) {
                continue;
            }

            if (isObjectInChunk(entry.getValue(), chunkPos)) {
                scheduleObjectLoad(id);
            }
        }
    }

    public CompletableFuture<IPhysicsObject> scheduleObjectLoad(UUID objectId, boolean initiallyActive) {
        return pendingLoads.computeIfAbsent(objectId, id -> {
            Optional<CompoundTag> objTagOpt = objManager.getSavedData().getObjectData(id);
            if (objTagOpt.isEmpty()) {
                return CompletableFuture.failedFuture(new IllegalStateException("No saved data for object " + id));
            }

            CompoundTag objTag = objTagOpt.get();
            String typeId = objTag.getString("objectTypeIdentifier");
            if (typeId.isEmpty() || !objManager.getRegisteredObjectFactories().containsKey(typeId)) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("No factory for type " + typeId));
            }

            CompletableFuture<IPhysicsObject> future = CompletableFuture.supplyAsync(() -> {
                PhysicsTransform transform = new PhysicsTransform();
                if (objTag.contains("transform", 10)) {
                    transform.fromNbt(objTag.getCompound("transform"));
                }
                return objManager.createPhysicsObject(typeId, id, objManager.managedLevel, transform, objTag);
            }, loadingExecutor).thenApplyAsync(obj -> {
                if (obj != null) {
                    if (obj instanceof RigidPhysicsObject rpo) {
                        AddRigidBodyCommand.queue(objManager.getPhysicsWorld(), rpo, initiallyActive);
                    } else if (obj instanceof SoftPhysicsObject spo) {
                        AddSoftBodyCommand.queue(objManager.getPhysicsWorld(), spo, initiallyActive);
                    }
                    objManager.manageLoadedObject(obj);
                }
                return obj;
            }, objManager.managedLevel.getServer());

            future.whenComplete((res, ex) -> {
                if (ex != null) {
                    XBullet.LOGGER.error("Failed to load physics object {}", id, ex);
                }
                pendingLoads.remove(id);
            });

            return future;
        });
    }

    public CompletableFuture<IPhysicsObject> scheduleObjectLoad(UUID objectId) {
        return this.scheduleObjectLoad(objectId, true);
    }

    @Nullable
    public CompletableFuture<IPhysicsObject> getPendingLoad(UUID id) {
        return pendingLoads.get(id);
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

    public void unloadObjectsInChunk(ChunkPos chunkPos) {
        if (!objManager.isInitialized()) {
            return;
        }

        new ArrayList<>(objManager.getManagedObjects().values()).forEach(obj -> {
            RVec3 pos = obj.getCurrentTransform().getTranslation();
            int cx = (int) Math.floor(pos.xx() / 16.0);
            int cz = (int) Math.floor(pos.zz() / 16.0);
            if (cx == chunkPos.x && cz == chunkPos.z) {
                objManager.removeObject(obj.getPhysicsId(), false);
            }
        });

        pendingLoads.forEach((id, future) -> {
            future.thenAccept(obj -> {
                if (obj != null) {
                    RVec3 pos = obj.getCurrentTransform().getTranslation();
                    int cx = (int) Math.floor(pos.xx() / 16.0);
                    int cz = (int) Math.floor(pos.zz() / 16.0);
                    if (cx == chunkPos.x && cz == chunkPos.z) {
                        future.cancel(true);
                    }
                }
            });
        });
    }

    public void cancelLoad(UUID id) {
        Optional.ofNullable(pendingLoads.remove(id)).ifPresent(f -> f.cancel(true));
    }

    public void reset() {
        pendingLoads.values().forEach(f -> f.cancel(true));
        pendingLoads.clear();
    }

    public int getPendingLoadCount() {
        return pendingLoads.size();
    }

    public void shutdown() {
        loadingExecutor.shutdownNow();
        reset();
    }
}