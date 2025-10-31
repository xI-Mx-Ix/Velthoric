/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.manager;

import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.xmx.velthoric.physics.body.AbstractDataStore;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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

    private final Map<UUID, Integer> uuidToIndex = new HashMap<>();
    private final List<UUID> indexToUuid = new ArrayList<>();
    private final Deque<Integer> freeIndices = new ArrayDeque<>();
    private int count = 0;
    private int capacity = 0;

    // --- Physics State Data (SoA) ---
    public float[] posX, posY, posZ;
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
    /** Flag indicating that the game logic has modified this body's state, requiring a sync to Jolt. */
    public boolean[] isGameStateDirty;
    /** Flag indicating that the body's transform (pos/rot/vel) has changed, requiring a network sync. */
    public boolean[] isTransformDirty;
    /** Flag indicating that the body's vertex data (for soft bodies) has changed, requiring a network sync. */
    public boolean[] isVertexDataDirty;
    /** Flag indicating that the body's custom data has changed, requiring a network sync. */
    public boolean[] isCustomDataDirty;
    /** The server timestamp of the last physics update for this body. */
    public long[] lastUpdateTimestamp;

    public VxBodyDataStore() {
        allocate(INITIAL_CAPACITY);
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
     * @param id The UUID of the body.
     * @param type The EBodyType of the body.
     * @return The data store index for the new body.
     */
    public synchronized int addBody(UUID id, EBodyType type) {
        if (uuidToIndex.containsKey(id)) {
            return uuidToIndex.get(id);
        }

        if (count == capacity) {
            allocate(capacity * 2);
        }
        int index = freeIndices.isEmpty() ? count++ : freeIndices.pop();

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
        Integer index = uuidToIndex.remove(id);
        if (index != null) {
            resetIndex(index);
            freeIndices.push(index);
            indexToUuid.set(index, null);
            return index;
        }
        return null;
    }

    /**
     * Clears all data and resets the store to its initial state.
     */
    public synchronized void clear() {
        uuidToIndex.clear();
        indexToUuid.clear();
        freeIndices.clear();
        count = 0;
        allocate(INITIAL_CAPACITY);
    }

    @Nullable
    public synchronized Integer getIndexForId(UUID id) {
        return uuidToIndex.get(id);
    }

    @Nullable
    public synchronized UUID getIdForIndex(int index) {
        if (index < 0 || index >= indexToUuid.size()) {
            return null;
        }
        return indexToUuid.get(index);
    }

    public synchronized int getBodyCount() {
        return this.count - freeIndices.size();
    }

    public int getCapacity() {
        return this.capacity;
    }

    private void resetIndex(int index) {
        posX[index] = posY[index] = posZ[index] = 0f;
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