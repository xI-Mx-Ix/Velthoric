/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.xmx.velthoric.core.AbstractDataStore;
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
     * The default initial capacity of the data arrays before any growth occurs.
     */
    protected static final int INITIAL_CAPACITY = 1024;

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

    /**
     * Structural double-buffered container for thread-safe resizing.
     */
    protected volatile VxBodyDataContainer currentContainer;

    /**
     * Constructs the data store and initializes ID maps.
     */
    protected VxBodyDataStore() {
        // -1 indicates "no key found" for fastutil primitive maps
        uuidToIndex.defaultReturnValue(-1);
    }

    public VxBodyDataContainer current() {
        return currentContainer;
    }

    /**
     * Reserves a new index for the specified body object.
     * <p>
     * If the arrays are full, this method triggers a {@link #allocate(int)} call to grow them.
     * This method automatically populates the {@code bodies} reference array at the reserved index.
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
        currentContainer.bodies[index] = body;

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
        VxBodyDataContainer c = currentContainer;
        c.bodies[index] = null;
        c.posX[index] = c.posY[index] = c.posZ[index] = 0.0;
        c.rotX[index] = c.rotY[index] = c.rotZ[index] = 0f;
        c.rotW[index] = 1f; // Identity Quaternion
        c.velX[index] = c.velY[index] = c.velZ[index] = 0f;
        c.vertexData[index] = null;
        c.isActive[index] = false;
        c.behaviorBits[index] = 0L;
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
     * This method handles expansion of the arrays by creating a new container
     * and swapping it atomically.
     *
     * @param newCapacity The new capacity for the data arrays.
     */
    protected void growBaseArrays(int newCapacity) {
        VxBodyDataContainer old = currentContainer;
        VxBodyDataContainer next = createContainer(newCapacity);

        if (old != null) {
            int copyLength = Math.min(old.capacity, newCapacity);
            System.arraycopy(old.posX, 0, next.posX, 0, copyLength);
            System.arraycopy(old.posY, 0, next.posY, 0, copyLength);
            System.arraycopy(old.posZ, 0, next.posZ, 0, copyLength);
            System.arraycopy(old.rotX, 0, next.rotX, 0, copyLength);
            System.arraycopy(old.rotY, 0, next.rotY, 0, copyLength);
            System.arraycopy(old.rotZ, 0, next.rotZ, 0, copyLength);
            System.arraycopy(old.rotW, 0, next.rotW, 0, copyLength);
            System.arraycopy(old.velX, 0, next.velX, 0, copyLength);
            System.arraycopy(old.velY, 0, next.velY, 0, copyLength);
            System.arraycopy(old.velZ, 0, next.velZ, 0, copyLength);
            System.arraycopy(old.vertexData, 0, next.vertexData, 0, copyLength);
            System.arraycopy(old.isActive, 0, next.isActive, 0, copyLength);
            System.arraycopy(old.behaviorBits, 0, next.behaviorBits, 0, copyLength);
            System.arraycopy(old.bodies, 0, next.bodies, 0, copyLength);
        }

        this.currentContainer = next;
        this.capacity = newCapacity;
    }

    /**
     * Factory method to create a container of the correct type.
     */
    protected abstract VxBodyDataContainer createContainer(int newCapacity);

    /**
     * Clears all mapped bodies and resets the store to its initial state.
     * <p>
     * Reallocates the default initial capacity to ensure that all data 
     * is physically cleared and memory is reclaimed, while maintaining
     * the structural mapping capacity.
     */
    public void clear() {
        uuidToIndex.clear();
        indexToUuid.clear();
        freeIndices.clear();
        count = 0;

        // Allocation will create a fresh container, effectively clearing data.
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