/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.server;

import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.core.body.VxRemovalReason;
import net.xmx.velthoric.core.body.VxAbstractBodyManager;
import net.xmx.velthoric.core.body.registry.VxBodyRegistry;
import net.xmx.velthoric.core.body.registry.VxBodyType;
import net.xmx.velthoric.core.body.tracking.VxSpatialManager;
import net.xmx.velthoric.core.body.type.VxBody;
import net.xmx.velthoric.core.body.type.VxRigidBody;
import net.xmx.velthoric.core.body.type.VxSoftBody;
import net.xmx.velthoric.core.mounting.manager.VxMountingManager;
import net.xmx.velthoric.core.network.internal.VxNetworkDispatcher;
import net.xmx.velthoric.core.network.synchronization.manager.VxServerSyncManager;
import net.xmx.velthoric.core.persistence.impl.body.VxBodyStorage;
import net.xmx.velthoric.core.persistence.impl.body.VxSerializedBodyData;
import net.xmx.velthoric.core.physics.VxJoltBridge;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * Manages the lifecycle, state, and persistence of all physics bodies within a specific {@link VxPhysicsWorld}.
 * <p>
 * This class serves as the central orchestration point for the physics simulation. Its responsibilities include:
 * <ul>
 *     <li><b>Lifecycle Management:</b> Creating, activating, deactivating, and destroying physics bodies.</li>
 *     <li><b>Data Storage:</b> Managing the {@link VxServerBodyDataStore}, which uses a Structure-of-Arrays (SoA) layout for CPU-cache-efficient access to physics data.</li>
 *     <li><b>Jolt Integration:</b> Bridging the gap between high-level Java objects and the native Jolt Physics engine via {@link VxJoltBridge}.</li>
 *     <li><b>Spatial Partitioning:</b> Delegating chunk-based tracking to the {@link VxSpatialManager}.</li>
 *     <li><b>Persistence:</b> coordinating with {@link VxBodyStorage} to save and load body states to disk.</li>
 *     <li><b>Networking:</b> Handling synchronization of physics states to clients via {@link VxNetworkDispatcher}.</li>
 * </ul>
 *
 * @author xI-Mx-Ix
 */
public class VxServerBodyManager extends VxAbstractBodyManager {

    private final VxPhysicsWorld world;
    private final VxBodyStorage bodyStorage;
    private final VxServerBodyDataStore dataStore;
    private final VxPhysicsExtractor physicsExtractor;
    private final VxNetworkDispatcher networkDispatcher;
    private final VxServerSyncManager serverSyncManager;
    private final VxSpatialManager spatialManager;
    private final VxMountingManager mountingManager;

    /**
     * Optimized lookup map connecting Jolt's native integer BodyIDs to the Java wrapper {@link VxBody}.
     * This is crucial for handling callbacks (e.g., collisions) from the native physics engine.
     */
    private final Int2ObjectMap<VxBody> joltBodyIdToVxBodyMap = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

    /**
     * A pool of recycled network IDs. When a body is removed, its integer network ID is returned
     * here to be reused, keeping the integer range compact for network packet optimization.
     */
    private final Deque<Integer> freeNetworkIds = new ArrayDeque<>();

    /**
     * Counter for generating new network IDs when the free pool is empty.
     */
    private int nextNetworkId = 1;

    /**
     * Constructs a new manager for the specified physics world.
     *
     * @param world The physics world instance this manager belongs to.
     */
    public VxServerBodyManager(VxPhysicsWorld world) {
        this.world = world;
        this.dataStore = new VxServerBodyDataStore();
        this.bodyStorage = new VxBodyStorage(world.getLevel());
        this.physicsExtractor = new VxPhysicsExtractor(this);
        this.networkDispatcher = new VxNetworkDispatcher(world.getLevel(), this);
        this.serverSyncManager = new VxServerSyncManager(this);
        this.spatialManager = new VxSpatialManager();
        this.mountingManager = new VxMountingManager(this.world);
    }

    /**
     * Initializes the storage and networking subsystems.
     * Should be called after the world is fully constructed but before simulation starts.
     */
    public void initialize() {
        networkDispatcher.start();
    }

    /**
     * Performs a clean shutdown of the physics manager.
     * <p>
     * This stops network dispatching, flushes all pending persistence data to disk,
     * and clears internal state maps.
     */
    public void shutdown() {
        networkDispatcher.stop();
        VxMainClass.LOGGER.debug("Flushing physics body persistence for world {}...", world.getDimensionKey().location());
        // Force a blocking flush to ensure data integrity on shutdown
        flushPersistence(true);
        VxMainClass.LOGGER.debug("Physics body persistence flushed for world {}.", world.getDimensionKey().location());
        clear();
        bodyStorage.shutdown();
    }

    /**
     * Clears all runtime references to bodies and resets internal counters.
     * Does not handle disk persistence; use {@link #shutdown()} or {@link #removeBody} for that.
     */
    private void clear() {
        this.clearInternal();
        joltBodyIdToVxBodyMap.clear();
        freeNetworkIds.clear();
        nextNetworkId = 1;
        dataStore.clear();
    }

    /**
     * Called during the physics thread tick. Delegates to the updater to advance simulation state.
     *
     * @param world The physics world being ticked.
     */
    public void onPhysicsTick(VxPhysicsWorld world) {
        physicsExtractor.onPhysicsTick(world);
    }

    /**
     * Called during the main game thread tick. Handles synchronization logic that requires
     * interaction with the Minecraft server level.
     *
     * @param level The Minecraft server level.
     */
    public void onGameTick(ServerLevel level) {
        networkDispatcher.onGameTick();
        physicsExtractor.onGameTick(level);
        mountingManager.onGameTick();
    }

    //================================================================================
    // Public API: Body Creation
    //================================================================================

    /**
     * Creates and registers a new Rigid Body in the physics world.
     *
     * @param type         The registry type of the rigid body.
     * @param transform    The initial position and rotation.
     * @param configurator A consumer to apply custom settings (friction, mass, etc.) before the body is added to the simulation.
     * @param <T>          The specific type of rigid body.
     * @return The created body instance, or null if creation failed.
     */
    @Nullable
    public <T extends VxRigidBody> T createRigidBody(VxBodyType<T> type, VxTransform transform, Consumer<T> configurator) {
        return createRigidBody(type, transform, EActivation.DontActivate, configurator);
    }

    /**
     * Creates and registers a new Rigid Body with a specific activation state.
     *
     * @param type         The registry type of the rigid body.
     * @param transform    The initial position and rotation.
     * @param activation   Whether the body should be active (simulating) or sleeping immediately upon creation.
     * @param configurator A consumer to apply custom settings before the body is added.
     * @param <T>          The specific type of rigid body.
     * @return The created body instance, or null if creation failed.
     */
    @Nullable
    public <T extends VxRigidBody> T createRigidBody(VxBodyType<T> type, VxTransform transform, EActivation activation, Consumer<T> configurator) {
        T body = type.create(world, UUID.randomUUID());
        if (body == null) return null;
        configurator.accept(body);
        addConstructedBody(body, activation, transform);
        return body;
    }

    /**
     * Creates and registers a new Soft Body in the physics world.
     *
     * @param type         The registry type of the soft body.
     * @param transform    The initial position and rotation.
     * @param configurator A consumer to apply custom settings (cloth settings, stiffness) before the body is added.
     * @param <T>          The specific type of soft body.
     * @return The created body instance, or null if creation failed.
     */
    @Nullable
    public <T extends VxSoftBody> T createSoftBody(VxBodyType<T> type, VxTransform transform, Consumer<T> configurator) {
        return createSoftBody(type, transform, EActivation.DontActivate, configurator);
    }

    /**
     * Creates and registers a new Soft Body with a specific activation state.
     *
     * @param type         The registry type of the soft body.
     * @param transform    The initial position and rotation.
     * @param activation   Whether the body should be active (simulating) or sleeping immediately upon creation.
     * @param configurator A consumer to apply custom settings before the body is added.
     * @param <T>          The specific type of soft body.
     * @return The created body instance, or null if creation failed.
     */
    @Nullable
    public <T extends VxSoftBody> T createSoftBody(VxBodyType<T> type, VxTransform transform, EActivation activation, Consumer<T> configurator) {
        T body = type.create(world, UUID.randomUUID());
        if (body == null) return null;
        configurator.accept(body);
        addConstructedBody(body, activation, transform);
        return body;
    }

    //================================================================================
    // Core Lifecycle Management: Addition & Removal
    //================================================================================

    /**
     * Finalizes the creation of a body by adding it to the internal systems and the Jolt engine.
     * <p>
     * This method syncs the {@link VxServerBodyDataStore} with the initial transform and velocity,
     * retrieves the desired motion type from the data store, and triggers the native Jolt creation.
     *
     * @param body       The body instance to add.
     * @param activation The initial activation state for the Jolt simulation.
     * @param transform  The world-space transform for the body.
     */
    public void addConstructedBody(VxBody body, EActivation activation, VxTransform transform) {
        addInternal(body);
        int index = body.getDataStoreIndex();

        if (index != -1) {
            dataStore.posX[index] = transform.getTranslation().x();
            dataStore.posY[index] = transform.getTranslation().y();
            dataStore.posZ[index] = transform.getTranslation().z();
            dataStore.rotX[index] = transform.getRotation().getX();
            dataStore.rotY[index] = transform.getRotation().getY();
            dataStore.rotZ[index] = transform.getRotation().getZ();
            dataStore.rotW[index] = transform.getRotation().getW();
        }

        // Retrieve properties from DataStore that were potentially modified by the configurator
        Vec3 linearVelocity = new Vec3(dataStore.velX[index], dataStore.velY[index], dataStore.velZ[index]);
        Vec3 angularVelocity = new Vec3(dataStore.angVelX[index], dataStore.angVelY[index], dataStore.angVelZ[index]);
        EMotionType motionType = dataStore.motionType[index];

        mountingManager.onBodyAdded(body);
        networkDispatcher.onBodyAdded(body);

        if (body instanceof VxRigidBody rigidBody) {
            VxJoltBridge.INSTANCE.createAndAddJoltRigidBody(rigidBody, this, linearVelocity, angularVelocity, activation, motionType);
        } else if (body instanceof VxSoftBody softBody) {
            VxJoltBridge.INSTANCE.createAndAddJoltSoftBody(softBody, this, linearVelocity, angularVelocity, activation);
        }
    }

    /**
     * Reconstitutes a body from serialized storage data.
     * <p>
     * Unlike {@link #createRigidBody}, this uses stored byte data to restore the exact state
     * (velocities, custom properties, vertices) of the body.
     *
     * @param data The serialized data wrapper containing ID, type, and payload.
     * @return The restored body, or null if creation failed.
     */
    @Nullable
    public VxBody addSerializedBody(VxSerializedBodyData data) {
        if (managedBodies.containsKey(data.id())) {
            return managedBodies.get(data.id());
        }

        VxBody body = VxBodyRegistry.getInstance().create(data.typeId(), world, data.id());
        if (body == null) {
            VxMainClass.LOGGER.error("Failed to create body of type {} with ID {} from storage.", data.typeId(), data.id());
            return null;
        }

        addInternal(body);

        // Deserialize internal state (transform, velocity, vertices, user data) into the body and DataStore.
        // This must occur after addInternal so the body has a valid DataStore index.
        body.readInternalPersistenceData(data.bodyData());
        data.bodyData().release();

        int index = body.getDataStoreIndex();
        if (index == -1) {
            return null; // Should technically be unreachable if addInternal succeeded
        }

        // Extract restored kinematics from DataStore
        Vec3 linearVelocity = new Vec3(dataStore.velX[index], dataStore.velY[index], dataStore.velZ[index]);
        Vec3 angularVelocity = new Vec3(dataStore.angVelX[index], dataStore.angVelY[index], dataStore.angVelZ[index]);
        EMotionType motionType = dataStore.motionType[index];

        // Determine if the body should be awake based on its velocity
        boolean shouldActivate = linearVelocity.lengthSq() > 0.0001f || angularVelocity.lengthSq() > 0.0001f;
        EActivation activation = shouldActivate ? EActivation.Activate : EActivation.DontActivate;

        mountingManager.onBodyAdded(body);
        networkDispatcher.onBodyAdded(body);

        if (body instanceof VxRigidBody rigidBody) {
            VxJoltBridge.INSTANCE.createAndAddJoltRigidBody(rigidBody, this, linearVelocity, angularVelocity, activation, motionType);
        } else if (body instanceof VxSoftBody softBody) {
            VxJoltBridge.INSTANCE.createAndAddJoltSoftBody(softBody, this, linearVelocity, angularVelocity, activation);
        }
        return body;
    }

    /**
     * Initiates the removal of a body identified by its unique identifier.
     * <p>
     * If the body is currently loaded in memory, it will be removed from the simulation
     * and internal registries. If the body is not loaded, this method primarily ensures
     * that dependent constraints are cleaned up.
     * <p>
     * <b>Persistence Note:</b> This method does not trigger an immediate disk write.
     * If the reason is {@link VxRemovalReason#DISCARD}, the body effectively ceases to exist
     * in the persistent store the next time its containing chunk is saved, as it will
     * no longer be present in the serialization list.
     *
     * @param id     The UUID of the body to remove.
     * @param reason The reason for removal, utilized for callbacks and dependency logic.
     */
    public void removeBody(UUID id, VxRemovalReason reason) {
        final VxBody body = this.managedBodies.get(id);

        if (body == null) {
            // If the body is not in memory, we cannot easily modify the chunk blob without
            // incurring a heavy I/O cost (loading, deserializing, filtering, saving).
            // Therefore, we only ensure that runtime constraints linking to this ID are severed.
            world.getConstraintManager().removeConstraintsForBody(id, reason == VxRemovalReason.DISCARD);
            return;
        }

        processBodyRemoval(body, reason);
    }

    /**
     * Internal routine to dismantle a physics body and release its resources.
     * <p>
     * This method handles:
     * <ul>
     *     <li>Unregistering from the manager's maps.</li>
     *     <li>Notifying listeners (Mounting, Network).</li>
     *     <li>Stopping chunk tracking.</li>
     *     <li>Cleaning up dependent constraints.</li>
     *     <li>Destroying the native Jolt physics body.</li>
     *     <li>Recycling the network ID and cleaning up the DataStore (SoA).</li>
     * </ul>
     *
     * @param body   The body instance to remove.
     * @param reason The context explaining why the removal is occurring.
     */
    private void processBodyRemoval(VxBody body, VxRemovalReason reason) {
        // 1. Remove from primary registry
        managedBodies.remove(body.getPhysicsId());

        // 2. Notify subsystems
        mountingManager.onBodyRemoved(body);
        networkDispatcher.onBodyRemoved(body);

        // 3. Update spatial tracking
        // If UNLOAD, the chunk system typically initiates this call, so we skip
        // modifying the tracking map to avoid concurrent modification issues during iteration.
        int index = body.getDataStoreIndex();
        if (reason != VxRemovalReason.UNLOAD && index != -1) {
            spatialManager.remove(dataStore.chunkKey[index], body);
        }

        // 4. Trigger body-specific cleanup hooks
        body.onBodyRemoved(world, reason);

        // 5. Cleanup Constraints
        // If we are discarding the body, we also want to permanently delete constraints attached to it.
        world.getConstraintManager().removeConstraintsForBody(body.getPhysicsId(), reason == VxRemovalReason.DISCARD);

        // 6. Destroy Native Jolt Body
        // This stops the actual physics simulation for this object.
        VxJoltBridge.INSTANCE.destroyJoltBody(world, body.getBodyId());

        // 7. Cleanup DataStore and ID Pools
        int netId = body.getNetworkId();
        if (netId != -1) {
            dataStore.unregisterNetworkId(netId);
            freeNetworkIds.add(netId);
        }

        dataStore.removeBody(body.getPhysicsId());

        if (body.getBodyId() != 0) {
            joltBodyIdToVxBodyMap.remove(body.getBodyId());
        }

        // 8. Notify Lifecycle Listeners
        notifyBodyRemoved(body);

        // Invalidate indices in the body object to prevent accidental reuse
        body.setDataStoreIndex(dataStore, -1);
        body.setNetworkId(-1);
    }

    /**
     * Registers a body with the internal maps and allocates a slot in the {@link VxServerBodyDataStore}.
     * <p>
     * This method populates the direct reference array in the data store via the add method
     * to enable O(1) lookups during the physics simulation loop.
     *
     * @param body The body to register.
     */
    private void addInternal(VxBody body) {
        if (body == null) return;
        managedBodies.computeIfAbsent(body.getPhysicsId(), id -> {
            EBodyType type = body instanceof VxSoftBody ? EBodyType.SoftBody : EBodyType.RigidBody;

            // Allocate DataStore slot and automatically set bodies[index] reference
            int index = dataStore.addBody(body, type);
            body.setDataStoreIndex(dataStore, index);

            // Assign Network ID (recycle if available, else increment)
            int networkId = freeNetworkIds.isEmpty() ? nextNetworkId++ : freeNetworkIds.pop();
            body.setNetworkId(networkId);
            dataStore.networkId[index] = networkId;
            dataStore.registerNetworkId(networkId, id);

            dataStore.isActive[index] = true;

            // Initialize spatial tracking
            long chunkKey = VxSpatialManager.calculateChunkKey(dataStore.posX[index], dataStore.posZ[index]);
            dataStore.chunkKey[index] = chunkKey;
            spatialManager.add(chunkKey, body);

            // Notify Lifecycle Listeners
            notifyBodyAdded(body);

            return body;
        });
    }

    /**
     * Updates the chunk tracking information for a body when it moves across a chunk border.
     * This method ensures the body is correctly listed in the new chunk and removed from the old one,
     * and notifies the network dispatcher of the change.
     *
     * @param body    The body that moved.
     * @param fromKey The long-encoded key of the chunk it moved from.
     * @param toKey   The long-encoded key of the chunk it moved to.
     */
    public void updateBodyTracking(VxBody body, long fromKey, long toKey) {
        int index = body.getDataStoreIndex();
        if (index != -1) {
            dataStore.chunkKey[index] = toKey;
        }

        // Update spatial manager
        spatialManager.move(body, fromKey, toKey);

        // Notify the network dispatcher about the movement for client-side tracking updates.
        networkDispatcher.onBodyMoved(body, new ChunkPos(fromKey), new ChunkPos(toKey));
    }

    /**
     * Efficiently unloads all physics bodies located in a specific chunk from memory.
     * <p>
     * This method removes the bodies from the active simulation. It assumes persistence
     * has been handled by a prior call to {@link #saveBodiesInChunk(ChunkPos)}.
     *
     * @param chunkPos The position of the chunk to unload.
     */
    public void onChunkUnload(ChunkPos chunkPos) {
        List<VxBody> bodiesToUnload = spatialManager.removeAllInChunk(chunkPos.toLong());
        if (bodiesToUnload.isEmpty()) return;

        for (VxBody body : bodiesToUnload) {
            processBodyRemoval(body, VxRemovalReason.UNLOAD);
        }
    }

    //================================================================================
    // Data Access & Utility
    //================================================================================

    /**
     * Retrieves a {@link VxBody} wrapper associated with a native Jolt Body ID.
     *
     * @param bodyId The integer ID assigned by Jolt.
     * @return The wrapper object, or null if not found.
     */
    @Nullable
    public VxBody getByJoltBodyId(int bodyId) {
        return joltBodyIdToVxBodyMap.get(bodyId);
    }

    /**
     * Populates the provided transform object with the current position and rotation of a body.
     *
     * @param dataStoreIndex The index of the body in the {@link VxServerBodyDataStore}.
     * @param out            The transform object to populate.
     */
    public void getTransform(int dataStoreIndex, VxTransform out) {
        if (dataStoreIndex >= 0 && dataStoreIndex < dataStore.getCapacity()) {
            out.getTranslation().set(dataStore.posX[dataStoreIndex], dataStore.posY[dataStoreIndex], dataStore.posZ[dataStoreIndex]);
            out.getRotation().set(dataStore.rotX[dataStoreIndex], dataStore.rotY[dataStoreIndex], dataStore.rotZ[dataStoreIndex], dataStore.rotW[dataStoreIndex]);
        }
    }

    /**
     * Marks the custom data of a body as "dirty," indicating it needs synchronization/saving.
     *
     * @param body The body whose data changed.
     */
    public void markCustomDataDirty(VxBody body) {
        if (body.getDataStoreIndex() != -1) {
            getDataStore().isCustomDataDirty[body.getDataStoreIndex()] = true;
        }
    }

    /**
     * Registers a mapping between a Jolt Body ID and a VxBody.
     * Usually called internally by the {@link VxJoltBridge}.
     *
     * @param bodyId The native Jolt ID.
     * @param body   The Java wrapper.
     */
    public void registerJoltBodyId(int bodyId, VxBody body) {
        joltBodyIdToVxBodyMap.put(bodyId, body);
    }

    /**
     * Retrieves the current vertex positions for a soft body.
     * <p>
     * This method prioritizes the cached data in {@link VxServerBodyDataStore} for performance (avoiding JNI calls).
     * If the cache is empty (e.g., first frame), it falls back to querying Jolt directly.
     *
     * @param body The soft body to retrieve vertices for.
     * @return An array of flattened vertex coordinates (x, y, z), or null if not available.
     */
    public float @Nullable [] retrieveSoftBodyVertices(VxSoftBody body) {
        int index = body.getDataStoreIndex();
        if (index == -1) return null;

        // Try to get cached vertices from the last physics tick
        float[] cached = dataStore.vertexData[index];
        if (cached != null && cached.length > 0) {
            return cached;
        }

        // Fallback: Query Jolt directly via JNI if cached data is missing
        return VxJoltBridge.INSTANCE.retrieveSoftBodyVertices(world, body);
    }

    /**
     * Updates the vertex positions for a soft body in both the DataStore and the Jolt simulation.
     * <p>
     * This is used when the simulation updates soft body deformation or when applying external
     * deformation logic.
     *
     * @param body     The soft body to update.
     * @param vertices The new flattened vertex coordinates (x, y, z).
     */
    public void updateSoftBodyVertices(VxSoftBody body, float[] vertices) {
        int index = body.getDataStoreIndex();
        if (index == -1) return;

        // Update cache and mark dirty for sync
        dataStore.vertexData[index] = vertices;
        dataStore.isVertexDataDirty[index] = true;

        // Apply immediately to Jolt
        VxJoltBridge.INSTANCE.setSoftBodyVertices(world, body, vertices);
    }

    /**
     * Serializes all physics bodies within a given chunk and queues them for storage.
     * Only bodies marked as persistent via {@link VxBody#isPersistent()} are included.
     * Uses the optimized chunk-based batching system.
     *
     * @param pos The position of the chunk to save.
     */
    public void saveBodiesInChunk(ChunkPos pos) {
        List<VxBody> bodiesInChunk = new ArrayList<>();

        spatialManager.forEachInChunk(pos.toLong(), body -> {
            // Only add the body to the save list if it is marked as persistent.
            if (body.isPersistent()) {
                bodiesInChunk.add(body);
            }
        });

        // Even if empty, we call save to ensure any previously existing data on disk is cleared
        bodyStorage.saveChunk(pos, bodiesInChunk);
    }

    /**
     * Forces pending persistence tasks to write to disk.
     *
     * @param block If true, blocks until the I/O processor completes all pending writes.
     */
    public void flushPersistence(boolean block) {
        bodyStorage.flush(block);
    }

    // Getters for subsystems

    public VxPhysicsWorld getPhysicsWorld() {
        return world;
    }

    public VxServerBodyDataStore getDataStore() {
        return dataStore;
    }

    public VxBodyStorage getBodyStorage() {
        return bodyStorage;
    }

    public VxNetworkDispatcher getNetworkDispatcher() {
        return networkDispatcher;
    }

    public VxSpatialManager getSpatialManager() {
        return spatialManager;
    }

    public VxServerSyncManager getServerSyncManager() {
        return serverSyncManager;
    }

    public VxMountingManager getMountingManager() {
        return mountingManager;
    }
}