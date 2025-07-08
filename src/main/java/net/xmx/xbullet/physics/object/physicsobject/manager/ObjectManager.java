package net.xmx.xbullet.physics.object.physicsobject.manager;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.readonly.ConstSoftBodySharedSettings;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
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
import net.xmx.xbullet.physics.object.physicsobject.manager.loader.ObjectDataSystem;
import net.xmx.xbullet.physics.object.physicsobject.packet.RemovePhysicsObjectPacket;
import net.xmx.xbullet.physics.object.physicsobject.packet.SpawnPhysicsObjectPacket;
import net.xmx.xbullet.physics.object.physicsobject.packet.SyncAllPhysicsObjectsPacket;
import net.xmx.xbullet.physics.object.physicsobject.packet.SyncPhysicsObjectNbtPacket;
import net.xmx.xbullet.physics.object.physicsobject.pcmd.ActivateBodyCommand;
import net.xmx.xbullet.physics.object.physicsobject.registry.GlobalPhysicsObjectRegistry;
import net.xmx.xbullet.physics.object.physicsobject.state.PhysicsObjectState;
import net.xmx.xbullet.physics.object.physicsobject.state.PhysicsObjectStatePool;
import net.xmx.xbullet.physics.terrain.manager.TerrainSystem;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class ObjectManager {

    final Map<UUID, IPhysicsObject> managedObjects = new ConcurrentHashMap<>(4096);
    final Map<SectionPos, List<UUID>> pendingActivationBySection = new ConcurrentHashMap<>();
    private final Int2ObjectMap<UUID> bodyIdToUuidMap = new Int2ObjectOpenHashMap<>();
    private final Object bodyIdToUuidMapLock = new Object();
    final Map<UUID, Boolean> lastActiveState = new ConcurrentHashMap<>(4096);
    @Nullable private PhysicsWorld physicsWorld;
    @Nullable ServerLevel managedLevel;

    private static final int MAX_PACKET_PAYLOAD_SIZE = 128 * 1024;

    final ObjectDataSystem dataSystem;
    private final AtomicBoolean isInitializedInternal = new AtomicBoolean(false);
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private final Map<String, GlobalPhysicsObjectRegistry.RegistrationData> registeredObjectFactories = new ConcurrentHashMap<>();
    private final ThreadLocal<List<UUID>> removalListPool = ThreadLocal.withInitial(ObjectArrayList::new);

    private final ThreadLocal<PhysicsTransform> tempTransform = ThreadLocal.withInitial(PhysicsTransform::new);

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

        if (managedObjects.containsKey(objectId) || dataSystem.hasObjectData(objectId)) {
            XBullet.LOGGER.warn("PhysicsObject with ID {} already exists or is loading/saved. Not registering duplicate.", objectId);
            return managedObjects.get(objectId);
        }

        manageNewObject(newObject, true);
        dataSystem.saveObject(newObject);

        return newObject;
    }

    public void manageNewObject(IPhysicsObject obj, boolean sendSpawnPacket) {
        if (!isInitialized() || obj == null || managedObjects.containsKey(obj.getPhysicsId())) {
            return;
        }
        managedObjects.put(obj.getPhysicsId(), obj);
        obj.initializePhysics(physicsWorld);

        if (sendSpawnPacket) {
            RVec3 pos = obj.getCurrentTransform().getTranslation();
            ChunkPos chunkPos = new ChunkPos((int) Math.floor(pos.xx() / 16.0), (int) Math.floor(pos.zz() / 16.0));
            NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> managedLevel.getChunk(chunkPos.x, chunkPos.z)),
                    new SpawnPhysicsObjectPacket(obj, System.nanoTime()));
        }
    }

    public void unloadObject(UUID id) {
        if (!isInitialized()) return;

        IPhysicsObject obj = managedObjects.get(id);
        if (obj != null) {
            dataSystem.saveObject(obj);
            obj.markRemoved();
            obj.removeFromPhysics(physicsWorld);
            if (physicsWorld != null) {
                ConstraintManager constraintManager = physicsWorld.getConstraintManager();
                if (constraintManager != null && constraintManager.isInitialized()) {
                    constraintManager.removeConstraintsForObject(id, false);
                }
            }
            managedObjects.remove(id);
        }
        dataSystem.cancelLoad(id);
    }

    public void deleteObject(UUID id) {
        if (!isInitialized()) return;

        if (physicsWorld != null) {
            ConstraintManager constraintManager = physicsWorld.getConstraintManager();
            if (constraintManager != null && constraintManager.isInitialized()) {
                constraintManager.removeConstraintsForObject(id, true);
            }
        }

        IPhysicsObject obj = managedObjects.remove(id);
        if (obj != null && !obj.isRemoved()) {
            obj.markRemoved();
            obj.removeFromPhysics(physicsWorld);
        }

        dataSystem.cancelLoad(id);
        dataSystem.removeObject(id);
        NetworkHandler.CHANNEL.send(PacketDistributor.DIMENSION.with(managedLevel::dimension), new RemovePhysicsObjectPacket(id));
    }

    public void parallelUpdateAndDispatch(long timestampNanos, BodyInterface bodyInterface, BodyLockInterface lockInterface) {
        if (!isInitialized() || managedLevel == null) {
            return;
        }

        List<IPhysicsObject> objectsToProcess = Collections.synchronizedList(new ObjectArrayList<>());

        managedObjects.values().parallelStream().forEach(obj -> {
            if (obj.isRemoved() || obj.getBodyId() == 0 || !bodyInterface.isAdded(obj.getBodyId())) {
                return;
            }

            if (!obj.isPhysicsInitialized()) {
                obj.confirmPhysicsInitialized();
                this.activateObjectWhenReady(obj);
            }

            obj.physicsTick(this.physicsWorld);

            boolean isActive = bodyInterface.isActive(obj.getBodyId());
            Boolean wasActive = lastActiveState.put(obj.getPhysicsId(), isActive);
            boolean stateChanged = wasActive != null && wasActive != isActive;

            if (isActive) {
                // Use pooled object for transform
                PhysicsTransform transform = tempTransform.get();
                bodyInterface.getPositionAndRotation(obj.getBodyId(), transform.getTranslation(), transform.getRotation());

                // KORREKTUR: Verwende die Rückgabewerte der Methoden
                Vec3 linVel = bodyInterface.getLinearVelocity(obj.getBodyId());
                Vec3 angVel = bodyInterface.getAngularVelocity(obj.getBodyId());

                float[] vertexData = null;
                if (obj.getPhysicsObjectType() == EObjectType.SOFT_BODY) {
                    vertexData = getSoftBodyVertices(lockInterface, obj.getBodyId());
                }

                obj.updateStateFromPhysicsThread(timestampNanos, transform, linVel, angVel, vertexData, isActive);

            } else {
                if (stateChanged) {
                    PhysicsTransform transform = tempTransform.get();
                    bodyInterface.getPositionAndRotation(obj.getBodyId(), transform.getTranslation(), transform.getRotation());
                    obj.updateStateFromPhysicsThread(timestampNanos, transform, null, null, null, isActive);
                } else {
                    obj.updateStateFromPhysicsThread(timestampNanos, null, null, null, null, isActive);
                }
            }

            if (isActive || stateChanged || obj.isNbtDirty()) {
                objectsToProcess.add(obj);
            }
        });

        if (objectsToProcess.isEmpty()) {
            return;
        }

        Map<Long, List<PhysicsObjectState>> statesByChunk = objectsToProcess.parallelStream()
                .filter(obj -> {
                    Boolean wasActiveWrapper = lastActiveState.get(obj.getPhysicsId());
                    boolean wasActive = wasActiveWrapper != null && wasActiveWrapper;
                    return obj.isPhysicsActive() || wasActive;
                })
                .map(obj -> {
                    long ts = obj.getLastUpdateTimestampNanos();
                    if (ts > 0) {
                        PhysicsObjectState state = PhysicsObjectStatePool.acquire();
                        state.from(obj, ts, obj.isPhysicsActive());
                        RVec3 pos = obj.getCurrentTransform().getTranslation();
                        long chunkKey = ChunkPos.asLong((int) Math.floor(pos.xx() / 16.0), (int) Math.floor(pos.zz() / 16.0));
                        return new AbstractMap.SimpleEntry<>(chunkKey, state);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        ConcurrentHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));

        if (!statesByChunk.isEmpty()) {
            sendChunkBatches(statesByChunk);
        }

        objectsToProcess.parallelStream()
                .filter(IPhysicsObject::isNbtDirty)
                .forEach(obj -> {
                    RVec3 pos = obj.getCurrentTransform().getTranslation();
                    ChunkPos chunkPos = new ChunkPos((int) Math.floor(pos.xx() / 16.0), (int) Math.floor(pos.zz() / 16.0));
                    NetworkHandler.CHANNEL.send(
                            PacketDistributor.TRACKING_CHUNK.with(() -> managedLevel.getChunk(chunkPos.x, chunkPos.z)),
                            new SyncPhysicsObjectNbtPacket(obj)
                    );
                    obj.clearNbtDirty();
                });

        removeObjectsBelowLevel(-100.0f);

        List<UUID> toRemove = removalListPool.get();
        toRemove.clear();
        for (IPhysicsObject obj : managedObjects.values()) {
            if (obj.isRemoved()) {
                toRemove.add(obj.getPhysicsId());
            }
        }
        if (!toRemove.isEmpty()) {
            for (UUID id : toRemove) {
                managedObjects.remove(id);
                lastActiveState.remove(id);
            }
        }
    }

    private void sendChunkBatches(Map<Long, List<PhysicsObjectState>> statesByChunk) {
        if (managedLevel == null) {
            return;
        }

        statesByChunk.forEach((chunkKey, statesInChunk) -> {
            if (statesInChunk.isEmpty()) {
                return;
            }

            int chunkX = ChunkPos.getX(chunkKey);
            int chunkZ = ChunkPos.getZ(chunkKey);

            List<PhysicsObjectState> currentBatch = new ObjectArrayList<>();
            int currentBatchSizeBytes = 0;

            for (PhysicsObjectState state : statesInChunk) {
                int stateSize = state.estimateEncodedSize();
                if (!currentBatch.isEmpty() && currentBatchSizeBytes + stateSize > MAX_PACKET_PAYLOAD_SIZE) {
                    sendBatchToChunkTrackers(new ObjectArrayList<>(currentBatch), chunkX, chunkZ);
                    currentBatch.clear();
                    currentBatchSizeBytes = 0;
                }
                currentBatch.add(state);
                currentBatchSizeBytes += stateSize;
            }

            if (!currentBatch.isEmpty()) {
                sendBatchToChunkTrackers(new ObjectArrayList<>(currentBatch), chunkX, chunkZ);
            }
        });
    }

    private void sendBatchToChunkTrackers(List<PhysicsObjectState> batch, int chunkX, int chunkZ) {
        if (managedLevel == null) {
            return;
        }

        var chunk = managedLevel.getChunkSource().getChunk(chunkX, chunkZ, false);
        if (chunk != null) {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.TRACKING_CHUNK.with(() -> chunk),
                    new SyncAllPhysicsObjectsPacket(batch)
            );
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

    private void removeObjectsBelowLevel(float yLevel) {
        List<UUID> toRemove = new ObjectArrayList<>();
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
        if (!toRemove.isEmpty()) {
            toRemove.forEach(this::deleteObject);
        }
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

    public CompletableFuture<IPhysicsObject> getOrLoadObject(UUID objectId) {
        if (managedObjects.containsKey(objectId)) {
            return CompletableFuture.completedFuture(managedObjects.get(objectId));
        }
        return dataSystem.getOrLoadObject(objectId, false);
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
}