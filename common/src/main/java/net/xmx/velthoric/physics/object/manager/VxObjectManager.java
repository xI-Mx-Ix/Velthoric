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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.persistence.VxBodyStorage;
import net.xmx.velthoric.physics.object.registry.VxObjectRegistry;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;
import net.xmx.velthoric.physics.object.type.factory.VxRigidBodyFactory;
import net.xmx.velthoric.physics.object.type.factory.VxSoftBodyFactory;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Manages the lifecycle, tracking, and state of all physics objects within a {@link VxPhysicsWorld}.
 * This class acts as the central hub for creating, removing, and accessing physics bodies,
 * utilizing a data-oriented approach with {@link VxObjectDataStore}.
 *
 * @author xI-Mx-Ix
 */
public class VxObjectManager {

    //================================================================================
    // Fields
    //================================================================================

    private final VxPhysicsWorld world;
    private final VxBodyStorage objectStorage;
    private final VxObjectDataStore dataStore;
    private final VxPhysicsUpdater physicsUpdater;
    private final VxObjectNetworkDispatcher networkDispatcher;

    /**
     * Main map for tracking all active object instances by their unique persistent ID.
     */
    private final Map<UUID, VxBody> managedObjects = new ConcurrentHashMap<>();

    /**
     * A fast lookup map from the Jolt physics body ID to the corresponding object wrapper.
     */
    private final Int2ObjectMap<VxBody> bodyIdToObjectMap = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

    /**
     * A spatial map that groups objects by the chunk they are in for efficient proximity queries.
     * The key is the long-encoded chunk position.
     */
    private final Long2ObjectMap<List<VxBody>> objectsByChunk = new Long2ObjectOpenHashMap<>();

    //================================================================================
    // Constructor & Lifecycle
    //================================================================================

    /**
     * Constructs a new VxObjectManager for the given physics world.
     *
     * @param world The physics world this manager belongs to.
     */
    public VxObjectManager(VxPhysicsWorld world) {
        this.world = world;
        this.dataStore = new VxObjectDataStore();
        this.objectStorage = new VxBodyStorage(world.getLevel(), this);
        this.physicsUpdater = new VxPhysicsUpdater(this);
        this.networkDispatcher = new VxObjectNetworkDispatcher(world.getLevel(), this);
    }

    /**
     * Initializes the manager and its subsystems, such as storage and network dispatching.
     */
    public void initialize() {
        objectStorage.initialize();
        networkDispatcher.start();
    }

    /**
     * Shuts down the manager, ensuring all objects are saved and subsystems are terminated gracefully.
     */
    public void shutdown() {
        networkDispatcher.stop();
        getAllObjects().forEach(objectStorage::storeObject);
        objectStorage.saveDirtyRegions();
        clear();
        objectStorage.shutdown();
    }

    /**
     * Clears all internal tracking maps and resets the data store.
     */
    private void clear() {
        managedObjects.clear();
        bodyIdToObjectMap.clear();
        dataStore.clear();
    }

    //================================================================================
    // Tick Handlers
    //================================================================================

    /**
     * Called on every physics thread tick to update object states.
     *
     * @param world The physics world instance.
     */
    public void onPhysicsTick(VxPhysicsWorld world) {
        physicsUpdater.onPhysicsTick(world);
    }

    /**
     * Called on every game thread tick for network and other game-side updates.
     *
     * @param level The server level instance.
     */
    public void onGameTick(ServerLevel level) {
        networkDispatcher.onGameTick();
        physicsUpdater.onGameTick(level);
    }

    //================================================================================
    // Public API: Object Creation
    //================================================================================

    /**
     * Creates a new rigid body and adds it to the physics world.
     * This method must be called on the main physics thread.
     *
     * @param type         The type of rigid body to create.
     * @param transform    The initial transform (position and rotation).
     * @param configurator A consumer to apply additional configuration to the body before it's added.
     * @return The created body, or null on failure.
     */
    @Nullable
    public <T extends VxRigidBody> T createRigidBody(VxObjectType<T> type, VxTransform transform, Consumer<T> configurator) {
        T body = type.create(world, UUID.randomUUID());
        if (body == null) return null;
        configurator.accept(body);
        addConstructedBody(body, EActivation.DontActivate, transform);
        return body;
    }

    /**
     * Creates a new soft body and adds it to the physics world.
     * This method must be called on the main physics thread.
     *
     * @param type         The type of soft body to create.
     * @param transform    The initial transform (position and rotation).
     * @param configurator A consumer to apply additional configuration to the body before it's added.
     * @return The created body, or null on failure.
     */
    @Nullable
    public <T extends VxSoftBody> T createSoftBody(VxObjectType<T> type, VxTransform transform, Consumer<T> configurator) {
        T body = type.create(world, UUID.randomUUID());
        if (body == null) return null;
        configurator.accept(body);
        addConstructedBody(body, EActivation.DontActivate, transform);
        return body;
    }

    //================================================================================
    // Core Lifecycle Management: Addition & Removal
    //================================================================================

    /**
     * Adds a pre-constructed body to the physics world with a specified initial transform.
     * This is the core method for adding new objects during gameplay.
     *
     * @param body       The body to add.
     * @param activation The initial activation state for the Jolt body.
     * @param transform  The initial transform to set in the data store.
     */
    public void addConstructedBody(VxBody body, EActivation activation, VxTransform transform) {
        // Step 1: Reserve a slot in the data store and add to internal tracking maps.
        addInternal(body);
        int index = body.getDataStoreIndex();

        // Step 2: Write the initial transform to the data store. This ensures the correct
        // spawn position is available before any network packets are created.
        if (index != -1) {
            dataStore.posX[index] = transform.getTranslation().x();
            dataStore.posY[index] = transform.getTranslation().y();
            dataStore.posZ[index] = transform.getTranslation().z();
            dataStore.rotX[index] = transform.getRotation().getX();
            dataStore.rotY[index] = transform.getRotation().getY();
            dataStore.rotZ[index] = transform.getRotation().getZ();
            dataStore.rotW[index] = transform.getRotation().getW();
        }

        // Step 3: Now that the data store is populated with the correct initial state,
        // notify the network dispatcher to send a spawn packet to clients.
        networkDispatcher.onObjectAdded(body);

        // Step 4: Create the actual physics body in the Jolt simulation.
        if (body instanceof VxRigidBody rigidBody) {
            addRigidBodyToPhysicsWorld(rigidBody, null, null, activation);
        } else if (body instanceof VxSoftBody softBody) {
            addSoftBodyToPhysicsWorld(softBody, activation);
        }
    }

    /**
     * Adds a body from serialized data, typically when loading from storage.
     *
     * @param data The deserialized data container.
     * @return The created and added {@link VxBody}, or null on failure.
     */
    @Nullable
    public VxBody addSerializedBody(VxBodyStorage.SerializedBodyData data) {
        VxBody obj = VxObjectRegistry.getInstance().create(data.typeId(), world, data.id());
        if (obj == null) {
            VxMainClass.LOGGER.error("Failed to create object of type {} with ID {} from storage.", data.typeId(), data.id());
            return null;
        }

        addInternal(obj);
        int index = obj.getDataStoreIndex();
        if (index != -1) {
            dataStore.posX[index] = data.transform().getTranslation().x();
            dataStore.posY[index] = data.transform().getTranslation().y();
            dataStore.posZ[index] = data.transform().getTranslation().z();
            dataStore.rotX[index] = data.transform().getRotation().getX();
            dataStore.rotY[index] = data.transform().getRotation().getY();
            dataStore.rotZ[index] = data.transform().getRotation().getZ();
            dataStore.rotW[index] = data.transform().getRotation().getW();
        }

        obj.readPersistenceData(data.persistenceData());
        data.persistenceData().release();

        networkDispatcher.onObjectAdded(obj);

        boolean hasVelocity = data.linearVelocity().lengthSq() > 0.0001f || data.angularVelocity().lengthSq() > 0.0001f;
        EActivation activation = hasVelocity ? EActivation.Activate : EActivation.DontActivate;

        if (obj instanceof VxRigidBody rigidBody) {
            addRigidBodyToPhysicsWorld(rigidBody, data.linearVelocity(), data.angularVelocity(), activation);
        } else if (obj instanceof VxSoftBody softBody) {
            addSoftBodyToPhysicsWorld(softBody, activation);
        }
        return obj;
    }

    /**
     * Completely removes a physics object from the world with a specified reason.
     * This handles removal from all internal systems: data store, tracking, network, storage, and the Jolt simulation.
     *
     * @param id     The UUID of the object to remove.
     * @param reason The reason for removal, which determines if the object is saved or discarded.
     */
    public void removeObject(UUID id, VxRemovalReason reason) {
        // Step 1: Atomically remove from internal data structures.
        final VxBody obj = this.removeInternal(id);
        if (obj == null) {
            VxMainClass.LOGGER.warn("Attempted to remove non-existent body: {}", id);
            if (reason == VxRemovalReason.DISCARD) {
                objectStorage.removeData(id);
            }
            world.getConstraintManager().removeConstraintsForObject(id, reason == VxRemovalReason.DISCARD);
            return;
        }

        // Step 2: Notify network dispatcher to despawn the object on clients.
        networkDispatcher.onObjectRemoved(obj);

        // Step 3: Stop server-side chunk tracking.
        stopTracking(obj);

        // Step 4: Fire the removal callback on the object itself.
        obj.onBodyRemoved(world, reason);

        // Step 5: Handle persistence based on the removal reason.
        if (reason == VxRemovalReason.SAVE) {
            objectStorage.storeObject(obj);
        } else if (reason == VxRemovalReason.DISCARD) {
            objectStorage.removeData(id);
        }

        // Step 6: Clean up any associated constraints.
        world.getConstraintManager().removeConstraintsForObject(id, reason == VxRemovalReason.DISCARD);

        // Step 7: Schedule the removal of the body from the Jolt simulation on the physics thread.
        final int bodyIdToRemove = obj.getBodyId();
        if (bodyIdToRemove != 0 && bodyIdToRemove != Jolt.cInvalidBodyId) {
            world.execute(() -> {
                BodyInterface bodyInterface = world.getBodyInterface();
                if (bodyInterface.isAdded(bodyIdToRemove)) {
                    bodyInterface.removeBody(bodyIdToRemove);
                }
                bodyInterface.destroyBody(bodyIdToRemove);
            });
        }
    }

    //================================================================================
    // Internal Helpers
    //================================================================================

    /**
     * Handles the internal registration of a physics object.
     * This method reserves a data store index, adds the object to managed maps, and begins server-side chunk tracking.
     * It does NOT notify the network dispatcher; that must be done after the initial state is written.
     *
     * @param obj The object to register.
     */
    private void addInternal(VxBody obj) {
        if (obj == null) return;
        managedObjects.computeIfAbsent(obj.getPhysicsId(), id -> {
            EBodyType type = obj instanceof VxSoftBody ? EBodyType.SoftBody : EBodyType.RigidBody;
            int index = dataStore.addObject(id, type);
            obj.setDataStoreIndex(index);
            startTracking(obj); // Manages server-side chunk lists for visibility checks.
            return obj;
        });
    }

    /**
     * Handles the internal deregistration of a physics object.
     * This method frees its data store index and removes it from tracking maps. It does not handle Jolt body removal.
     * This method is synchronized to ensure the entire removal process is atomic.
     *
     * @param id The UUID of the object to remove.
     * @return The removed object, or null if it was not found.
     */
    @Nullable
    private synchronized VxBody removeInternal(UUID id) {
        VxBody obj = managedObjects.remove(id);
        if (obj != null) {
            dataStore.removeObject(id);
            obj.setDataStoreIndex(-1);
            if (obj.getBodyId() != 0) {
                bodyIdToObjectMap.remove(obj.getBodyId());
            }
        }
        return obj;
    }

    /**
     * Internal helper to create and add a rigid body to the Jolt physics system.
     *
     * @param body            The rigid body wrapper.
     * @param linearVelocity  The initial linear velocity (can be null).
     * @param angularVelocity The initial angular velocity (can be null).
     * @param activation      The initial activation state.
     */
    private void addRigidBodyToPhysicsWorld(VxRigidBody body, @Nullable Vec3 linearVelocity, @Nullable Vec3 angularVelocity, EActivation activation) {
        try {
            VxRigidBodyFactory factory = (shapeSettings, bcs) -> {
                try (ShapeResult shapeResult = shapeSettings.create()) {
                    if (shapeResult.hasError()) {
                        throw new IllegalStateException("Shape creation failed: " + shapeResult.getError());
                    }
                    try (ShapeRefC shapeRef = shapeResult.get()) {
                        int index = body.getDataStoreIndex();
                        bcs.setShape(shapeRef);
                        bcs.setPosition(dataStore.posX[index], dataStore.posY[index], dataStore.posZ[index]);
                        bcs.setRotation(new Quat(dataStore.rotX[index], dataStore.rotY[index], dataStore.rotZ[index], dataStore.rotW[index]));
                        if (linearVelocity != null) bcs.setLinearVelocity(linearVelocity);
                        if (angularVelocity != null) bcs.setAngularVelocity(angularVelocity);

                        return world.getBodyInterface().createAndAddBody(bcs, activation);
                    }
                }
            };

            int bodyId = body.createJoltBody(factory);

            if (bodyId == Jolt.cInvalidBodyId) {
                VxMainClass.LOGGER.error("Jolt failed to create/add rigid body for {}", body.getPhysicsId());
                removeInternal(body.getPhysicsId()); // Clean up failed addition.
                return;
            }
            body.setBodyId(bodyId);
            bodyIdToObjectMap.put(bodyId, body);
            body.onBodyAdded(world);
            world.getConstraintManager().getDataSystem().onDependencyLoaded(body.getPhysicsId());

        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to create/add rigid body {}", body.getPhysicsId(), e);
            removeInternal(body.getPhysicsId()); // Clean up on exception.
        }
    }

    /**
     * Internal helper to create and add a soft body to the Jolt physics system.
     *
     * @param body       The soft body wrapper.
     * @param activation The initial activation state.
     */
    private void addSoftBodyToPhysicsWorld(VxSoftBody body, EActivation activation) {
        try {
            VxSoftBodyFactory factory = (sharedSettings, creationSettings) -> {
                try (sharedSettings; creationSettings) {
                    int index = body.getDataStoreIndex();
                    creationSettings.setPosition(dataStore.posX[index], dataStore.posY[index], dataStore.posZ[index]);
                    creationSettings.setRotation(new Quat(dataStore.rotX[index], dataStore.rotY[index], dataStore.rotZ[index], dataStore.rotW[index]));

                    return world.getBodyInterface().createAndAddSoftBody(creationSettings, activation);
                }
            };

            int bodyId = body.createJoltBody(factory);

            if (bodyId == Jolt.cInvalidBodyId) {
                VxMainClass.LOGGER.error("Jolt failed to create/add soft body for {}", body.getPhysicsId());
                removeInternal(body.getPhysicsId()); // Clean up failed addition.
                return;
            }
            body.setBodyId(bodyId);
            bodyIdToObjectMap.put(bodyId, body);
            body.onBodyAdded(world);
            world.getConstraintManager().getDataSystem().onDependencyLoaded(body.getPhysicsId());
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to create/add soft body {}", body.getPhysicsId(), e);
            removeInternal(body.getPhysicsId()); // Clean up on exception.
        }
    }

    //================================================================================
    // Chunk Tracking & Spatial Queries
    //================================================================================

    /**
     * Starts tracking an object on the server, adding it to the appropriate chunk list.
     *
     * @param body The object to start tracking.
     */
    public void startTracking(VxBody body) {
        int index = body.getDataStoreIndex();
        if (index == -1) return;
        long key = getObjectChunkPos(index).toLong();
        dataStore.chunkKey[index] = key;
        synchronized (objectsByChunk) {
            objectsByChunk.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(body);
        }
    }

    /**
     * Stops tracking an object on the server, removing it from its chunk list.
     *
     * @param body The object to stop tracking.
     */
    public void stopTracking(VxBody body) {
        int index = body.getDataStoreIndex();
        if (index == -1) return;
        long key = dataStore.chunkKey[index];
        if (key != Long.MAX_VALUE) {
            synchronized (objectsByChunk) {
                List<VxBody> list = objectsByChunk.get(key);
                if (list != null) {
                    list.remove(body);
                    if (list.isEmpty()) {
                        objectsByChunk.remove(key);
                    }
                }
            }
        }
    }

    /**
     * Updates the chunk tracking information for a body when it moves across a chunk border.
     *
     * @param body    The body that moved.
     * @param fromKey The long-encoded key of the chunk it moved from.
     * @param toKey   The long-encoded key of the chunk it moved to.
     */
    void updateObjectTracking(VxBody body, long fromKey, long toKey) {
        int index = body.getDataStoreIndex();
        if (index != -1) {
            dataStore.chunkKey[index] = toKey;
        }

        // Remove from the old chunk's list.
        if (fromKey != Long.MAX_VALUE) {
            synchronized (objectsByChunk) {
                List<VxBody> fromList = objectsByChunk.get(fromKey);
                if (fromList != null) {
                    fromList.remove(body);
                    if (fromList.isEmpty()) {
                        objectsByChunk.remove(fromKey);
                    }
                }
            }
        }
        // Add to the new chunk's list.
        synchronized (objectsByChunk) {
            objectsByChunk.computeIfAbsent(toKey, k -> new CopyOnWriteArrayList<>()).add(body);
        }
        // Notify the network dispatcher about the movement for client-side tracking updates.
        networkDispatcher.onObjectMoved(body, new ChunkPos(fromKey), new ChunkPos(toKey));
    }

    /**
     * Handles the unloading of all physics objects within a specific chunk.
     * The objects are removed from the active simulation and queued for saving.
     *
     * @param chunkPos The position of the chunk being unloaded.
     */
    public void onChunkUnload(ChunkPos chunkPos) {
        List<VxBody> objectsInChunk = getObjectsInChunk(chunkPos);
        if (objectsInChunk.isEmpty()) {
            return;
        }
        // Create a copy to avoid ConcurrentModificationException, as removeObject
        // will modify the underlying list via stopTracking.
        for (VxBody obj : List.copyOf(objectsInChunk)) {
            removeObject(obj.getPhysicsId(), VxRemovalReason.SAVE);
        }
    }

    //================================================================================
    // Data Accessors & State Modification
    //================================================================================

    /**
     * Retrieves a physics object by its Jolt body ID.
     *
     * @param bodyId The Jolt body ID.
     * @return The corresponding {@link VxBody}, or null if not found.
     */
    @Nullable
    public VxBody getByBodyId(int bodyId) {
        return bodyIdToObjectMap.get(bodyId);
    }

    /**
     * Gets a loaded physics object by its UUID.
     *
     * @param id The UUID of the object.
     * @return The {@link VxBody} instance, or null if not currently loaded.
     */
    @Nullable
    public VxBody getObject(UUID id) {
        return managedObjects.get(id);
    }

    /**
     * Gets a collection of all managed physics objects.
     *
     * @return A collection view of all {@link VxBody} instances.
     */
    public Collection<VxBody> getAllObjects() {
        return managedObjects.values();
    }

    /**
     * Retrieves a list of all physics objects within a specific chunk.
     *
     * @param pos The position of the chunk.
     * @return A list of objects in that chunk, or an empty list if none exist.
     */
    public List<VxBody> getObjectsInChunk(ChunkPos pos) {
        synchronized (objectsByChunk) {
            return objectsByChunk.getOrDefault(pos.toLong(), Collections.emptyList());
        }
    }

    /**
     * Asynchronously gets a physics object by its UUID, loading it from storage if it's not already in memory.
     *
     * @param id The UUID of the object.
     * @return A {@link CompletableFuture} that will complete with the object, or null if it cannot be found or loaded.
     */
    public CompletableFuture<VxBody> getOrLoadObject(UUID id) {
        if (id == null) {
            return CompletableFuture.completedFuture(null);
        }
        VxBody loadedObject = getObject(id);
        if (loadedObject != null) {
            return CompletableFuture.completedFuture(loadedObject);
        }
        return objectStorage.loadObject(id);
    }

    /**
     * Populates a {@link VxTransform} object with the current position and rotation from the data store.
     *
     * @param dataStoreIndex The index of the object in the data store.
     * @param out            The {@link VxTransform} object to populate.
     */
    public void getTransform(int dataStoreIndex, VxTransform out) {
        if (dataStoreIndex >= 0 && dataStoreIndex < dataStore.getCapacity()) {
            out.getTranslation().set(dataStore.posX[dataStoreIndex], dataStore.posY[dataStoreIndex], dataStore.posZ[dataStoreIndex]);
            out.getRotation().set(dataStore.rotX[dataStoreIndex], dataStore.rotY[dataStoreIndex], dataStore.rotZ[dataStoreIndex], dataStore.rotW[dataStoreIndex]);
        }
    }

    /**
     * Calculates the chunk position of an object based on its state in the data store.
     *
     * @param dataStoreIndex The index of the object.
     * @return The calculated {@link ChunkPos}.
     */
    public ChunkPos getObjectChunkPos(int dataStoreIndex) {
        if (dataStoreIndex >= 0 && dataStoreIndex < dataStore.getCapacity()) {
            return new ChunkPos(
                    SectionPos.posToSectionCoord(dataStore.posX[dataStoreIndex]),
                    SectionPos.posToSectionCoord(dataStore.posZ[dataStoreIndex])
            );
        }
        return new ChunkPos(0, 0); // Fallback
    }

    /**
     * Marks an object's custom data as dirty, queuing it for network synchronization.
     *
     * @param body The body whose custom data has changed.
     */
    public void markCustomDataDirty(VxBody body) {
        if (body.getDataStoreIndex() != -1) {
            getDataStore().isCustomDataDirty[body.getDataStoreIndex()] = true;
        }
    }

    //================================================================================
    // Persistence
    //================================================================================

    /**
     * Schedules a task on the physics thread to save all currently managed objects
     * and any modified region files to disk. This ensures thread-safe access to physics data.
     */
    public void saveAll() {
        VxPhysicsWorld physicsWorld = getPhysicsWorld();
        if (physicsWorld != null && physicsWorld.isRunning()) {
            physicsWorld.execute(() -> {
                Collection<VxBody> allObjects = getAllObjects();
                if (!allObjects.isEmpty()) {
                    objectStorage.storeObjects(allObjects);
                }
                objectStorage.saveDirtyRegions();
            });
        }
    }

    //================================================================================
    // Getters for Subsystems
    //================================================================================

    public VxPhysicsWorld getPhysicsWorld() {
        return world;
    }

    public VxObjectDataStore getDataStore() {
        return dataStore;
    }

    public VxBodyStorage getObjectStorage() {
        return objectStorage;
    }

    public VxObjectNetworkDispatcher getNetworkDispatcher() {
        return networkDispatcher;
    }
}