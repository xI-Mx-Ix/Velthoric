package net.xmx.xbullet.physics.object.global.physicsobject.manager;

import com.github.stephengold.joltjni.RVec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.network.NetworkHandler;
import net.xmx.xbullet.physics.XBulletSavedData;
import net.xmx.xbullet.physics.object.global.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.packet.RemovePhysicsObjectPacket;
import net.xmx.xbullet.physics.object.global.physicsobject.packet.SpawnPhysicsObjectPacket;
import net.xmx.xbullet.physics.object.global.physicsobject.packet.SyncPhysicsObjectPacket;
import net.xmx.xbullet.physics.object.global.physicsobject.registry.GlobalPhysicsObjectRegistry;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class PhysicsObjectManager {

    final Map<UUID, IPhysicsObject> managedObjects = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> bodyIdToUuidMap = new ConcurrentHashMap<>();
    final Map<UUID, Boolean> lastActiveState = new ConcurrentHashMap<>();
    @Nullable
    private PhysicsWorld physicsWorld;
    @Nullable
    ServerLevel managedLevel;
    int tickCounter = 0;
    final int syncInterval = 1;

    @Nullable
    XBulletSavedData savedData;
    final PhysicsObjectLoader objLoader;
    private final AtomicBoolean isInitializedInternal = new AtomicBoolean(false);
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private final Map<String, GlobalPhysicsObjectRegistry.RegistrationData> registeredObjectFactories = new ConcurrentHashMap<>();

    public PhysicsObjectManager() {
        this.objLoader = new PhysicsObjectLoader(this);
    }

    public void initialize(PhysicsWorld physicsWorld) {
        this.physicsWorld = physicsWorld;
        ServerLevel level = physicsWorld.getLevel();

        if (this.physicsWorld == null || level == null) {
            XBullet.LOGGER.debug("PhysicsWorld or Level is null. Manager will not initialize.");
            return;
        }

        this.isShutdown.set(false);
        this.isInitializedInternal.set(false);
        this.managedLevel = level;
        this.managedObjects.clear();
        this.lastActiveState.clear();
        this.objLoader.reset();
        this.savedData = XBulletSavedData.get(level);
        this.registeredObjectFactories.putAll(GlobalPhysicsObjectRegistry.getRegisteredTypes());
        this.isInitializedInternal.set(true);
    }

    public boolean isInitialized() {
        return isInitializedInternal.get() && physicsWorld != null && managedLevel != null && physicsWorld.isRunning();
    }

    @Nullable
    public IPhysicsObject createPhysicsObject(String typeId, UUID id, Level level, PhysicsTransform transform, @Nullable CompoundTag initialNbt) {
        GlobalPhysicsObjectRegistry.RegistrationData regData = registeredObjectFactories.get(typeId);
        if (regData == null) {
            XBullet.LOGGER.error("No factory registered for PhysicsObject type: {}", typeId);
            return null;
        }
        return regData.factory().create(id, level, typeId, transform, regData.properties(), initialNbt);
    }

    public IPhysicsObject registerObject(IPhysicsObject newObject) {
        if (!isInitialized() || newObject == null) return null;
        UUID objectId = newObject.getPhysicsId();

        if (managedObjects.containsKey(objectId) || (savedData != null && savedData.getObjectData(objectId).isPresent())) {
            XBullet.LOGGER.warn("PhysicsObject with ID {} already exists or is loading/saved. Not registering duplicate.", objectId);
            return managedObjects.get(objectId);
        }

        manageLoadedObject(newObject);
        if (savedData != null) {
            savedData.updateObjectData(newObject);
        }

        RVec3 pos = newObject.getCurrentTransform().getTranslation();
        ChunkPos chunkPos = new ChunkPos((int) Math.floor(pos.xx() / 16.0), (int) Math.floor(pos.zz() / 16.0));
        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> managedLevel.getChunk(chunkPos.x, chunkPos.z)),
                new SpawnPhysicsObjectPacket(newObject, System.nanoTime()));

        return newObject;
    }

    public void removeObject(UUID id, boolean isPermanent) {
        if (!isInitialized()) return;

        IPhysicsObject obj = managedObjects.get(id);
        if (obj != null && !obj.isRemoved()) {
            obj.markRemoved();
            obj.removeFromPhysics(physicsWorld);
        }
        objLoader.cancelLoad(id);
        if (isPermanent && savedData != null) {
            savedData.removeObjectData(id);
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.DIMENSION.with(managedLevel::dimension), new RemovePhysicsObjectPacket(id));
    }

    public void serverTick() {
        if (!isInitialized()) return;
        removeObjectsBelowLevel(-100.0f);
        Iterator<Map.Entry<UUID, IPhysicsObject>> iterator = managedObjects.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, IPhysicsObject> entry = iterator.next();
            IPhysicsObject obj = entry.getValue();
            UUID objectId = entry.getKey();
            if (obj.isRemoved()) {
                iterator.remove();
                lastActiveState.remove(objectId);
                continue;
            }
            if (!obj.isPhysicsInitialized() && obj.getBodyId() != 0) {
                obj.confirmPhysicsInitialized();
            }
            if (obj.getBodyId() == 0 && obj.isPhysicsInitialized()) {
                obj.markRemoved();
                continue;
            }
            if (!obj.isPhysicsInitialized()) {
                continue;
            }
            obj.serverTick(this.physicsWorld);
            boolean currentIsActive = obj.isPhysicsActive();
            Boolean wasActive = lastActiveState.getOrDefault(objectId, false);
            boolean shouldSync = currentIsActive || wasActive;
            if (tickCounter % syncInterval == 0 && shouldSync) {
                long timestamp = obj.getLastUpdateTimestampNanos();
                if (timestamp > 0) {
                    RVec3 pos = obj.getCurrentTransform().getTranslation();
                    ChunkPos chunkPos = new ChunkPos((int) Math.floor(pos.xx() / 16.0), (int) Math.floor(pos.zz() / 16.0));
                    NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> managedLevel.getChunk(chunkPos.x, chunkPos.z)),
                            new SyncPhysicsObjectPacket(obj, timestamp, currentIsActive));
                }
            }
            lastActiveState.put(objectId, currentIsActive);
        }
        tickCounter++;
    }

    public void loadPhysicsObjectsForChunk(ChunkPos chunkPos) {
        if (isInitialized()) objLoader.loadObjectsInChunk(chunkPos);
    }

    public void unloadPhysicsObjectsForChunk(ChunkPos chunkPos) {
        if (isInitialized()) objLoader.unloadObjectsInChunk(chunkPos);
    }

    public boolean manageLoadedObject(IPhysicsObject obj) {
        if (!isInitialized() || managedObjects.containsKey(obj.getPhysicsId())) {
            return false;
        }
        managedObjects.put(obj.getPhysicsId(), obj);
        obj.initializePhysics(physicsWorld);
        return true;
    }

    public Optional<IPhysicsObject> getObjectByBodyId(int bodyId) {
        UUID objectId = this.bodyIdToUuidMap.get(bodyId);
        if (objectId != null) {
            return getObject(objectId);
        }
        return Optional.empty();
    }

    public void linkBodyId(int bodyId, UUID objectId) {
        this.bodyIdToUuidMap.put(bodyId, objectId);
    }

    public void unlinkBodyId(int bodyId) {
        this.bodyIdToUuidMap.remove(bodyId);
    }

    private void removeObjectsBelowLevel(float yLevel) {
        List<UUID> toRemove = new ArrayList<>();
        for (IPhysicsObject obj : managedObjects.values()) {
            if (obj.getPhysicsObjectType() == EObjectType.RIGID_BODY) {
                if (obj.getCurrentTransform().getTranslation().yy() < yLevel) {
                    toRemove.add(obj.getPhysicsId());
                }
            } else if (obj.getPhysicsObjectType() == EObjectType.SOFT_BODY) {
                float[] vertices = obj.getLastSyncedVertexData();
                if (vertices != null && vertices.length > 0) {
                    float avgY = 0;
                    int count = 0;
                    for (int i = 1; i < vertices.length; i += 3) {
                        avgY += vertices[i];
                        count++;
                    }
                    if (count > 0 && (avgY / count) < yLevel) {
                        toRemove.add(obj.getPhysicsId());
                    }
                }
            }
        }
        toRemove.forEach(id -> removeObject(id, true));
    }

    public Optional<IPhysicsObject> getObject(UUID id) {
        return Optional.ofNullable(managedObjects.get(id));
    }

    @Nullable
    public ServerLevel getManagedLevel() {
        return managedLevel;
    }

    @Nullable
    public PhysicsWorld getPhysicsWorld() {
        return physicsWorld;
    }

    @Nullable
    public XBulletSavedData getSavedData() {
        return savedData;
    }

    public Map<String, GlobalPhysicsObjectRegistry.RegistrationData> getRegisteredObjectFactories() {
        return Collections.unmodifiableMap(registeredObjectFactories);
    }

    public Map<UUID, IPhysicsObject> getManagedObjects() {
        return Collections.unmodifiableMap(managedObjects);
    }

    public void shutdown() {
        if (isShutdown.getAndSet(true)) {
            return;
        }
        isInitializedInternal.set(false);
        if (this.savedData != null) {
            for (IPhysicsObject obj : managedObjects.values()) {
                savedData.updateObjectData(obj);
                if (physicsWorld != null) {
                    obj.removeFromPhysics(physicsWorld);
                }
            }
            savedData.setDirty();
        }
        managedObjects.clear();
        bodyIdToUuidMap.clear();
        objLoader.shutdown();
        this.savedData = null;
        this.physicsWorld = null;
        this.managedLevel = null;
    }

    public int getPendingLoadCount() {
        return this.objLoader.getPendingLoadCount();
    }

    public CompletableFuture<IPhysicsObject> getOrLoadObject(UUID objectId) {
        if (managedObjects.containsKey(objectId)) {
            return CompletableFuture.completedFuture(managedObjects.get(objectId));
        }

        CompletableFuture<IPhysicsObject> pendingFuture = objLoader.getPendingLoad(objectId);
        if (pendingFuture != null) {
            return pendingFuture;
        }

        return objLoader.scheduleObjectLoad(objectId);
    }

}