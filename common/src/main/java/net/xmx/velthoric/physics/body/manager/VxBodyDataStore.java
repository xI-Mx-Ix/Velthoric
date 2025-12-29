/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.manager;

import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.xmx.velthoric.physics.body.AbstractDataStore;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A data-oriented store for the live state of all physics bodies on the server.
 * This class uses a "Structure of Arrays" (SoA) layout, where each property of a body
 * is stored in a separate array. This is highly efficient for the physics update loop,
 * as it improves CPU cache locality when iterating over a single property (e.g., position)
 * for all bodies. This class is thread-safe.
 *
 * @author xI-Mx-Ix
 */
public class VxBodyDataStore extends AbstractDataStore {
    // The initial capacity for the data arrays.
    private static final int INITIAL_CAPACITY = 256;

    private final Object2IntMap<UUID> uuidToIndex = new Object2IntOpenHashMap<>();
    private final ObjectArrayList<UUID> indexToUuid = new ObjectArrayList<>();

    // Reverse lookup map for network synchronization (Network ID -> Body UUID).
    // Synchronized to ensure thread safety between the network thread and game thread.
    private final Int2ObjectMap<UUID> networkIdToUuid = new Int2ObjectOpenHashMap<>();

    private final IntArrayList freeIndices = new IntArrayList();
    private int count = 0;
    private int capacity = 0;

    // --- Physics State Data (SoA) ---
    public double[] posX, posY, posZ;
    public float[] rotX, rotY, rotZ, rotW;
    public float[] velX, velY, velZ;
    public float[] angVelX, angVelY, angVelZ;
    public float[] aabbMinX, aabbMinY, aabbMinZ; // AABB min corner
    public float[] aabbMaxX, aabbMaxY, aabbMaxZ; // AABB max corner
    public float[] @Nullable [] vertexData; // For soft bodies
    public boolean[] isActive;
    public EBodyType[] bodyType;
    public EMotionType[] motionType;
    public long[] chunkKey;
    public int[] networkId;

    // --- Sync & Management Data ---
    /**
     * Flag indicating that the game logic has modified this body's state, requiring a sync to Jolt.
     */
    public boolean[] isGameStateDirty;

    /**
     * Flag indicating that the body's transform (pos/rot/vel) has changed, requiring a network sync.
     */
    public boolean[] isTransformDirty;

    /**
     * Flag indicating that the body's vertex data (for soft bodies) has changed, requiring a network sync.
     */
    public boolean[] isVertexDataDirty;

    /**
     * Flag indicating that the body's custom data has changed, requiring a network sync.
     */
    public boolean[] isCustomDataDirty;

    /**
     * The server timestamp of the last physics update for this body.
     */
    public long[] lastUpdateTimestamp;

    public VxBodyDataStore() {
        allocate(INITIAL_CAPACITY);
        uuidToIndex.defaultReturnValue(-1);
    }

    private void allocate(int newCapacity) {
        posX = grow(posX, newCapacity);
        posY = grow(posY, newCapacity);
        posZ = grow(posZ, newCapacity);
        rotX = grow(rotX, newCapacity);
        rotY = grow(rotY, newCapacity);
        rotZ = grow(rotZ, newCapacity);
        rotW = grow(rotW, newCapacity);
        velX = grow(velX, newCapacity);
        velY = grow(velY, newCapacity);
        velZ = grow(velZ, newCapacity);
        angVelX = grow(angVelX, newCapacity);
        angVelY = grow(angVelY, newCapacity);
        angVelZ = grow(angVelZ, newCapacity);
        aabbMinX = grow(aabbMinX, newCapacity);
        aabbMinY = grow(aabbMinY, newCapacity);
        aabbMinZ = grow(aabbMinZ, newCapacity);
        aabbMaxX = grow(aabbMaxX, newCapacity);
        aabbMaxY = grow(aabbMaxY, newCapacity);
        aabbMaxZ = grow(aabbMaxZ, newCapacity);
        vertexData = grow(vertexData, newCapacity);
        isActive = grow(isActive, newCapacity);
        bodyType = grow(bodyType, newCapacity);
        motionType = grow(motionType, newCapacity);
        chunkKey = grow(chunkKey, newCapacity);
        networkId = grow(networkId, newCapacity);

        isGameStateDirty = grow(isGameStateDirty, newCapacity);
        isTransformDirty = grow(isTransformDirty, newCapacity);
        isVertexDataDirty = grow(isVertexDataDirty, newCapacity);
        isCustomDataDirty = grow(isCustomDataDirty, newCapacity);
        lastUpdateTimestamp = grow(lastUpdateTimestamp, newCapacity);

        this.capacity = newCapacity;
    }

    /**
     * Reserves a new index for a physics body and sets its type.
     *
     * @param id   The UUID of the body.
     * @param type The EBodyType of the body.
     * @return The data store index for the new body.
     */
    public synchronized int addBody(UUID id, EBodyType type) {
        if (uuidToIndex.containsKey(id)) {
            return uuidToIndex.getInt(id);
        }

        if (count == capacity) {
            allocate(capacity * 2);
        }
        int index = freeIndices.isEmpty() ? count++ : freeIndices.removeInt(freeIndices.size() - 1);

        uuidToIndex.put(id, index);
        if (index >= indexToUuid.size()) {
            indexToUuid.add(id);
        } else {
            indexToUuid.set(index, id);
        }

        bodyType[index] = type;
        chunkKey[index] = Long.MAX_VALUE; // Initialize with an invalid key
        return index;
    }

    /**
     * Releases the index for a given body UUID, making it available for reuse.
     *
     * @param id The UUID of the body to remove.
     * @return The released index, or null if the body was not found.
     */
    @Nullable
    public synchronized Integer removeBody(UUID id) {
        int index = uuidToIndex.removeInt(id);
        if (index != -1) {
            resetIndex(index);
            freeIndices.add(index);
            indexToUuid.set(index, null);
            return index;
        }
        return null;
    }

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
    public synchronized void clear() {
        uuidToIndex.clear();
        indexToUuid.clear();
        networkIdToUuid.clear();
        freeIndices.clear();
        count = 0;
        allocate(INITIAL_CAPACITY);
    }

    /**
     * Gets the index for a given body UUID.
     *
     * @param id The UUID of the body.
     * @return The integer index, or null if the body is not in the store.
     */
    @Nullable
    public synchronized Integer getIndexForId(UUID id) {
        int index = uuidToIndex.getInt(id);
        return index == -1 ? null : index;
    }

    /**
     * Gets the UUID for a given index.
     *
     * @param index The index of the body.
     * @return The UUID, or null if the index is invalid or free.
     */
    @Nullable
    public synchronized UUID getIdForIndex(int index) {
        if (index < 0 || index >= indexToUuid.size()) {
            return null;
        }
        return indexToUuid.get(index);
    }

    /**
     * @return The total number of active bodies in the store.
     */
    public synchronized int getBodyCount() {
        return this.count - freeIndices.size();
    }

    /**
     * @return The total number of bodies the store can hold before reallocating.
     */
    public int getCapacity() {
        return this.capacity;
    }

    private void resetIndex(int index) {
        posX[index] = posY[index] = posZ[index] = 0.0;
        rotX[index] = rotY[index] = rotZ[index] = 0f;
        rotW[index] = 1f;
        velX[index] = velY[index] = velZ[index] = 0f;
        angVelX[index] = angVelY[index] = angVelZ[index] = 0f;
        aabbMinX[index] = aabbMinY[index] = aabbMinZ[index] = 0f;
        aabbMaxX[index] = aabbMaxY[index] = aabbMaxZ[index] = 0f;
        vertexData[index] = null;
        isActive[index] = false;
        bodyType[index] = null;
        motionType[index] = null;
        chunkKey[index] = Long.MAX_VALUE;
        networkId[index] = -1;
        isGameStateDirty[index] = false;
        isTransformDirty[index] = false;
        isVertexDataDirty[index] = false;
        isCustomDataDirty[index] = false;
        lastUpdateTimestamp[index] = 0L;
    }
}