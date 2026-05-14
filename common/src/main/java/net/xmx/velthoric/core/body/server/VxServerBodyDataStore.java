/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.server;

import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.xmx.velthoric.core.body.VxBodyDataStore;
import net.xmx.velthoric.core.body.VxBodyDataContainer;
import net.xmx.velthoric.core.body.VxBody;
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

    // --- Server Logic & Networking ---

    /**
     * Reverse lookup map for network synchronization (Network ID -> Body UUID).
     */
    private final Int2ObjectMap<UUID> networkIdToUuid = new Int2ObjectOpenHashMap<>();

    /**
     * Initializes the server data store and pre-allocates the initial container.
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
        VxServerBodyDataContainer c = serverCurrent();
        c.bodyType[index] = type;
        c.chunkKey[index] = Long.MAX_VALUE; // Sentinel for "no chunk"
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
        Integer index = super.removeBody(id);
        if (index != null) {
            serverCurrent().dirtyIndices.remove((int) index);
        }
        return index;
    }

    /**
     * Resets all data at a specific index to default values.
     * <p>
     * Delegates to {@link VxServerBodyDataContainer#reset(int)} and handles
     * Network ID registry cleanup.
     *
     * @param index The index to reset.
     */
    @Override
    protected void resetIndex(int index) {
        VxServerBodyDataContainer c = serverCurrent();

        // Handle Network ID cleanup before resetting the array slot
        int netId = c.networkId[index];
        if (netId != -1) {
            unregisterNetworkId(netId);
        }

        // Delegate full reset to container
        super.resetIndex(index);
    }

    /**
     * Reallocates all data arrays to a new capacity.
     * <p>
     * Triggers base reallocation logic which handles the atomical swap
     * of the underlying {@link VxServerBodyDataContainer}.
     *
     * @param newCapacity The new size for the arrays.
     */
    @Override
    protected void allocate(int newCapacity) {
        super.growBaseArrays(newCapacity);
    }

    /**
     * Returns the currently active server-side data container.
     *
     * @return The typed container instance.
     */
    public VxServerBodyDataContainer serverCurrent() {
        return (VxServerBodyDataContainer) currentContainer;
    }

    @Override
    protected VxBodyDataContainer createContainer(int newCapacity) {
        return new VxServerBodyDataContainer(newCapacity);
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