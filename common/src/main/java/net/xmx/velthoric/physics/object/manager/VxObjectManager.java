/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
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
import net.xmx.velthoric.physics.object.manager.registry.VxObjectRegistry;
import net.xmx.velthoric.physics.object.persistence.VxObjectStorage;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Manages the lifecycle, tracking, and state of all physics objects within a VxPhysicsWorld.
 * This class acts as the central hub for creating, removing, and accessing physics bodies.
 *
 * @author xI-Mx-Ix
 */
public class VxObjectManager {

    private final VxPhysicsWorld world;
    private final VxObjectStorage objectStorage;
    private final VxObjectDataStore dataStore;
    private final VxPhysicsUpdater physicsUpdater;
    private final VxObjectNetworkDispatcher networkDispatcher;
    private final ConcurrentLinkedQueue<Integer> dirtyIndicesQueue = new ConcurrentLinkedQueue<>();

    private final Map<UUID, VxAbstractBody> managedObjects = new ConcurrentHashMap<>();
    private final Int2ObjectMap<VxAbstractBody> bodyIdToObjectMap = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
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
        objectStorage.saveDirtyRegions();
        clear();
        objectStorage.shutdown();
    }

    private void clear() {
        managedObjects.clear();
        bodyIdToObjectMap.clear();
        dataStore.clear();
    }

    /**
     * Registers an existing, externally created physics object with the manager.
     * <p>
     * This method is intended for internal or advanced use cases where a body has already been
     * created and added to the Jolt physics system manually. It bypasses the standard creation
     * pipeline (e.g., {@code createRigidBody} or {@code addConstructedBody}). The caller is responsible
     * for ensuring the body is valid and has been added to the simulation.
     *
     * @param obj The fully constructed {@link VxAbstractBody} to register.
     */
    @VxUnsafe("Direct manipulation of internal physics objects. Use with caution.")
    public void add(VxAbstractBody obj) {
        if (obj == null) return;
        addInternal(obj);
    }

    /**
     * Unregisters a physics object from the manager's internal tracking systems.
     * <p>
     * This method only removes the object from the manager's maps and data store. It does
     * <strong>not</strong> remove the body from the Jolt physics simulation, handle network despawning,
     * or clean up associated constraints. For safe and complete removal, use
     * {@link #removeObject(UUID, VxRemovalReason)}.
     *
     * @param id The UUID of the object to unregister.
     * @return The removed object, or {@code null} if it was not found.
     */
    @VxUnsafe("Direct manipulation of internal physics objects. Use with caution.")
    @Nullable
    public VxAbstractBody remove(UUID id) {
        return removeInternal(id);
    }

    /**
     * Links a Jolt body ID to a VxAbstractBody instance for fast lookups.
     * This method is unsafe and should only be used internally.
     *
     * @param bodyId The Jolt body ID.
     * @param object The corresponding physics object.
     */
    @VxUnsafe("Direct manipulation of internal physics objects. Use with caution.")
    public void linkBodyId(int bodyId, VxAbstractBody object) {
        if (bodyId == 0 || bodyId == Jolt.cInvalidBodyId) {
            VxMainClass.LOGGER.warn("Attempted to link invalid Body ID {}", bodyId, new Throwable());
            return;
        }
        this.bodyIdToObjectMap.put(bodyId, object);
    }

    /**
     * Unlinks a Jolt body ID from its VxAbstractBody instance.
     * This method is unsafe and should only be used internally.
     *
     * @param bodyId The Jolt body ID to unlink.
     */
    @VxUnsafe("Direct manipulation of internal physics objects. Use with caution.")
    public void unlinkBodyId(int bodyId) {
        if (bodyId == 0 || bodyId == Jolt.cInvalidBodyId) return;
        this.bodyIdToObjectMap.remove(bodyId);
    }

    @Nullable
    public VxAbstractBody getByBodyId(int bodyId) {
        return bodyIdToObjectMap.get(bodyId);
    }

    public Collection<VxAbstractBody> getAllObjects() {
        return managedObjects.values();
    }

    public void onPhysicsTick(long timestampNanos) {
        physicsUpdater.update(timestampNanos, this.world);
    }

    public void onGameTick() {
        networkDispatcher.onGameTick();
        getAllObjects().forEach(obj -> obj.gameTick(world.getLevel()));
    }

    /**
     * Creates a new rigid body and adds it to the physics world.
     * Must be called on the Physics thread.
     *
     * @param type        The type of rigid body to create.
     * @param transform   The initial transform.
     * @param configurator A consumer to apply additional configuration.
     * @return The created body, or null on failure.
     */
    @Nullable
    public <T extends VxRigidBody> T createRigidBody(VxObjectType<T> type, VxTransform transform, Consumer<T> configurator) {
        T body = type.create(world, UUID.randomUUID());
        if (body == null) return null;
        body.getGameTransform().set(transform);
        configurator.accept(body);
        addConstructedBody(body, EActivation.DontActivate);
        return body;
    }

    /**
     * Creates a new soft body and adds it to the physics world.
     * Must be called on the Physics thread.
     *
     * @param type        The type of soft body to create.
     * @param transform   The initial transform.
     * @param configurator A consumer to apply additional configuration.
     * @return The created body, or null on failure.
     */
    @Nullable
    public <T extends VxSoftBody> T createSoftBody(VxObjectType<T> type, VxTransform transform, Consumer<T> configurator) {
        T body = type.create(world, UUID.randomUUID());
        if (body == null) return null;
        body.getGameTransform().set(transform);
        configurator.accept(body);
        addConstructedBody(body, EActivation.DontActivate);
        return body;
    }

    /**
     * Adds a pre-constructed body to the physics world.
     * This is typically used for newly created objects during gameplay.
     *
     * @param body       The body to add.
     * @param activation The initial activation state.
     */
    public void addConstructedBody(VxAbstractBody body, EActivation activation) {
        if (body instanceof VxRigidBody rigidBody) {
            addRigidBodyToPhysicsWorld(rigidBody, null, null, activation);
        } else if (body instanceof VxSoftBody softBody) {
            addSoftBodyToPhysicsWorld(softBody, activation);
        }
    }

    /**
     * Adds a body from serialized data, typically from storage.
     * This method correctly sets initial velocities and activates the body if needed.
     *
     * @param data The deserialized data container.
     * @return The created and added VxAbstractBody, or null on failure.
     */
    @Nullable
    public VxAbstractBody addSerializedBody(VxObjectStorage.SerializedBodyData data) {
        VxAbstractBody obj = VxObjectRegistry.getInstance().create(data.typeId(), world, data.id());
        if (obj == null) {
            VxMainClass.LOGGER.error("Failed to create object of type {} with ID {} from storage.", data.typeId(), data.id());
            return null;
        }

        obj.getGameTransform().set(data.transform());
        obj.readCreationData(data.customData());
        data.customData().release();

        // Activate if velocity is not zero
        boolean hasVelocity = data.linearVelocity().lengthSq() > 0.0001f || data.angularVelocity().lengthSq() > 0.0001f;
        EActivation activation = hasVelocity ? EActivation.Activate : EActivation.DontActivate;

        if (obj instanceof VxRigidBody rigidBody) {
            addRigidBodyToPhysicsWorld(rigidBody, data.linearVelocity(), data.angularVelocity(), activation);
        } else if (obj instanceof VxSoftBody softBody) {
            // Soft bodies currently don't have velocity persistence in this manner
            addSoftBodyToPhysicsWorld(softBody, activation);
        }
        return obj;
    }

    private void addRigidBodyToPhysicsWorld(VxRigidBody body, @Nullable Vec3 linearVelocity, @Nullable Vec3 angularVelocity, EActivation activation) {
        try (ShapeSettings shapeSettings = body.createShapeSettings()) {
            if (shapeSettings == null) throw new IllegalStateException("createShapeSettings returned null for type: " + body.getType().getTypeId());
            try (ShapeResult shapeResult = shapeSettings.create()) {
                if (shapeResult.hasError()) throw new IllegalStateException("Shape creation failed: " + shapeResult.getError());
                try (ShapeRefC shapeRef = shapeResult.get()) {
                    try (BodyCreationSettings bcs = body.createBodyCreationSettings(shapeRef)) {
                        bcs.setPosition(body.getGameTransform().getTranslation());
                        bcs.setRotation(body.getGameTransform().getRotation());
                        if (linearVelocity != null) bcs.setLinearVelocity(linearVelocity);
                        if (angularVelocity != null) bcs.setAngularVelocity(angularVelocity);

                        int bodyId = world.getBodyInterface().createAndAddBody(bcs, activation);
                        if (bodyId == Jolt.cInvalidBodyId) {
                            VxMainClass.LOGGER.error("Jolt failed to create/add rigid body for {}", body.getPhysicsId());
                            return;
                        }
                        body.setBodyId(bodyId);
                        addInternal(body);
                        body.onBodyAdded(world);
                    }
                }
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to create/add rigid body {}", body.getPhysicsId(), e);
        }
    }

    private void addSoftBodyToPhysicsWorld(VxSoftBody body, EActivation activation) {
        try (SoftBodySharedSettings sharedSettings = body.createSoftBodySharedSettings()) {
            if (sharedSettings == null) throw new IllegalStateException("createSoftBodySharedSettings returned null for type: " + body.getType().getTypeId());
            try (SoftBodyCreationSettings settings = body.createSoftBodyCreationSettings(sharedSettings)) {
                settings.setPosition(body.getGameTransform().getTranslation());
                settings.setRotation(body.getGameTransform().getRotation());

                int bodyId = world.getBodyInterface().createAndAddSoftBody(settings, activation);
                if (bodyId == Jolt.cInvalidBodyId) {
                    VxMainClass.LOGGER.error("Jolt failed to create/add soft body for {}", body.getPhysicsId());
                    return;
                }
                body.setBodyId(bodyId);
                addInternal(body);
                body.onBodyAdded(world);
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to create/add soft body {}", body.getPhysicsId(), e);
        }
    }

    private void addInternal(VxAbstractBody obj) {
        if (obj == null) return;
        managedObjects.computeIfAbsent(obj.getPhysicsId(), id -> {
            EBodyType type = obj instanceof VxSoftBody ? EBodyType.SoftBody : EBodyType.RigidBody;
            int index = dataStore.addObject(id, type);
            obj.setDataStoreIndex(index);

            if (obj.getBodyId() != 0) {
                linkBodyId(obj.getBodyId(), obj);
            }
            startTracking(obj);
            world.getConstraintManager().getDataSystem().onDependencyLoaded(id);
            return obj;
        });
    }

    @Nullable
    private VxAbstractBody removeInternal(UUID id) {
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

    public void removeObject(UUID id, VxRemovalReason reason) {
        final VxAbstractBody obj = this.removeInternal(id);
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
            VxTransform transform = obj.getTransform(world);
            if (transform != null) {
                obj.getGameTransform().set(transform);
            }
            objectStorage.storeObject(obj);
        } else if (reason == VxRemovalReason.DISCARD) {
            objectStorage.removeData(id);
        }

        world.getConstraintManager().removeConstraintsForObject(id, reason == VxRemovalReason.DISCARD);

        final int bodyIdToRemove = obj.getBodyId();
        if (bodyIdToRemove != 0 && bodyIdToRemove != Jolt.cInvalidBodyId) {
            world.execute(() -> {
                if (world.getBodyInterface().isAdded(bodyIdToRemove)) {
                    world.getBodyInterface().removeBody(bodyIdToRemove);
                }
                world.getBodyInterface().destroyBody(bodyIdToRemove);
            });
        }
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

    @Nullable
    public VxAbstractBody getObject(UUID id) {
        return managedObjects.get(id);
    }

    public CompletableFuture<VxAbstractBody> getOrLoadObject(UUID id) {
        if (id == null) {
            return CompletableFuture.completedFuture(null);
        }
        VxAbstractBody loadedObject = getObject(id);
        if (loadedObject != null) {
            return CompletableFuture.completedFuture(loadedObject);
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