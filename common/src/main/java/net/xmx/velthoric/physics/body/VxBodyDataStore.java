/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.xmx.velthoric.physics.AbstractDataStore;
import net.xmx.velthoric.physics.body.type.VxBody;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

/**
 * The abstract base class for a Structure of Arrays (SoA) physics data store.
 * <p>
 * This class manages the central mapping between unique Body UUIDs and their integer indices
 * within the data arrays. It also holds the core physical state vectors (Position, Rotation,
 * Velocity) used on both Client and Server.
 * <p>
 * <b>Role Distinction:</b>
 * <ul>
 *     <li><b>On Server:</b> The arrays {@code posX}, {@code rotX}, etc., represent the authoritative
 *     physics simulation state calculated by Jolt.</li>
 *     <li><b>On Client:</b> The arrays {@code posX}, {@code rotX}, etc., represent the visual
 *     <b>Render State</b> (the result of interpolation/extrapolation).</li>
 * </ul>
 *
 * @author xI-Mx-Ix
 */
public abstract class VxBodyDataStore extends AbstractDataStore {

    /**
     * The initial capacity of the data arrays.
     */
    protected static final int INITIAL_CAPACITY = 256;

    // --- ID Management ---

    /**
     * Maps a persistent Body UUID to its current index in the SoA arrays.
     */
    protected final Object2IntMap<UUID> uuidToIndex = new Object2IntOpenHashMap<>();

    /**
     * Reverse mapping from index to UUID.
     */
    protected final ObjectArrayList<UUID> indexToUuid = new ObjectArrayList<>();

    /**
     * Stack of indices that were previously removed and can be reused to keep arrays compact.
     */
    protected final IntArrayList freeIndices = new IntArrayList();

    /**
     * The number of active bodies.
     * Note: This count may include holes if accessed raw, but `uuidToIndex.size()` is the true active count.
     */
    protected int count = 0;

    /**
     * The current allocated size of the arrays.
     */
    protected int capacity = 0;

    // --- Shared Physical State (SoA) ---

    /**
     * X position (Double precision for world-space coordinates).
     * On Client: Interpolated Render Position.
     */
    public double[] posX;

    /**
     * Y position (Double precision for world-space coordinates).
     * On Client: Interpolated Render Position.
     */
    public double[] posY;

    /**
     * Z position (Double precision for world-space coordinates).
     * On Client: Interpolated Render Position.
     */
    public double[] posZ;

    /**
     * Rotation X component (Quaternion).
     * On Client: Interpolated Render Rotation.
     */
    public float[] rotX;

    /**
     * Rotation Y component (Quaternion).
     * On Client: Interpolated Render Rotation.
     */
    public float[] rotY;

    /**
     * Rotation Z component (Quaternion).
     * On Client: Interpolated Render Rotation.
     */
    public float[] rotZ;

    /**
     * Rotation W component (Quaternion).
     * On Client: Interpolated Render Rotation.
     */
    public float[] rotW;

    /**
     * Linear Velocity X.
     */
    public float[] velX;

    /**
     * Linear Velocity Y.
     */
    public float[] velY;

    /**
     * Linear Velocity Z.
     */
    public float[] velZ;

    /**
     * Soft Body Vertex Data (Null for rigid bodies).
     * Contains flattened [x, y, z, x, y, z...] data.
     */
    public float[] @Nullable [] vertexData;

    /**
     * Active state flag. True if the body is currently simulated/rendered.
     */
    public boolean[] isActive;

    /**
     * Direct reference array to the VxBody objects.
     * <p>
     * This array mirrors the internal structure-of-arrays indices. It allows O(1) access
     * to the body object given an index, bypassing the need for UUID map lookups
     * in hot loops like the physics updater.
     */
    public VxBody[] bodies;

    /**
     * Constructs the data store and initializes ID maps.
     */
    protected VxBodyDataStore() {
        // -1 indicates "no key found" for fastutil primitive maps
        uuidToIndex.defaultReturnValue(-1);
    }

    /**
     * Reserves a new index for the specified body object.
     * <p>
     * If the arrays are full, this method triggers a {@link #allocate(int)} call to grow them.
     * This method automatically populates the {@link #bodies} reference array at the reserved index.
     *
     * @param body The body instance to add to the store.
     * @return The assigned data store index.
     */
    protected int reserveIndex(VxBody body) {
        if (count >= capacity) {
            allocate(Math.max(INITIAL_CAPACITY, capacity * 2));
        }

        // Reuse a freed index if available to keep arrays compact
        int index;
        if (!freeIndices.isEmpty()) {
            index = freeIndices.removeInt(freeIndices.size() - 1);
        } else {
            index = count++;
        }

        UUID id = body.getPhysicsId();
        uuidToIndex.put(id, index);

        // Ensure the reverse lookup list is large enough
        if (index >= indexToUuid.size()) {
            indexToUuid.add(id);
        } else {
            indexToUuid.set(index, id);
        }

        // Directly map the body object for O(1) access
        this.bodies[index] = body;

        return index;
    }

    /**
     * Removes the body mapping for the given UUID and marks the index as free.
     * <p>
     * This calls {@link #resetIndex(int)} to clear data at that index.
     *
     * @param id The UUID of the body to remove.
     * @return The index that was freed, or null if the body did not exist.
     */
    @Nullable
    public Integer removeBody(UUID id) {
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
     * Resets all data fields at the specified index to their default values.
     * <p>
     * Clears the body reference to prevent memory leaks and resets physical properties.
     *
     * @param index The index to reset.
     */
    protected void resetIndex(int index) {
        bodies[index] = null;
        posX[index] = posY[index] = posZ[index] = 0.0;
        rotX[index] = rotY[index] = rotZ[index] = 0f;
        rotW[index] = 1f; // Identity Quaternion
        velX[index] = velY[index] = velZ[index] = 0f;
        vertexData[index] = null;
        isActive[index] = false;
    }

    /**
     * Allocates or re-allocates the underlying arrays to the specified capacity.
     * <p>
     * Subclasses must implement this to grow their specific arrays and call {@code super.growBaseArrays(newCapacity)}.
     *
     * @param newCapacity The new size of the arrays.
     */
    protected abstract void allocate(int newCapacity);

    /**
     * Helper method for subclasses to grow the base shared arrays.
     * <p>
     * Extends the capacity of the position, rotation, velocity, and reference arrays.
     * This method handles both expansion and contraction of the arrays, ensuring
     * that data is preserved up to the limit of the new capacity.
     *
     * @param newCapacity The new capacity for the data arrays.
     */
    protected void growBaseArrays(int newCapacity) {
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

        vertexData = grow(vertexData, newCapacity);
        isActive = grow(isActive, newCapacity);

        // Reallocate the direct reference array.
        VxBody[] newBodies = new VxBody[newCapacity];
        if (bodies != null) {
            // Determine the number of elements to copy, ensuring we do not overflow
            // the destination if shrinking, or read past the source if growing.
            int copyLength = Math.min(bodies.length, newCapacity);
            System.arraycopy(bodies, 0, newBodies, 0, copyLength);
        }
        bodies = newBodies;

        this.capacity = newCapacity;
    }

    /**
     * Clears all bodies and resets the store to its initial state.
     * <p>
     * This removes all mappings, resets counters, and re-allocates the internal
     * arrays to the initial capacity. The reference to the body array is nullified
     * before allocation to ensure that no stale references are copied into the
     * new store, allowing for immediate garbage collection of removed bodies.
     */
    public void clear() {
        uuidToIndex.clear();
        indexToUuid.clear();
        freeIndices.clear();
        count = 0;
        this.bodies = null;

        allocate(INITIAL_CAPACITY);
    }

    // --- Accessors ---

    /**
     * Gets the index for a given body UUID.
     *
     * @param id The UUID of the body.
     * @return The integer index, or null if the body is not in the store.
     */
    @Nullable
    public Integer getIndexForId(UUID id) {
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
    public UUID getIdForIndex(int index) {
        if (index >= 0 && index < indexToUuid.size()) {
            return indexToUuid.get(index);
        }
        return null;
    }

    /**
     * Checks if a body with the given UUID exists in the store.
     *
     * @param id The UUID to check.
     * @return True if the body exists, false otherwise.
     */
    public boolean hasBody(UUID id) {
        return uuidToIndex.containsKey(id);
    }

    /**
     * Returns an unmodifiable collection of all active body UUIDs.
     *
     * @return A set of UUIDs.
     */
    public Collection<UUID> getAllPhysicsIds() {
        return Collections.unmodifiableSet(uuidToIndex.keySet());
    }

    /**
     * Returns the total number of active bodies in the store.
     *
     * @return The count of bodies.
     */
    public int getBodyCount() {
        return count - freeIndices.size();
    }

    /**
     * Returns the total number of bodies the store can hold before reallocating.
     *
     * @return The capacity.
     */
    public int getCapacity() {
        return this.capacity;
    }

    /**
     * Returns the number of free indices available for reuse.
     *
     * @return The count of free indices.
     */
    public int getFreeIndicesCount() {
        return this.freeIndices.size();
    }
}