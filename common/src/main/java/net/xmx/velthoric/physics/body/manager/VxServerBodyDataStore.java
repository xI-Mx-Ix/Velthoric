/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.manager;

import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.xmx.velthoric.physics.body.VxBodyDataStore;
import net.xmx.velthoric.physics.body.type.VxBody;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The server-side implementation of the body data store.
 * <p>
 * In this implementation, the shared arrays inherited from {@link VxBodyDataStore} (posX, rotX, etc.)
 * represent the <b>Live Simulation State</b> driven by the physics engine.
 * <p>
 * It adds server-specific fields for:
 * <ul>
 *     <li>Network synchronization (dirty flags, network ID mapping).</li>
 *     <li>Spatial partitioning (chunk keys).</li>
 *     <li>Physics properties not needed for rendering (AABB, angular velocity).</li>
 * </ul>
 * This class is thread-safe.
 *
 * @author xI-Mx-Ix
 */
public class VxServerBodyDataStore extends VxBodyDataStore {

    // --- Server-Specific Physics Data ---

    /**
     * Angular Velocity X component.
     */
    public float[] angVelX;

    /**
     * Angular Velocity Y component.
     */
    public float[] angVelY;

    /**
     * Angular Velocity Z component.
     */
    public float[] angVelZ;

    /**
     * AABB Minimum X coordinate.
     */
    public float[] aabbMinX;

    /**
     * AABB Minimum Y coordinate.
     */
    public float[] aabbMinY;

    /**
     * AABB Minimum Z coordinate.
     */
    public float[] aabbMinZ;

    /**
     * AABB Maximum X coordinate.
     */
    public float[] aabbMaxX;

    /**
     * AABB Maximum Y coordinate.
     */
    public float[] aabbMaxY;

    /**
     * AABB Maximum Z coordinate.
     */
    public float[] aabbMaxZ;

    /**
     * The Jolt Body Type (Rigid vs Soft).
     */
    public EBodyType[] bodyType;

    /**
     * The Motion Type (Static, Kinematic, Dynamic).
     */
    public EMotionType[] motionType;

    // --- Server Logic & Networking ---

    /**
     * The packed chunk key (ChunkPos long) identifying the chunk this body resides in.
     */
    public long[] chunkKey;

    /**
     * The session-unique integer Network ID used for packet optimization.
     */
    public int[] networkId;

    /**
     * Reverse lookup map for network synchronization (Network ID -> Body UUID).
     */
    private final Int2ObjectMap<UUID> networkIdToUuid = new Int2ObjectOpenHashMap<>();

    // --- Dirty Flags for Synchronization ---

    /**
     * Flag indicating that the game logic has modified this body's state, requiring a sync TO Jolt.
     */
    public boolean[] isGameStateDirty;

    /**
     * Flag indicating that the body's transform (pos/rot/vel) has changed, requiring a network sync TO Clients.
     */
    public boolean[] isTransformDirty;

    /**
     * Flag indicating that the body's vertex data (for soft bodies) has changed, requiring a network sync TO Clients.
     */
    public boolean[] isVertexDataDirty;

    /**
     * Flag indicating that the body's custom data has changed, requiring a network sync TO Clients.
     */
    public boolean[] isCustomDataDirty;

    /**
     * The server timestamp of the last physics update for this body.
     */
    public long[] lastUpdateTimestamp;

    /**
     * Constructs the server data store.
     */
    public VxServerBodyDataStore() {
        super();
        allocate(INITIAL_CAPACITY);
    }

    /**
     * Reserves a new index for a physics body and sets its type.
     * <p>
     * This method delegates to the base {@link #reserveIndex(VxBody)} to ensure the
     * internal object reference array is correctly populated.
     *
     * @param body The body object to add.
     * @param type The EBodyType of the body.
     * @return The data store index for the new body.
     */
    public synchronized int addBody(VxBody body, EBodyType type) {
        // Use base class logic to reserve slot and set body reference
        int index = super.reserveIndex(body);

        // Initialize server-specific data
        bodyType[index] = type;
        chunkKey[index] = Long.MAX_VALUE; // Sentinel for "no chunk"
        return index;
    }

    /**
     * Releases the index for a given body UUID, making it available for reuse.
     *
     * @param id The UUID of the body to remove.
     * @return The released index, or null if the body was not found.
     */
    @Override
    @Nullable
    public synchronized Integer removeBody(UUID id) {
        return super.removeBody(id);
    }

    /**
     * Resets all data at a specific index to default values.
     *
     * @param index The index to reset.
     */
    @Override
    protected void resetIndex(int index) {
        super.resetIndex(index);

        angVelX[index] = angVelY[index] = angVelZ[index] = 0f;
        aabbMinX[index] = aabbMinY[index] = aabbMinZ[index] = 0f;
        aabbMaxX[index] = aabbMaxY[index] = aabbMaxZ[index] = 0f;

        bodyType[index] = null;
        motionType[index] = null;
        chunkKey[index] = Long.MAX_VALUE;

        // Handle Network ID cleanup
        int netId = networkId[index];
        if (netId != -1) {
            unregisterNetworkId(netId);
        }
        networkId[index] = -1;

        isGameStateDirty[index] = false;
        isTransformDirty[index] = false;
        isVertexDataDirty[index] = false;
        isCustomDataDirty[index] = false;
        lastUpdateTimestamp[index] = 0L;
    }

    /**
     * Reallocates all data arrays to a new capacity.
     *
     * @param newCapacity The new size for the arrays.
     */
    @Override
    protected void allocate(int newCapacity) {
        super.growBaseArrays(newCapacity);

        angVelX = grow(angVelX, newCapacity);
        angVelY = grow(angVelY, newCapacity);
        angVelZ = grow(angVelZ, newCapacity);

        aabbMinX = grow(aabbMinX, newCapacity);
        aabbMinY = grow(aabbMinY, newCapacity);
        aabbMinZ = grow(aabbMinZ, newCapacity);
        aabbMaxX = grow(aabbMaxX, newCapacity);
        aabbMaxY = grow(aabbMaxY, newCapacity);
        aabbMaxZ = grow(aabbMaxZ, newCapacity);

        bodyType = grow(bodyType, newCapacity);
        motionType = grow(motionType, newCapacity);
        chunkKey = grow(chunkKey, newCapacity);
        networkId = grow(networkId, newCapacity);

        isGameStateDirty = grow(isGameStateDirty, newCapacity);
        isTransformDirty = grow(isTransformDirty, newCapacity);
        isVertexDataDirty = grow(isVertexDataDirty, newCapacity);
        isCustomDataDirty = grow(isCustomDataDirty, newCapacity);
        lastUpdateTimestamp = grow(lastUpdateTimestamp, newCapacity);
    }

    // --- Network ID Management ---

    /**
     * Registers a network ID mapping for the given body UUID.
     *
     * @param networkId The session-unique network ID.
     * @param id        The persistent body UUID.
     */
    public synchronized void registerNetworkId(int networkId, UUID id) {
        this.networkIdToUuid.put(networkId, id);
    }

    /**
     * Unregisters a network ID mapping.
     *
     * @param networkId The network ID to remove.
     */
    public synchronized void unregisterNetworkId(int networkId) {
        this.networkIdToUuid.remove(networkId);
    }

    /**
     * Retrieves the body UUID associated with a network ID.
     *
     * @param networkId The network ID to look up.
     * @return The body UUID, or null if not found.
     */
    @Nullable
    public synchronized UUID getIdForNetworkId(int networkId) {
        return this.networkIdToUuid.get(networkId);
    }

    /**
     * Clears all data and resets the store to its initial state.
     */
    @Override
    public synchronized void clear() {
        networkIdToUuid.clear();
        super.clear();
    }
}