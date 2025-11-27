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
import net.xmx.velthoric.physics.body.manager.chunk.VxChunkManager;
import net.xmx.velthoric.physics.body.persistence.VxBodyStorage;
import net.xmx.velthoric.physics.body.persistence.VxSerializedBodyData;
import net.xmx.velthoric.physics.body.registry.VxBodyRegistry;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
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
 * Manages the lifecycle and state of all physics bodies within a {@link VxPhysicsWorld}.
 * This class acts as the central hub for creating, removing, and accessing physics bodies,
 * delegating spatial partitioning to a {@link VxChunkManager} and low-level Jolt
 * interactions to the {@link VxJoltBridge}.
 *
 * @author xI-Mx-Ix
 */
public class VxBodyManager {

    private final VxPhysicsWorld world;
    private final VxBodyStorage bodyStorage;
    private final VxBodyDataStore dataStore;
    private final VxPhysicsUpdater physicsUpdater;
    private final VxNetworkDispatcher networkDispatcher;
    private final VxChunkManager chunkManager;

    /**
     * Main map for tracking all active body instances by their unique persistent ID.
     */
    private final Map<UUID, VxBody> managedBodies = new ConcurrentHashMap<>();

    /**
     * A fast lookup map from the Jolt physics body ID to the corresponding VxBody wrapper.
     */
    private final Int2ObjectMap<VxBody> joltBodyIdToVxBodyMap = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

    /**
     * A pool of recycled network IDs from removed bodies.
     */
    private final Deque<Integer> freeNetworkIds = new ArrayDeque<>();

    /**
     * The next network ID to be assigned.
     */
    private int nextNetworkId = 1;

    public VxBodyManager(VxPhysicsWorld world) {
        this.world = world;
        this.dataStore = new VxBodyDataStore();
        this.bodyStorage = new VxBodyStorage(world.getLevel(), this);
        this.physicsUpdater = new VxPhysicsUpdater(this);
        this.networkDispatcher = new VxNetworkDispatcher(world.getLevel(), this);
        this.chunkManager = new VxChunkManager(this);
    }

    public void initialize() {
        bodyStorage.initialize();
        networkDispatcher.start();
    }

    public void shutdown() {
        networkDispatcher.stop();
        VxMainClass.LOGGER.debug("Flushing physics body persistence for world {}...", world.getDimensionKey().location());
        flushPersistence(true);
        VxMainClass.LOGGER.debug("Physics body persistence flushed for world {}.", world.getDimensionKey().location());
        clear();
        bodyStorage.shutdown();
    }

    private void clear() {
        managedBodies.clear();
        joltBodyIdToVxBodyMap.clear();
        freeNetworkIds.clear();
        nextNetworkId = 1;
        dataStore.clear();
    }

    public void onPhysicsTick(VxPhysicsWorld world) {
        physicsUpdater.onPhysicsTick(world);
    }

    public void onGameTick(ServerLevel level) {
        networkDispatcher.onGameTick();
        physicsUpdater.onGameTick(level);
    }

    //================================================================================
    // Public API: Body Creation
    //================================================================================

    @Nullable
    public <T extends VxRigidBody> T createRigidBody(VxBodyType<T> type, VxTransform transform, Consumer<T> configurator) {
        return createRigidBody(type, transform, EActivation.DontActivate, configurator);
    }

    @Nullable
    public <T extends VxRigidBody> T createRigidBody(VxBodyType<T> type, VxTransform transform, EActivation activation, Consumer<T> configurator) {
        T body = type.create(world, UUID.randomUUID());
        if (body == null) return null;
        configurator.accept(body);
        addConstructedBody(body, activation, transform);
        return body;
    }

    @Nullable
    public <T extends VxSoftBody> T createSoftBody(VxBodyType<T> type, VxTransform transform, Consumer<T> configurator) {
        return createSoftBody(type, transform, EActivation.DontActivate, configurator);
    }

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

        world.getMountingManager().onBodyAdded(body);
        networkDispatcher.onBodyAdded(body);

        if (body instanceof VxRigidBody rigidBody) {
            VxJoltBridge.INSTANCE.createAndAddJoltRigidBody(rigidBody, this, null, null, activation, EMotionType.Dynamic);
        } else if (body instanceof VxSoftBody softBody) {
            VxJoltBridge.INSTANCE.createAndAddJoltSoftBody(softBody, this, activation);
        }
    }

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

        // Read all internal persistence data (transform, velocity, vertices, user data)
        // directly from the buffer into the Body and the DataStore.
        // This MUST happen after addInternal so the body has a valid DataStore index.
        body.readInternalPersistenceData(data.bodyData());
        data.bodyData().release();

        int index = body.getDataStoreIndex();
        if (index == -1) {
            return null; // Should not happen
        }

        // Retrieve the restored values from the DataStore to configure Jolt
        Vec3 linearVelocity = new Vec3(dataStore.velX[index], dataStore.velY[index], dataStore.velZ[index]);
        Vec3 angularVelocity = new Vec3(dataStore.angVelX[index], dataStore.angVelY[index], dataStore.angVelZ[index]);
        EMotionType motionType = dataStore.motionType[index];

        // Determine activation based on velocity
        boolean shouldActivate = linearVelocity.lengthSq() > 0.0001f || angularVelocity.lengthSq() > 0.0001f;
        EActivation activation = shouldActivate ? EActivation.Activate : EActivation.DontActivate;

        world.getMountingManager().onBodyAdded(body);
        networkDispatcher.onBodyAdded(body);

        if (body instanceof VxRigidBody rigidBody) {
            VxJoltBridge.INSTANCE.createAndAddJoltRigidBody(rigidBody, this, linearVelocity, angularVelocity, activation, motionType);
        } else if (body instanceof VxSoftBody softBody) {
            VxJoltBridge.INSTANCE.createAndAddJoltSoftBody(softBody, this, activation);
        }
        return body;
    }

    public void removeBody(UUID id, VxRemovalReason reason) {
        final VxBody body = this.managedBodies.get(id);

        if (body == null) {
            if (reason == VxRemovalReason.DISCARD) {
                bodyStorage.removeData(id);
            }
            world.getConstraintManager().removeConstraintsForBody(id, reason == VxRemovalReason.DISCARD);
            return;
        }

        processBodyRemoval(body, reason);
    }

    private void processBodyRemoval(VxBody body, VxRemovalReason reason) {
        managedBodies.remove(body.getPhysicsId());

        if (reason == VxRemovalReason.SAVE) {
            bodyStorage.storeBody(body);
        } else if (reason == VxRemovalReason.DISCARD) {
            bodyStorage.removeData(body.getPhysicsId());
        }

        world.getMountingManager().onBodyRemoved(body);
        networkDispatcher.onBodyRemoved(body);

        if (reason != VxRemovalReason.UNLOAD) {
            chunkManager.stopTracking(body);
        }

        body.onBodyRemoved(world, reason);
        world.getConstraintManager().removeConstraintsForBody(body.getPhysicsId(), reason == VxRemovalReason.DISCARD);
        VxJoltBridge.INSTANCE.destroyJoltBody(world, body.getBodyId());

        if (body.getNetworkId() != -1) {
            freeNetworkIds.add(body.getNetworkId());
        }

        dataStore.removeBody(body.getPhysicsId());
        if (body.getBodyId() != 0) {
            joltBodyIdToVxBodyMap.remove(body.getBodyId());
        }
        body.setDataStoreIndex(-1);
        body.setNetworkId(-1);
    }

    private void addInternal(VxBody body) {
        if (body == null) return;
        managedBodies.computeIfAbsent(body.getPhysicsId(), id -> {
            EBodyType type = body instanceof VxSoftBody ? EBodyType.SoftBody : EBodyType.RigidBody;
            int index = dataStore.addBody(id, type);
            body.setDataStoreIndex(index);

            // Assign Network ID
            int networkId = freeNetworkIds.isEmpty() ? nextNetworkId++ : freeNetworkIds.pop();
            body.setNetworkId(networkId);
            dataStore.networkId[index] = networkId;

            dataStore.isActive[index] = true;
            chunkManager.startTracking(body);
            return body;
        });
    }

    /**
     * Efficiently unloads all physics bodies from a chunk from memory.
     * This is called when a chunk is being unloaded and relies on a separate process
     * to have already saved the chunk data to disk.
     *
     * @param chunkPos The position of the chunk to unload.
     */
    public void onChunkUnload(ChunkPos chunkPos) {
        // Step 1: Atomically get all bodies from the chunk and remove them from the tracking map. This is fast.
        List<VxBody> bodiesToUnload = chunkManager.removeAllInChunk(chunkPos);

        if (bodiesToUnload.isEmpty()) {
            return;
        }

        // Step 2: Loop through the bodies and perform only the in-memory cleanup.
        // We use VxRemovalReason.UNLOAD, which skips any further persistence calls.
        for (VxBody body : bodiesToUnload) {
            processBodyRemoval(body, VxRemovalReason.UNLOAD);
        }
    }

    @Nullable
    public VxBody getByJoltBodyId(int bodyId) {
        return joltBodyIdToVxBodyMap.get(bodyId);
    }

    @Nullable
    public VxBody getVxBody(UUID id) {
        return managedBodies.get(id);
    }

    public Collection<VxBody> getAllBodies() {
        return managedBodies.values();
    }

    public void getTransform(int dataStoreIndex, VxTransform out) {
        if (dataStoreIndex >= 0 && dataStoreIndex < dataStore.getCapacity()) {
            out.getTranslation().set(dataStore.posX[dataStoreIndex], dataStore.posY[dataStoreIndex], dataStore.posZ[dataStoreIndex]);
            out.getRotation().set(dataStore.rotX[dataStoreIndex], dataStore.rotY[dataStoreIndex], dataStore.rotZ[dataStoreIndex], dataStore.rotW[dataStoreIndex]);
        }
    }

    public ChunkPos getBodyChunkPos(int dataStoreIndex) {
        if (dataStoreIndex >= 0 && dataStoreIndex < dataStore.getCapacity()) {
            return new ChunkPos(
                    SectionPos.posToSectionCoord(dataStore.posX[dataStoreIndex]),
                    SectionPos.posToSectionCoord(dataStore.posZ[dataStoreIndex])
            );
        }
        return new ChunkPos(0, 0); // Fallback
    }

    public void markCustomDataDirty(VxBody body) {
        if (body.getDataStoreIndex() != -1) {
            getDataStore().isCustomDataDirty[body.getDataStoreIndex()] = true;
        }
    }

    public void registerJoltBodyId(int bodyId, VxBody body) {
        joltBodyIdToVxBodyMap.put(bodyId, body);
    }

    /**
     * Retrieves the current vertex positions for a soft body.
     * This method prioritizes the cached data store for performance but will
     * query Jolt directly if the cache is empty.
     *
     * @param body The soft body to retrieve vertices for.
     * @return An array of vertex coordinates (x, y, z), or null/empty if not available.
     */
    public float @Nullable [] retrieveSoftBodyVertices(VxSoftBody body) {
        int index = body.getDataStoreIndex();
        if (index == -1) return null;

        // Try to get cached vertices from the last physics tick
        float[] cached = dataStore.vertexData[index];
        if (cached != null && cached.length > 0) {
            return cached;
        }

        // Fallback: Query Jolt directly if cached data is missing (e.g., before first tick)
        return VxJoltBridge.INSTANCE.retrieveSoftBodyVertices(world, body);
    }

    /**
     * Updates the vertex positions for a soft body.
     * This updates the data store and, if the body is currently added to Jolt,
     * updates the physics simulation immediately.
     *
     * @param body     The soft body to update.
     * @param vertices The new vertex coordinates (x, y, z).
     */
    public void updateSoftBodyVertices(VxSoftBody body, float[] vertices) {
        int index = body.getDataStoreIndex();
        if (index == -1) return;

        // Update cache
        dataStore.vertexData[index] = vertices;
        dataStore.isVertexDataDirty[index] = true;

        // Apply to Jolt
        VxJoltBridge.INSTANCE.setSoftBodyVertices(world, body, vertices);
    }

    /**
     * Saves all physics bodies within a given chunk in a single batched operation.
     * It creates a snapshot of all bodies on the physics thread and then passes the
     * serialized data to the storage system for asynchronous writing.
     *
     * @param pos The position of the chunk to save.
     */
    public void saveBodiesInChunk(ChunkPos pos) {
        List<VxBody> bodiesInChunk = new ArrayList<>();
        chunkManager.forEachBodyInChunk(pos, bodiesInChunk::add);

        if (bodiesInChunk.isEmpty()) {
            return;
        }

        // Schedule a single, batched task on the physics thread.
        world.execute(() -> {
            Map<VxAbstractRegionStorage.RegionPos, Map<UUID, byte[]>> dataByRegion = new HashMap<>();

            for (VxBody body : bodiesInChunk) {
                int index = body.getDataStoreIndex();
                if (index == -1) continue;

                // Create the data snapshot for this body.
                byte[] snapshot = bodyStorage.serializeBodyData(body);
                if (snapshot == null) continue;

                // Group the snapshot by its region position for efficient batch writing.
                ChunkPos chunkPos = getBodyChunkPos(index);
                VxAbstractRegionStorage.RegionPos regionPos = new VxAbstractRegionStorage.RegionPos(chunkPos.x >> 5, chunkPos.z >> 5);
                dataByRegion.computeIfAbsent(regionPos, k -> new HashMap<>()).put(body.getPhysicsId(), snapshot);
            }

            // Pass the grouped snapshots to the storage system.
            if (!dataByRegion.isEmpty()) {
                bodyStorage.storeBodyBatch(dataByRegion);
            }
        });
    }

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

    public VxPhysicsWorld getPhysicsWorld() {
        return world;
    }

    public VxBodyDataStore getDataStore() {
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
}