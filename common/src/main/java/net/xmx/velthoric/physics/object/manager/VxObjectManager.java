package net.xmx.velthoric.physics.object.manager;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.annotation.VxUnsafe;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.persistence.VxObjectStorage;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;
import net.xmx.velthoric.physics.terrain.VxSectionPos;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class VxObjectManager {

    private final VxPhysicsWorld world;
    private final VxObjectStorage objectStorage;
    private final VxObjectDataStore dataStore;
    private final VxPhysicsUpdater physicsUpdater;
    private final VxObjectNetworkDispatcher networkDispatcher;
    private final ConcurrentLinkedQueue<Integer> dirtyIndicesQueue = new ConcurrentLinkedQueue<>();

    private final Map<UUID, VxAbstractBody> managedObjects = new ConcurrentHashMap<>();
    private final Int2ObjectMap<UUID> bodyIdToUuidMap = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

    private final ConcurrentHashMap<UUID, VxAbstractBody> pendingActivations = new ConcurrentHashMap<>();
    private final List<UUID> uuidsToRemove = new ArrayList<>();
    private final List<Integer> bodyIdsToActivate = new ArrayList<>();
    private final Long2ObjectMap<List<VxAbstractBody>> objectsByChunk = new Long2ObjectOpenHashMap<>();

    public VxObjectManager(VxPhysicsWorld world) {
        this.world = world;
        this.dataStore = new VxObjectDataStore();
        this.objectStorage = new VxObjectStorage(world.getLevel(), this);
        this.physicsUpdater = new VxPhysicsUpdater(this, dirtyIndicesQueue);
        this.networkDispatcher = new VxObjectNetworkDispatcher(world.getLevel(), this, dirtyIndicesQueue);
    }

    public void initialize() {
        objectStorage.initialize();
        networkDispatcher.start();
    }

    public void shutdown() {
        networkDispatcher.stop();
        getAllObjects().forEach(objectStorage::storeObject);
        pendingActivations.values().forEach(objectStorage::storeObject);
        objectStorage.saveDirtyRegions();
        clear();
        pendingActivations.clear();
        objectStorage.shutdown();
    }

    @VxUnsafe("Direct manipulation of internal physics objects. Use with caution.")
    public void add(VxAbstractBody obj) {
        if (obj == null) return;
        managedObjects.computeIfAbsent(obj.getPhysicsId(), id -> {
            EBodyType type = obj instanceof VxSoftBody ? EBodyType.SoftBody : EBodyType.RigidBody;
            int index = dataStore.addObject(id, type);
            obj.setDataStoreIndex(index);

            if (obj.getBodyId() != 0) {
                linkBodyId(obj.getBodyId(), id);
            }
            world.getConstraintManager().getDataSystem().onDependencyLoaded(id);
            return obj;
        });
    }

    @VxUnsafe("Direct manipulation of internal physics objects. Use with caution.")
    public VxAbstractBody remove(UUID id) {
        VxAbstractBody obj = managedObjects.remove(id);
        if (obj != null) {
            dataStore.removeObject(id);
            obj.setDataStoreIndex(-1);
            if (obj.getBodyId() != 0) {
                unlinkBodyId(obj.getBodyId());
            }
        }
        return obj;
    }

    private void clear() {
        managedObjects.clear();
        bodyIdToUuidMap.clear();
        dataStore.clear();
    }

    @VxUnsafe("Direct manipulation of internal physics objects. Use with caution.")
    public void linkBodyId(int bodyId, UUID objectId) {
        if (bodyId == 0) {
            new Throwable("Attempted to link invalid Body ID 0").printStackTrace();
            return;
        }
        this.bodyIdToUuidMap.put(bodyId, objectId);
    }

    @VxUnsafe("Direct manipulation of internal physics objects. Use with caution.")
    public void unlinkBodyId(int bodyId) {
        if (bodyId == 0) {
            new Throwable("Attempted to unlink invalid Body ID 0").printStackTrace();
            return;
        }
        this.bodyIdToUuidMap.remove(bodyId);
    }

    public Optional<VxAbstractBody> getByBodyId(int bodyId) {
        UUID objectId = bodyIdToUuidMap.get(bodyId);
        if (objectId == null) {
            return Optional.empty();
        }
        return getObject(objectId);
    }

    public Collection<VxAbstractBody> getAllObjects() {
        return managedObjects.values();
    }

    public void onPhysicsTick(long timestampNanos) {
        tickPendingActivations();
        physicsUpdater.update(timestampNanos, this.world);
    }

    public void onGameTick() {
        networkDispatcher.onGameTick();
        getAllObjects().forEach(obj -> obj.gameTick(world.getLevel()));
    }

    void updateObjectTracking(VxAbstractBody body, long fromKey, long toKey) {
        if (fromKey != Long.MAX_VALUE) {
            synchronized (objectsByChunk) {
                List<VxAbstractBody> fromList = objectsByChunk.get(fromKey);
                if (fromList != null) {
                    fromList.remove(body);
                    if (fromList.isEmpty()) {
                        objectsByChunk.remove(fromKey);
                    }
                }
            }
        }
        synchronized (objectsByChunk) {
            objectsByChunk.computeIfAbsent(toKey, k -> new CopyOnWriteArrayList<>()).add(body);
        }
        networkDispatcher.onObjectMoved(body, new ChunkPos(fromKey), new ChunkPos(toKey));
    }

    public void startTracking(VxAbstractBody body) {
        long key = getObjectChunkPos(body).toLong();
        body.setLastKnownChunkKey(key);
        synchronized (objectsByChunk) {
            objectsByChunk.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(body);
        }
        networkDispatcher.onObjectAdded(body);
    }

    public void stopTracking(VxAbstractBody body) {
        long key = body.getLastKnownChunkKey();
        if (key != Long.MAX_VALUE) {
            synchronized (objectsByChunk) {
                List<VxAbstractBody> list = objectsByChunk.get(key);
                if (list != null) {
                    list.remove(body);
                    if (list.isEmpty()) {
                        objectsByChunk.remove(key);
                    }
                }
            }
        }
        networkDispatcher.onObjectRemoved(body);
    }

    public List<VxAbstractBody> getObjectsInChunk(ChunkPos pos) {
        synchronized (objectsByChunk) {
            return objectsByChunk.getOrDefault(pos.toLong(), Collections.emptyList());
        }
    }

    void addRigidBodyToPhysicsWorld(VxRigidBody body, EActivation activation, boolean usePendingActivation) {
        try (ShapeSettings shapeSettings = body.createShapeSettings()) {
            if (shapeSettings == null)
                throw new IllegalStateException("createShapeSettings returned null for type: " + body.getType().getTypeId());
            try (ShapeResult shapeResult = shapeSettings.create()) {
                if (shapeResult.hasError())
                    throw new IllegalStateException("Shape creation failed: " + shapeResult.getError());
                try (ShapeRefC shapeRef = shapeResult.get()) {
                    try (BodyCreationSettings bcs = body.createBodyCreationSettings(shapeRef)) {
                        bcs.setPosition(body.getGameTransform().getTranslation());
                        bcs.setRotation(body.getGameTransform().getRotation());

                        int bodyId = world.getBodyInterface().createAndAddBody(bcs, activation);
                        if (bodyId == Jolt.cInvalidBodyId) {
                            VxMainClass.LOGGER.error("Jolt failed to create/add rigid body for {}", body.getPhysicsId());
                            return;
                        }
                        body.setBodyId(bodyId);
                        add(body);
                        startTracking(body);
                        body.onBodyAdded(world);

                        if (activation == EActivation.DontActivate && usePendingActivation) {
                            pendingActivations.put(body.getPhysicsId(), body);
                        }
                        world.getConstraintManager().getDataSystem().onDependencyLoaded(body.getPhysicsId());
                    }
                }
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to create/add rigid body {}", body.getPhysicsId(), e);
        }
    }

    void addSoftBodyToPhysicsWorld(VxSoftBody body, EActivation activation, boolean usePendingActivation) {
        try (SoftBodySharedSettings sharedSettings = body.createSoftBodySharedSettings()) {
            if (sharedSettings == null)
                throw new IllegalStateException("createSoftBodySharedSettings returned null for type: " + body.getType().getTypeId());
            try (SoftBodyCreationSettings settings = body.createSoftBodyCreationSettings(sharedSettings)) {
                settings.setPosition(body.getGameTransform().getTranslation());
                settings.setRotation(body.getGameTransform().getRotation());

                int bodyId = world.getBodyInterface().createAndAddSoftBody(settings, activation);
                if (bodyId == Jolt.cInvalidBodyId) {
                    VxMainClass.LOGGER.error("Jolt failed to create/add soft body for {}", body.getPhysicsId());
                    return;
                }
                body.setBodyId(bodyId);
                add(body);
                startTracking(body);
                body.onBodyAdded(world);

                if (activation == EActivation.DontActivate && usePendingActivation) {
                    pendingActivations.put(body.getPhysicsId(), body);
                }
                world.getConstraintManager().getDataSystem().onDependencyLoaded(body.getPhysicsId());
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to create/add soft body {}", body.getPhysicsId(), e);
        }
    }

    public void addConstructedBody(VxAbstractBody body) {
        world.execute(() -> {
            if (body instanceof VxRigidBody rigidBody) {
                addRigidBodyToPhysicsWorld(rigidBody, EActivation.DontActivate, true);
            } else if (body instanceof VxSoftBody softBody) {
                addSoftBodyToPhysicsWorld(softBody, EActivation.DontActivate, true);
            }
        });
    }

    public void reAddObjectToWorld(VxAbstractBody body) {
        world.execute(() -> {
            if (body instanceof VxRigidBody rigidBody) {
                addRigidBodyToPhysicsWorld(rigidBody, EActivation.DontActivate, false);
            } else if (body instanceof VxSoftBody softBody) {
                addSoftBodyToPhysicsWorld(softBody, EActivation.DontActivate, false);
            }
        });
    }

    public <T extends VxRigidBody> Optional<T> createRigidBody(VxObjectType<T> type, VxTransform transform, Consumer<T> configurator) {
        T body = type.create(world, UUID.randomUUID());
        body.getGameTransform().set(transform);
        configurator.accept(body);
        world.execute(() ->
                addRigidBodyToPhysicsWorld(body, EActivation.DontActivate, true)
        );
        return Optional.of(body);
    }

    public <T extends VxSoftBody> Optional<T> createSoftBody(VxObjectType<T> type, VxTransform transform, Consumer<T> configurator) {
        T body = type.create(world, UUID.randomUUID());
        body.getGameTransform().set(transform);
        configurator.accept(body);
        world.execute(() ->
                addSoftBodyToPhysicsWorld(body, EActivation.DontActivate, true)
        );
        return Optional.of(body);
    }

    private void tickPendingActivations() {
        if (pendingActivations.isEmpty()) {
            return;
        }

        final BodyInterface bodyInterface = world.getBodyInterface();
        if (bodyInterface == null) {
            return;
        }

        uuidsToRemove.clear();
        bodyIdsToActivate.clear();

        for (var entry : pendingActivations.entrySet()) {
            UUID id = entry.getKey();
            VxAbstractBody body = entry.getValue();

            if (body == null || body.getBodyId() == 0 || body.getBodyId() == Jolt.cInvalidBodyId) {
                VxMainClass.LOGGER.warn("Pending body {} is invalid. Removing.", id);
                uuidsToRemove.add(id);
                continue;
            }

            if (bodyInterface.isActive(body.getBodyId())) {
                uuidsToRemove.add(id);
                continue;
            }

            var pos = body.getGameTransform().getTranslation();

            VxSectionPos sectionPos = VxSectionPos.fromWorldSpace(pos.xx(), pos.yy(), pos.zz());
            SectionPos minecraftSectionPos = SectionPos.of(sectionPos.x(), sectionPos.y(), sectionPos.z());

            if (world.getTerrainSystem().isSectionReady(minecraftSectionPos)) {
                bodyIdsToActivate.add(body.getBodyId());
                uuidsToRemove.add(id);
            }
        }

        if (!bodyIdsToActivate.isEmpty()) {
            world.execute(() -> {
                for (int bodyId : bodyIdsToActivate) {
                    try {

                        if (bodyInterface.isAdded(bodyId)) {
                            bodyInterface.activateBody(bodyId);
                        }
                    } catch (Exception e) {
                        VxMainClass.LOGGER.error("Failed to activate body {}", bodyId, e);
                    }
                }
            });
        }

        if (!uuidsToRemove.isEmpty()) {
            for (UUID id : uuidsToRemove) {
                pendingActivations.remove(id);
            }
        }
    }

    public void removeObject(UUID id, VxRemovalReason reason) {
        final VxAbstractBody obj = this.remove(id);
        if (obj == null) {
            VxMainClass.LOGGER.warn("Attempted to remove non-existent body: {}", id);
            if (reason == VxRemovalReason.DISCARD) {
                objectStorage.removeData(id);
            }
            world.getConstraintManager().removeConstraintsForObject(id, reason == VxRemovalReason.DISCARD);
            return;
        }
        stopTracking(obj);
        obj.onBodyRemoved(world, reason);
        physicsUpdater.clearStateFor(id);

        if (reason == VxRemovalReason.SAVE) {
            obj.getTransform(world).ifPresent(t -> obj.getGameTransform().set(t));
            objectStorage.storeObject(obj);
        } else if (reason == VxRemovalReason.DISCARD) {
            objectStorage.removeData(id);
        }

        world.getConstraintManager().removeConstraintsForObject(id, reason == VxRemovalReason.DISCARD);

        final int bodyIdToRemove = obj.getBodyId();

        pendingActivations.remove(id);

        if (bodyIdToRemove != 0 && bodyIdToRemove != Jolt.cInvalidBodyId) {
            world.execute(() -> {
                if (world.getBodyInterface().isAdded(bodyIdToRemove)) {
                    world.getBodyInterface().removeBody(bodyIdToRemove);
                }
                world.getBodyInterface().destroyBody(bodyIdToRemove);
            });
        }
    }

    public Optional<VxAbstractBody> getObject(UUID id) {
        return Optional.ofNullable(managedObjects.get(id));
    }

    public CompletableFuture<VxAbstractBody> getOrLoadObject(UUID id) {
        if (id == null) {
            return CompletableFuture.completedFuture(null);
        }
        Optional<VxAbstractBody> loadedObject = getObject(id);
        if (loadedObject.isPresent()) {
            return CompletableFuture.completedFuture(loadedObject.get());
        }
        return objectStorage.loadObject(id);
    }

    public VxPhysicsWorld getPhysicsWorld() {
        return world;
    }

    public VxObjectDataStore getDataStore() {
        return dataStore;
    }

    public VxObjectStorage getObjectStorage() {
        return objectStorage;
    }

    public VxObjectNetworkDispatcher getNetworkDispatcher() {
        return networkDispatcher;
    }

    public static ChunkPos getObjectChunkPos(VxAbstractBody body) {
        var pos = body.getGameTransform().getTranslation();
        return new ChunkPos(SectionPos.posToSectionCoord(pos.xx()), SectionPos.posToSectionCoord(pos.zz()));
    }
}