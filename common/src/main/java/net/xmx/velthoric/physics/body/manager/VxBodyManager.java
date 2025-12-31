/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.manager;

import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.body.VxJoltBridge;
import net.xmx.velthoric.physics.body.VxRemovalReason;
import net.xmx.velthoric.physics.body.network.internal.VxNetworkDispatcher;
import net.xmx.velthoric.physics.body.persistence.VxBodyStorage;
import net.xmx.velthoric.physics.body.persistence.VxSerializedBodyData;
import net.xmx.velthoric.physics.body.registry.VxBodyRegistry;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.network.synchronization.manager.VxServerSyncManager;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.body.type.VxRigidBody;
import net.xmx.velthoric.physics.body.type.VxSoftBody;
import net.xmx.velthoric.physics.persistence.VxAbstractRegionStorage;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages the lifecycle, state, and persistence of all physics bodies within a specific {@link VxPhysicsWorld}.
 * <p>
 * This class serves as the central orchestration point for the physics simulation. Its responsibilities include:
 * <ul>
 *     <li><b>Lifecycle Management:</b> Creating, activating, deactivating, and destroying physics bodies.</li>
 *     <li><b>Data Storage:</b> Managing the {@link VxServerBodyDataStore}, which uses a Structure-of-Arrays (SoA) layout for CPU-cache-efficient access to physics data.</li>
 *     <li><b>Jolt Integration:</b> Bridging the gap between high-level Java objects and the native Jolt Physics engine via {@link VxJoltBridge}.</li>
 *     <li><b>Spatial Partitioning:</b> Delegating chunk-based tracking to the {@link VxChunkManager}.</li>
 *     <li><b>Persistence:</b> coordinating with {@link VxBodyStorage} to save and load body states to disk.</li>
 *     <li><b>Networking:</b> Handling synchronization of physics states to clients via {@link VxNetworkDispatcher}.</li>
 * </ul>
 *
 * @author xI-Mx-Ix
 */
public class VxBodyManager {

    private final VxPhysicsWorld world;
    private final VxBodyStorage bodyStorage;
    private final VxServerBodyDataStore dataStore;
    private final VxPhysicsUpdater physicsUpdater;
    private final VxNetworkDispatcher networkDispatcher;
    private final VxServerSyncManager serverSyncManager;
    private final VxChunkManager chunkManager;

    /**
     * Primary registry of active bodies, mapped by their persistent unique identifier (UUID).
     * This map is thread-safe to allow concurrent access during physics steps and game ticks.
     */
    private final Map<UUID, VxBody> managedBodies = new ConcurrentHashMap<>();

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
    public VxBodyManager(VxPhysicsWorld world) {
        this.world = world;
        this.dataStore = new VxServerBodyDataStore();
        this.bodyStorage = new VxBodyStorage(world.getLevel(), this);
        this.physicsUpdater = new VxPhysicsUpdater(this);
        this.networkDispatcher = new VxNetworkDispatcher(world.getLevel(), this);
        this.serverSyncManager = new VxServerSyncManager(this);
        this.chunkManager = new VxChunkManager(this);
    }

    /**
     * Initializes the storage and networking subsystems.
     * Should be called after the world is fully constructed but before simulation starts.
     */
    public void initialize() {
        bodyStorage.initialize();
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
        managedBodies.clear();
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
        physicsUpdater.onPhysicsTick(world);
    }

    /**
     * Called during the main game thread tick. Handles synchronization logic that requires
     * interaction with the Minecraft server level.
     *
     * @param level The Minecraft server level.
     */
    public void onGameTick(ServerLevel level) {
        networkDispatcher.onGameTick();
        physicsUpdater.onGameTick(level);
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
     * triggers network dispatch, and performs the native Jolt body creation.
     *
     * @param body       The body instance to add.
     * @param activation The initial activation state for the Jolt simulation.
     * @param transform  The world-space transform for the body.
     */
    public void addConstructedBody(VxBody body, EActivation activation, VxTransform transform) {
        addInternal(body);
        int index = body.getDataStoreIndex();

        // Initialize DataStore values from the transform
        if (index != -1) {
            dataStore.posX[index] = transform.getTranslation().x();
            dataStore.posY[index] = transform.getTranslation().y();
            dataStore.posZ[index] = transform.getTranslation().z();
            dataStore.rotX[index] = transform.getRotation().getX();
            dataStore.rotY[index] = transform.getRotation().getY();
            dataStore.rotZ[index] = transform.getRotation().getZ();
            dataStore.rotW[index] = transform.getRotation().getW();
        }

        // Retrieve initial velocity potentially set by the configurator via the DataStore
        Vec3 linearVelocity = new Vec3(dataStore.velX[index], dataStore.velY[index], dataStore.velZ[index]);
        Vec3 angularVelocity = new Vec3(dataStore.angVelX[index], dataStore.angVelY[index], dataStore.angVelZ[index]);

        world.getMountingManager().onBodyAdded(body);
        networkDispatcher.onBodyAdded(body);

        // Bridge to Native Jolt
        if (body instanceof VxRigidBody rigidBody) {
            VxJoltBridge.INSTANCE.createAndAddJoltRigidBody(rigidBody, this, linearVelocity, angularVelocity, activation, EMotionType.Dynamic);
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

        world.getMountingManager().onBodyAdded(body);
        networkDispatcher.onBodyAdded(body);

        if (body instanceof VxRigidBody rigidBody) {
            VxJoltBridge.INSTANCE.createAndAddJoltRigidBody(rigidBody, this, linearVelocity, angularVelocity, activation, motionType);
        } else if (body instanceof VxSoftBody softBody) {
            VxJoltBridge.INSTANCE.createAndAddJoltSoftBody(softBody, this, linearVelocity, angularVelocity, activation);
        }
        return body;
    }

    /**
     * Initiates the removal of a body identified by its UUID.
     *
     * @param id     The UUID of the body to remove.
     * @param reason The reason for removal, affecting persistence behavior (Save, Discard, Unload).
     */
    public void removeBody(UUID id, VxRemovalReason reason) {
        final VxBody body = this.managedBodies.get(id);

        if (body == null) {
            // Even if the body object isn't loaded, ensure residual data is cleaned up if requested
            if (reason == VxRemovalReason.DISCARD) {
                bodyStorage.removeData(id);
            }
            world.getConstraintManager().removeConstraintsForBody(id, reason == VxRemovalReason.DISCARD);
            return;
        }

        processBodyRemoval(body, reason);
    }

    /**
     * Internal logic for removing a body, handling Jolt destruction, cleanup, and persistence.
     *
     * @param body   The body object to remove.
     * @param reason The context for the removal.
     */
    private void processBodyRemoval(VxBody body, VxRemovalReason reason) {
        managedBodies.remove(body.getPhysicsId());

        // Handle persistence
        if (reason == VxRemovalReason.SAVE) {
            bodyStorage.storeBody(body);
        } else if (reason == VxRemovalReason.DISCARD) {
            bodyStorage.removeData(body.getPhysicsId());
        }

        world.getMountingManager().onBodyRemoved(body);
        networkDispatcher.onBodyRemoved(body);

        // If explicitly unloading, we assume the chunk system handles the tracking logic elsewhere
        if (reason != VxRemovalReason.UNLOAD) {
            chunkManager.stopTracking(body);
        }

        body.onBodyRemoved(world, reason);
        world.getConstraintManager().removeConstraintsForBody(body.getPhysicsId(), reason == VxRemovalReason.DISCARD);

        // Remove from Native Jolt Simulation
        VxJoltBridge.INSTANCE.destroyJoltBody(world, body.getBodyId());

        // Recycle the network ID
        int netId = body.getNetworkId();
        if (netId != -1) {
            dataStore.unregisterNetworkId(netId);
            freeNetworkIds.add(netId);
        }

        // Clean up maps and DataStore
        dataStore.removeBody(body.getPhysicsId());
        if (body.getBodyId() != 0) {
            joltBodyIdToVxBodyMap.remove(body.getBodyId());
        }
        body.setDataStoreIndex(dataStore,-1);
        body.setNetworkId(-1);
    }

    /**
     * Registers a body with the internal maps and allocates a slot in the {@link VxServerBodyDataStore}.
     * This is the common initialization step for both fresh creations and serialized loads.
     *
     * @param body The body to register.
     */
    private void addInternal(VxBody body) {
        if (body == null) return;
        managedBodies.computeIfAbsent(body.getPhysicsId(), id -> {
            EBodyType type = body instanceof VxSoftBody ? EBodyType.SoftBody : EBodyType.RigidBody;

            // Allocate DataStore slot
            int index = dataStore.addBody(id, type);
            body.setDataStoreIndex(dataStore, index);

            // Assign Network ID (recycle if available, else increment)
            int networkId = freeNetworkIds.isEmpty() ? nextNetworkId++ : freeNetworkIds.pop();
            body.setNetworkId(networkId);
            dataStore.networkId[index] = networkId;
            dataStore.registerNetworkId(networkId, id);

            dataStore.isActive[index] = true;
            chunkManager.startTracking(body);
            return body;
        });
    }

    /**
     * Efficiently unloads all physics bodies located in a specific chunk.
     * <p>
     * This method is intended to be called when a Minecraft chunk is unloaded. It removes
     * the bodies from memory to free resources but does <b>not</b> trigger a save operation,
     * assuming the chunk data has already been serialized via {@link #saveBodiesInChunk}.
     *
     * @param chunkPos The position of the chunk to unload.
     */
    public void onChunkUnload(ChunkPos chunkPos) {
        // Step 1: Atomically retrieve and untrack bodies. This avoids concurrent modification issues.
        List<VxBody> bodiesToUnload = chunkManager.removeAllInChunk(chunkPos);

        if (bodiesToUnload.isEmpty()) {
            return;
        }

        // Step 2: Perform memory cleanup with the UNLOAD reason (skips disk I/O).
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
     * Retrieves a managed body by its persistent UUID.
     *
     * @param id The unique identifier.
     * @return The body, or null if it is not currently loaded.
     */
    @Nullable
    public VxBody getVxBody(UUID id) {
        return managedBodies.get(id);
    }

    /**
     * Returns a collection of all currently active bodies.
     * Note: The collection is backed by the concurrent map, so iteration is safe but weakly consistent.
     *
     * @return A collection of active bodies.
     */
    public Collection<VxBody> getAllBodies() {
        return managedBodies.values();
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
     * Calculates the ChunkPos for a body based on its current position in the DataStore.
     *
     * @param dataStoreIndex The index of the body.
     * @return The chunk position containing the body's center.
     */
    public ChunkPos getBodyChunkPos(int dataStoreIndex) {
        if (dataStoreIndex >= 0 && dataStoreIndex < dataStore.getCapacity()) {
            return new ChunkPos(
                    SectionPos.posToSectionCoord(dataStore.posX[dataStoreIndex]),
                    SectionPos.posToSectionCoord(dataStore.posZ[dataStoreIndex])
            );
        }
        return new ChunkPos(0, 0); // Fallback to 0,0 if index invalid
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
     * <p>
     * This operation is split into two phases:
     * 1. <b>Snapshot (Main/Physics Thread):</b> Captures the state of all bodies in the chunk to a byte buffer.
     * 2. <b>Write (Async):</b> Passes the buffer to {@link VxBodyStorage} to write to disk.
     *
     * @param pos The position of the chunk to save.
     */
    public void saveBodiesInChunk(ChunkPos pos) {
        List<VxBody> bodiesInChunk = new ArrayList<>();
        chunkManager.forEachBodyInChunk(pos, bodiesInChunk::add);

        if (bodiesInChunk.isEmpty()) {
            return;
        }

        // Schedule the snapshot creation on the physics thread to ensure thread safety
        world.execute(() -> {
            Map<VxAbstractRegionStorage.RegionPos, Map<UUID, byte[]>> dataByRegion = new HashMap<>();

            for (VxBody body : bodiesInChunk) {
                int index = body.getDataStoreIndex();
                if (index == -1) continue;

                // Serialize the body state
                byte[] snapshot = bodyStorage.serializeBodyData(body);
                if (snapshot == null) continue;

                // Group data by region (32x32 chunks) for efficient batch I/O
                ChunkPos chunkPos = getBodyChunkPos(index);
                VxAbstractRegionStorage.RegionPos regionPos = new VxAbstractRegionStorage.RegionPos(chunkPos.x >> 5, chunkPos.z >> 5);
                dataByRegion.computeIfAbsent(regionPos, k -> new HashMap<>()).put(body.getPhysicsId(), snapshot);
            }

            // Hand off to the storage system
            if (!dataByRegion.isEmpty()) {
                bodyStorage.storeBodyBatch(dataByRegion);
            }
        });
    }

    /**
     * Forces pending persistence tasks to write to disk.
     *
     * @param block If true, the current thread will wait until all data is flushed.
     */
    public void flushPersistence(boolean block) {
        try {
            CompletableFuture<Void> future = bodyStorage.saveDirtyRegions();
            bodyStorage.getRegionIndex().save();
            if (block) {
                future.join();
            }
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to flush physics body persistence for world {}", world.getLevel().dimension().location(), e);
        }
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

    public VxChunkManager getChunkManager() {
        return chunkManager;
    }

    public VxServerSyncManager getServerSyncManager() {
        return serverSyncManager;
    }
}