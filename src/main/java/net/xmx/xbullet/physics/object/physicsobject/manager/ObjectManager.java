package net.xmx.xbullet.physics.object.physicsobject.manager;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.readonly.ConstSoftBodySharedSettings;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import net.xmx.xbullet.init.XBullet;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.network.NetworkHandler;
import net.xmx.xbullet.physics.constraint.manager.ConstraintManager;
import net.xmx.xbullet.physics.object.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.physicsobject.packet.RemovePhysicsObjectPacket;
import net.xmx.xbullet.physics.object.physicsobject.packet.SpawnPhysicsObjectPacket;
import net.xmx.xbullet.physics.object.physicsobject.packet.SyncAllPhysicsObjectsPacket;
import net.xmx.xbullet.physics.object.physicsobject.packet.SyncPhysicsObjectDataPacket;
import net.xmx.xbullet.physics.object.physicsobject.pcmd.ActivateBodyCommand;
import net.xmx.xbullet.physics.object.physicsobject.registry.GlobalPhysicsObjectRegistry;
import net.xmx.xbullet.physics.object.physicsobject.state.PhysicsObjectState;
import net.xmx.xbullet.physics.object.physicsobject.state.PhysicsObjectStatePool;
import net.xmx.xbullet.physics.terrain.manager.TerrainSystem;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import javax.annotation.Nullable;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.StampedLock;

public class ObjectManager {

    private final Map<UUID, IPhysicsObject> managedObjects = new ConcurrentHashMap<>(4096);
    private final Map<SectionPos, List<UUID>> pendingActivationBySection = new ConcurrentHashMap<>();
    private final Int2ObjectMap<UUID> bodyIdToUuidMap = new Int2ObjectOpenHashMap<>();
    private final Object bodyIdToUuidMapLock = new Object();
    private final Map<UUID, Boolean> lastActiveState = new ConcurrentHashMap<>(4096);
    @Nullable private PhysicsWorld physicsWorld;
    @Nullable ServerLevel managedLevel;

    private ObjectLifecycleManager lifecycleManager;

    private static final int MAX_PACKET_PAYLOAD_SIZE = 128 * 1024;
    private static final int MAX_SOFT_BODY_VERTICES = 4096;

    private long physicsTickCounter = 0;
    private static final int INACTIVE_OBJECT_UPDATE_INTERVAL_TICKS = 3;

    final ObjectDataSystem dataSystem;
    private final AtomicBoolean isInitializedInternal = new AtomicBoolean(false);
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private final Map<String, GlobalPhysicsObjectRegistry.RegistrationData> registeredObjectFactories = new ConcurrentHashMap<>();

    private final ThreadLocal<PhysicsTransform> tempTransform = ThreadLocal.withInitial(PhysicsTransform::new);
    private final ThreadLocal<List<IPhysicsObject>> objectsToUpdatePool = ThreadLocal.withInitial(ObjectArrayList::new);
    private final ThreadLocal<FloatBuffer> softBodyVertexBuffer = ThreadLocal.withInitial(() -> Jolt.newDirectFloatBuffer(MAX_SOFT_BODY_VERTICES * 3));
    private final ThreadLocal<List<UUID>> removalListPool = ThreadLocal.withInitial(ObjectArrayList::new);

    public ObjectManager() {
        this.dataSystem = new ObjectDataSystem(this);
    }

    public void initialize(PhysicsWorld physicsWorld) {
        this.physicsWorld = physicsWorld;
        ServerLevel level = physicsWorld.getLevel();

        if (this.physicsWorld == null || level == null) {
            XBullet.LOGGER.debug("PhysicsWorld or Level is null. Manager will not initialize.");
            return;
        }

        this.isShutdown.set(false);
        this.lifecycleManager = new ObjectLifecycleManager(this);
        this.isInitializedInternal.set(false);
        this.managedLevel = level;
        this.managedObjects.clear();
        this.lastActiveState.clear();
        this.dataSystem.initialize(level);
        this.registeredObjectFactories.putAll(GlobalPhysicsObjectRegistry.getRegisteredTypes());
        this.isInitializedInternal.set(true);
    }

    public boolean isInitialized() {
        return isInitializedInternal.get() && physicsWorld != null && managedLevel != null && physicsWorld.isRunning();
    }

    @Nullable
    public IPhysicsObject createPhysicsObject(String typeId, UUID id, Level level, PhysicsTransform transform, @Nullable FriendlyByteBuf initialData) {
        GlobalPhysicsObjectRegistry.RegistrationData regData = registeredObjectFactories.get(typeId);
        if (regData == null) {
            XBullet.LOGGER.error("No factory registered for PhysicsObject type: {}", typeId);
            return null;
        }
        return regData.factory().create(id, level, typeId, transform, regData.properties(), initialData);
    }

    public IPhysicsObject spawnObject(IPhysicsObject newObject) {
        if (!isInitialized() || newObject == null) {
            return null;
        }

        UUID objectId = newObject.getPhysicsId();

        if (managedObjects.containsKey(objectId) || dataSystem.hasObjectData(objectId)) {
            XBullet.LOGGER.warn("PhysicsObject with ID {} already exists or is loading/saved. Not registering duplicate.", objectId);
            return managedObjects.get(objectId);
        }

        manageLoadedObject(newObject);

        return newObject;
    }

    public void manageLoadedObject(IPhysicsObject obj) {
        if (!isInitialized() || obj == null || physicsWorld == null || managedObjects.containsKey(obj.getPhysicsId())) {
            return;
        }

        managedObjects.put(obj.getPhysicsId(), obj);
        obj.initializePhysics(physicsWorld);

        if (this.physicsWorld != null) {
            ConstraintManager cm = this.physicsWorld.getConstraintManager();
            if (cm.isInitialized()) {
                cm.getDataSystem().checkForConstraintsForObject(obj.getPhysicsId());
            }
        }

        if (managedLevel != null) {
            RVec3 pos = obj.getCurrentTransform().getTranslation();
            ChunkPos chunkPos = new ChunkPos((int) Math.floor(pos.xx() / 16.0), (int) Math.floor(pos.zz() / 16.0));
            var chunk = managedLevel.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false);
            if (chunk != null) {
                NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk),
                        new SpawnPhysicsObjectPacket(obj, System.nanoTime()));
            }
        }
    }

    public void unloadObject(UUID id) {
        if (!isInitialized()) return;

        IPhysicsObject obj = managedObjects.remove(id);
        if (obj != null) {
            dataSystem.saveObject(obj);
            obj.markRemoved();
            obj.removeFromPhysics(physicsWorld);
            if (physicsWorld != null) {
                ConstraintManager constraintManager = physicsWorld.getConstraintManager();
                if (constraintManager.isInitialized()) {
                    constraintManager.removeConstraintsForObject(id, false);
                }
            }
        }
    }

    public void deleteObject(UUID id) {
        if (!isInitialized()) return;

        if (physicsWorld != null) {
            ConstraintManager constraintManager = physicsWorld.getConstraintManager();
            if (constraintManager.isInitialized()) {
                constraintManager.removeConstraintsForObject(id, true);
            }
        }

        IPhysicsObject obj = managedObjects.remove(id);
        if (obj != null && !obj.isRemoved()) {
            obj.markRemoved();
            obj.removeFromPhysics(physicsWorld);
        }

        dataSystem.removeObject(id);
        if (managedLevel != null) {
            NetworkHandler.CHANNEL.send(PacketDistributor.DIMENSION.with(managedLevel::dimension), new RemovePhysicsObjectPacket(id));
        }
    }

    public void parallelUpdateAndDispatch(long timestampNanos, BodyInterface bodyInterface, BodyLockInterface lockInterface) {
        if (!isInitialized() || managedLevel == null || physicsWorld == null) {
            return;
        }

        physicsTickCounter++;
        final BodyInterface bodyInterfaceNoLock = physicsWorld.getPhysicsSystem().getBodyInterfaceNoLock();
        final boolean isPeriodicUpdateTick = (physicsTickCounter % INACTIVE_OBJECT_UPDATE_INTERVAL_TICKS == 0);

        List<IPhysicsObject> objectsToUpdate = managedObjects.values().parallelStream()
                .filter(obj -> obj != null && !obj.isRemoved() && obj.getBodyId() != 0 && bodyInterfaceNoLock.isAdded(obj.getBodyId()))
                .peek(obj -> {
                    obj.fixedPhysicsTick(this.physicsWorld);
                    obj.physicsTick(this.physicsWorld);
                })
                .filter(obj -> {
                    boolean isActive = bodyInterfaceNoLock.isActive(obj.getBodyId());
                    Boolean wasActive = lastActiveState.get(obj.getPhysicsId());

                    if (wasActive == null || wasActive != isActive) {
                        lastActiveState.put(obj.getPhysicsId(), isActive);
                        return true;
                    }

                    return isActive || obj.isDataDirty() || (!isActive && isPeriodicUpdateTick);
                })
                .toList();

        if (!objectsToUpdate.isEmpty()) {
            List<PhysicsObjectState> statesToSend = new ArrayList<>(objectsToUpdate.size());
            for (IPhysicsObject obj : objectsToUpdate) {
                PhysicsTransform transform = tempTransform.get();
                Vec3 linVel = null;
                Vec3 angVel = null;
                float[] vertexData = null;
                boolean isActive = bodyInterfaceNoLock.isActive(obj.getBodyId());

                final StampedLock transformLock = obj.getTransformLock();
                long stamp = transformLock.writeLock();
                try {
                    bodyInterfaceNoLock.getPositionAndRotation(obj.getBodyId(), transform.getTranslation(), transform.getRotation());

                    if (isActive) {
                        linVel = bodyInterfaceNoLock.getLinearVelocity(obj.getBodyId());
                        angVel = bodyInterfaceNoLock.getAngularVelocity(obj.getBodyId());
                        if (obj.getPhysicsObjectType() == EObjectType.SOFT_BODY) {
                            vertexData = getSoftBodyVertices(lockInterface, obj.getBodyId());
                        }
                    }

                    obj.updateStateFromPhysicsThread(timestampNanos, transform, linVel, angVel, vertexData, isActive);
                } finally {
                    transformLock.unlockWrite(stamp);
                }

                PhysicsObjectState state = PhysicsObjectStatePool.acquire();
                state.from(obj, timestampNanos, isActive);
                statesToSend.add(state);
            }

            processAndSendUpdates(statesToSend);
        }

        cleanupRemovedObjects();
    }

    private void processAndSendUpdates(List<PhysicsObjectState> states) {
        if (managedLevel == null) return;

        Long2ObjectMap<List<PhysicsObjectState>> statesByChunk = new Long2ObjectOpenHashMap<>();
        for (PhysicsObjectState state : states) {
            IPhysicsObject obj = managedObjects.get(state.getId());
            if (obj == null) {
                PhysicsObjectStatePool.release(state);
                continue;
            }

            RVec3 pos = obj.getCurrentTransform().getTranslation();
            long chunkKey = ChunkPos.asLong((int) Math.floor(pos.xx() / 16.0), (int) Math.floor(pos.zz() / 16.0));
            statesByChunk.computeIfAbsent(chunkKey, k -> new ObjectArrayList<>()).add(state);

            if (obj.isDataDirty()) {
                ChunkPos chunkPos = new ChunkPos(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.TRACKING_CHUNK.with(() -> managedLevel.getChunk(chunkPos.x, chunkPos.z)),
                        new SyncPhysicsObjectDataPacket(obj)
                );
                obj.clearDataDirty();
            }
        }

        sendChunkBatches(statesByChunk);
    }

    private void sendChunkBatches(Long2ObjectMap<List<PhysicsObjectState>> statesByChunk) {
        if (managedLevel == null) return;

        statesByChunk.forEach((chunkKey, statesInChunk) -> {
            if (statesInChunk.isEmpty()) {
                return;
            }

            int chunkX = ChunkPos.getX(chunkKey);
            int chunkZ = ChunkPos.getZ(chunkKey);
            var chunk = managedLevel.getChunkSource().getChunk(chunkX, chunkZ, false);
            if (chunk == null) {
                statesInChunk.forEach(PhysicsObjectStatePool::release);
                return;
            }

            ObjectArrayList<PhysicsObjectState> currentBatch = new ObjectArrayList<>();
            int currentBatchSizeBytes = 0;

            for (PhysicsObjectState state : statesInChunk) {
                int stateSize = state.estimateEncodedSize();
                if (!currentBatch.isEmpty() && currentBatchSizeBytes + stateSize > MAX_PACKET_PAYLOAD_SIZE) {
                    NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), new SyncAllPhysicsObjectsPacket(currentBatch));

                    currentBatch = new ObjectArrayList<>();
                    currentBatchSizeBytes = 0;
                }
                currentBatch.add(state);
                currentBatchSizeBytes += stateSize;
            }

            if (!currentBatch.isEmpty()) {
                NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), new SyncAllPhysicsObjectsPacket(currentBatch));
            }
        });
    }

    private void cleanupRemovedObjects() {
        List<UUID> toRemove = removalListPool.get();
        toRemove.clear();
        for (var entry : managedObjects.entrySet()) {
            IPhysicsObject obj = entry.getValue();

            if (obj.isRemoved() || obj.getCurrentTransform().getTranslation().yy() < -100.0f) {
                toRemove.add(entry.getKey());
            }
        }

        if (!toRemove.isEmpty()) {
            for (UUID id : toRemove) {
                deleteObject(id);
                lastActiveState.remove(id);
            }
        }
    }

    public Optional<IPhysicsObject> getObjectByBodyId(int bodyId) {
        UUID objectId;
        synchronized (bodyIdToUuidMapLock) {
            objectId = this.bodyIdToUuidMap.get(bodyId);
        }
        if (objectId != null) {
            return getObject(objectId);
        }
        return Optional.empty();
    }

    public void linkBodyId(int bodyId, UUID objectId) {
        synchronized (bodyIdToUuidMapLock) {
            this.bodyIdToUuidMap.put(bodyId, objectId);
        }
    }

    public void unlinkBodyId(int bodyId) {
        synchronized (bodyIdToUuidMapLock) {
            this.bodyIdToUuidMap.remove(bodyId);
        }
    }

    @Nullable
    private float[] getSoftBodyVertices(BodyLockInterface lockInterface, int bodyId) {
        try (BodyLockRead lock = new BodyLockRead(lockInterface, bodyId)) {
            if (lock.succeededAndIsInBroadPhase()) {
                Body body = lock.getBody();
                if (body != null && body.isSoftBody()) {
                    SoftBodyMotionProperties motionProps = (SoftBodyMotionProperties) body.getMotionProperties();
                    ConstSoftBodySharedSettings sharedSettings = motionProps.getSettings();
                    int numVertices = sharedSettings.countVertices();

                    if (numVertices > 0) {
                        float[] vertexBuffer = new float[numVertices * 3];
                        RMat44 worldTransform = body.getWorldTransform();
                        for (int i = 0; i < numVertices; i++) {
                            SoftBodyVertex vertex = motionProps.getVertex(i);
                            if (vertex == null) continue;
                            Vec3 localPos = vertex.getPosition();
                            RVec3 worldPos = worldTransform.multiply3x4(localPos);
                            int baseIndex = i * 3;
                            vertexBuffer[baseIndex] = worldPos.x();
                            vertexBuffer[baseIndex + 1] = worldPos.y();
                            vertexBuffer[baseIndex + 2] = worldPos.z();
                        }
                        return vertexBuffer;
                    }
                }
            }
        }
        return null;
    }

    public Optional<IPhysicsObject> getObject(UUID id) {
        return Optional.ofNullable(managedObjects.get(id));
    }

    public void activateObjectWhenReady(IPhysicsObject obj) {
        if (obj == null || obj.getBodyId() == 0 || physicsWorld == null) return;
        RVec3 pos = obj.getCurrentTransform().getTranslation();
        SectionPos sectionPos = SectionPos.of((int) pos.xx(), (int) pos.yy(), (int) pos.zz());
        TerrainSystem terrainSystem = physicsWorld.getTerrainSystem();
        if (terrainSystem != null && terrainSystem.isSectionReady(sectionPos)) {
            physicsWorld.queueCommand(new ActivateBodyCommand(obj.getBodyId()));
        } else {
            pendingActivationBySection.computeIfAbsent(sectionPos, k -> new ObjectArrayList<>()).add(obj.getPhysicsId());
        }
    }

    public void onTerrainSectionReady(SectionPos sectionPos) {
        List<UUID> objectsToActivate = pendingActivationBySection.remove(sectionPos);
        if (objectsToActivate != null && physicsWorld != null) {
            for (UUID objectId : objectsToActivate) {
                getObject(objectId).ifPresent(obj -> {
                    if (obj.getBodyId() != 0) {
                        physicsWorld.queueCommand(new ActivateBodyCommand(obj.getBodyId()));
                    }
                });
            }
        }
    }

    public Map<UUID, IPhysicsObject> getManagedObjects() {
        return Collections.unmodifiableMap(managedObjects);
    }

    public ObjectDataSystem getDataSystem() {
        return dataSystem;
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
    public ObjectLifecycleManager getLifecycleManager() {
        return lifecycleManager;
    }

    public Map<String, GlobalPhysicsObjectRegistry.RegistrationData> getRegisteredObjectFactories() {
        return Collections.unmodifiableMap(registeredObjectFactories);
    }

    public void shutdown() {
        if (isShutdown.getAndSet(true)) {
            return;
        }
        isInitializedInternal.set(false);

        if (dataSystem != null) {
            dataSystem.saveAll(managedObjects.values());
            dataSystem.shutdown();
        }

        managedObjects.values().forEach(obj -> {
            if (physicsWorld != null) {
                obj.removeFromPhysics(physicsWorld);
            }
        });
        managedObjects.clear();
        synchronized (bodyIdToUuidMapLock) {
            bodyIdToUuidMap.clear();
        }
        this.physicsWorld = null;
        this.managedLevel = null;
    }

    public CompletableFuture<IPhysicsObject> getOrLoadObject(@Nullable UUID id) {
        if (id == null) {
            return CompletableFuture.completedFuture(null);
        }
        IPhysicsObject loadedObject = managedObjects.get(id);
        if (loadedObject != null) {
            return CompletableFuture.completedFuture(loadedObject);
        }
        return dataSystem.loadObject(id);
    }

    public boolean isObjectInChunk(@Nullable UUID id, ChunkPos pos) {
        if (id == null) return false;
        return getObject(id).map(obj -> {
            RVec3 p = obj.getCurrentTransform().getTranslation();
            return new ChunkPos((int)Math.floor(p.x()/16.0), (int)Math.floor(p.z()/16.0)).equals(pos);
        }).orElseGet(() -> dataSystem.isObjectInChunk(id, pos));
    }

    public boolean isObjectInLoadedChunk(@Nullable UUID id) {
        if (id == null) {
            return false;
        }
        return managedObjects.containsKey(id);
    }
}