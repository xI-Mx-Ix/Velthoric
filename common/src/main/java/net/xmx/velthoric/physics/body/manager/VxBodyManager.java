/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.manager;

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
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
        flushPersistence();
        VxMainClass.LOGGER.debug("Physics body persistence flushed for world {}.", world.getDimensionKey().location());
        clear();
        bodyStorage.shutdown();
    }

    private void clear() {
        managedBodies.clear();
        joltBodyIdToVxBodyMap.clear();
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
        VxBody body = VxBodyRegistry.getInstance().create(data.typeId(), world, data.id());
        if (body == null) {
            VxMainClass.LOGGER.error("Failed to create body of type {} with ID {} from storage.", data.typeId(), data.id());
            return null;
        }

        addInternal(body);
        int index = body.getDataStoreIndex();

        boolean shouldActivate = data.linearVelocity().lengthSq() > 0.0001f || data.angularVelocity().lengthSq() > 0.0001f;
        EActivation activation = shouldActivate ? EActivation.Activate : EActivation.DontActivate;

        if (index != -1) {
            dataStore.motionType[index] = data.motionType();
            dataStore.posX[index] = data.transform().getTranslation().x();
            dataStore.posY[index] = data.transform().getTranslation().y();
            dataStore.posZ[index] = data.transform().getTranslation().z();
            dataStore.rotX[index] = data.transform().getRotation().getX();
            dataStore.rotY[index] = data.transform().getRotation().getY();
            dataStore.rotZ[index] = data.transform().getRotation().getZ();
            dataStore.rotW[index] = data.transform().getRotation().getW();
        }

        body.readPersistenceData(data.persistenceData());
        data.persistenceData().release();

        world.getMountingManager().onBodyAdded(body);
        networkDispatcher.onBodyAdded(body);

        if (body instanceof VxRigidBody rigidBody) {
            VxJoltBridge.INSTANCE.createAndAddJoltRigidBody(rigidBody, this, data.linearVelocity(), data.angularVelocity(), activation, data.motionType());
        } else if (body instanceof VxSoftBody softBody) {
            VxJoltBridge.INSTANCE.createAndAddJoltSoftBody(softBody, this, activation);
        }
        return body;
    }

    public void removeBody(UUID id, VxRemovalReason reason) {
        final VxBody body = this.managedBodies.remove(id);

        if (body == null) {
            VxMainClass.LOGGER.warn("Attempted to remove non-existent body: {}", id);
            if (reason == VxRemovalReason.DISCARD) {
                bodyStorage.removeData(id);
            }
            world.getConstraintManager().removeConstraintsForBody(id, reason == VxRemovalReason.DISCARD);
            return;
        }

        if (reason == VxRemovalReason.SAVE) {
            bodyStorage.storeBody(body);
        } else if (reason == VxRemovalReason.DISCARD) {
            bodyStorage.removeData(id);
        }

        world.getMountingManager().onBodyRemoved(body);
        networkDispatcher.onBodyRemoved(body);
        chunkManager.stopTracking(body);
        body.onBodyRemoved(world, reason);
        world.getConstraintManager().removeConstraintsForBody(id, reason == VxRemovalReason.DISCARD);

        VxJoltBridge.INSTANCE.destroyJoltBody(world, body.getBodyId());

        dataStore.removeBody(id);
        if (body.getBodyId() != 0) {
            joltBodyIdToVxBodyMap.remove(body.getBodyId());
        }
        body.setDataStoreIndex(-1);
    }

    private void addInternal(VxBody body) {
        if (body == null) return;
        managedBodies.computeIfAbsent(body.getPhysicsId(), id -> {
            EBodyType type = body instanceof VxSoftBody ? EBodyType.SoftBody : EBodyType.RigidBody;
            int index = dataStore.addBody(id, type);
            body.setDataStoreIndex(index);
            dataStore.isActive[index] = true;
            chunkManager.startTracking(body);
            return body;
        });
    }

    public void onChunkUnload(ChunkPos chunkPos) {
        List<VxBody> bodiesInChunk = getBodiesInChunk(chunkPos);
        if (bodiesInChunk.isEmpty()) return;
        for (VxBody body : List.copyOf(bodiesInChunk)) {
            removeBody(body.getPhysicsId(), VxRemovalReason.SAVE);
        }
    }

    //================================================================================
    // Data Accessors & State Modification
    //================================================================================

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

    public List<VxBody> getBodiesInChunk(ChunkPos pos) {
        return chunkManager.getBodiesInChunk(pos);
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

    /**
     *  Internal use only. Registers a mapping from a Jolt body ID to a VxBody instance.
     */
    public void registerJoltBodyId(int bodyId, VxBody body) {
        joltBodyIdToVxBodyMap.put(bodyId, body);
    }

    //================================================================================
    // Persistence
    //================================================================================

    public void saveBodiesInChunk(ChunkPos pos) {
        List<VxBody> bodiesInChunk = getBodiesInChunk(pos);
        if (!bodiesInChunk.isEmpty()) {
            bodyStorage.storeBodies(List.copyOf(bodiesInChunk));
        }
    }

    public void flushPersistence() {
        try {
            bodyStorage.saveDirtyRegions().join();
            bodyStorage.getRegionIndex().save();
        } catch (Exception e) {
            VxMainClass.LOGGER.error("Failed to flush physics body persistence for world {}", world.getLevel().dimension().location(), e);
        }
    }

    //================================================================================
    // Getters for Subsystems
    //================================================================================

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