/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.client;

import com.github.stephengold.joltjni.RVec3;
import io.netty.buffer.ByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.core.mounting.VxMountable;
import net.xmx.velthoric.core.mounting.manager.VxClientMountingManager;
import net.xmx.velthoric.core.mounting.seat.VxSeat;
import net.xmx.velthoric.config.VxModConfig;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.core.body.client.time.VxClientClock;
import net.xmx.velthoric.core.network.synchronization.manager.VxClientSyncManager;
import net.xmx.velthoric.core.body.registry.VxBodyRegistry;
import net.xmx.velthoric.core.body.registry.VxBodyType;
import net.xmx.velthoric.core.body.type.VxBody;
import net.xmx.velthoric.core.physics.world.VxClientPhysicsWorld;
import net.xmx.velthoric.core.body.manager.VxAbstractBodyManager;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The main manager for all client-side physics bodies.
 * <p>
 * This class is responsible for:
 * <ul>
 *     <li>Storing and managing the state of all physics bodies known to the client.</li>
 *     <li>Processing incoming state update packets from the server.</li>
 *     <li>Synchronizing the client-side clock with the server's clock.</li>
 *     <li>Triggering the interpolation of body states for smooth rendering.</li>
 *     <li>Handling the spawning and removal of bodies via {@link VxBody} handles.</li>
 * </ul>
 *
 * @author xI-Mx-Ix
 */
public class VxClientBodyManager extends VxAbstractBodyManager {

    // The parent physics world context that owns this manager.
    private final VxClientPhysicsWorld world;

    // The delay applied to rendering to allow for interpolation. A larger value
    // can smooth over more network jitter but increases perceived latency.
    // Value is in nanoseconds (150ms).
    private final long interpolationDelayNanos;

    // The data store holding all body states in a Structure of Arrays format.
    private final VxClientBodyDataStore store = new VxClientBodyDataStore();

    // The interpolator responsible for calculating smooth body transforms based on history buffers.
    private final VxClientBodyInterpolator interpolator = new VxClientBodyInterpolator();

    // The manager responsible for synchronizing client-authoritative data (C2S).
    private final VxClientSyncManager syncManager;

    // The calculated time offset between the client and server clocks.
    // Client Render Time = Client Game Time + Offset - Interpolation Delay.
    private long clockOffsetNanos = 0L;

    // Flag indicating if the initial clock synchronization has completed.
    private boolean isClockOffsetInitialized = false;

    // A list of recent clock offset samples used for calculating an average.
    private final List<Long> clockOffsetSamples = new ArrayList<>();

    // The manager responsible for mountable seats on the client.
    private final VxClientMountingManager mountingManager;

    /**
     * Constructs the client body manager.
     *
     * @param world The parent client physics world.
     */
    public VxClientBodyManager(VxClientPhysicsWorld world) {
        this.world = world;
        this.syncManager = new VxClientSyncManager(this);
        this.interpolationDelayNanos = VxModConfig.CLIENT.interpolationDelayNanos.get();
        this.mountingManager = new VxClientMountingManager();
    }

    /**
     * Adds a sample to be used for server-client clock synchronization.
     * This is typically called from packet handlers when a timestamped packet is received.
     *
     * @param offsetSample The calculated time offset for a single packet.
     */
    public void addClockSyncSample(long offsetSample) {
        synchronized (clockOffsetSamples) {
            this.clockOffsetSamples.add(offsetSample);
        }
    }

    /**
     * Synchronizes the client clock with the server clock using collected samples.
     * It uses a trimmed mean to discard outliers caused by network spikes (jitter).
     */
    private void synchronizeClock() {
        synchronized (clockOffsetSamples) {
            // Wait for a sufficient number of samples before calculating to ensure stability.
            if (clockOffsetSamples.size() < 20) {
                return;
            }

            Collections.sort(clockOffsetSamples);

            // Trim the top and bottom 25% of samples to remove outliers.
            int trimCount = clockOffsetSamples.size() / 4;
            List<Long> trimmedSamples = clockOffsetSamples.subList(trimCount, clockOffsetSamples.size() - trimCount);

            if (trimmedSamples.isEmpty()) {
                clockOffsetSamples.clear();
                return;
            }

            // Calculate the average of the remaining samples.
            long sum = 0L;
            for (Long sample : trimmedSamples) {
                sum += sample;
            }
            long averageOffset = sum / trimmedSamples.size();
            clockOffsetSamples.clear();

            // Smoothly adjust the clock offset to the new average to prevents visible snapping.
            if (!isClockOffsetInitialized) {
                this.clockOffsetNanos = averageOffset;
                this.isClockOffsetInitialized = true;
            } else {
                // Apply a low-pass filter (5% weight to new value)
                this.clockOffsetNanos = (long) (this.clockOffsetNanos * 0.95 + averageOffset * 0.05);
            }
        }
    }

    /**
     * Spawns a new physics body on the client based on data from a spawn packet.
     *
     * @param id          The UUID of the new body.
     * @param networkId   The session-specific network ID for the body.
     * @param typeId      The ResourceLocation identifying the body's type.
     * @param data        A buffer containing the initial transform and custom sync data.
     * @param timestamp   The server-side timestamp of the spawn event.
     */
    public void spawnBody(UUID id, int networkId, ResourceLocation typeId, VxByteBuf data, long timestamp) {
        // Prevent duplicate spawning logic
        if (store.hasBody(id)) {
            VxMainClass.LOGGER.warn("Client received spawn request for already existing body: {}", id);
            return;
        }

        VxBodyRegistry registry = VxBodyRegistry.getInstance();
        VxBodyType<?> type = registry.getRegistrationData(typeId);

        if (type == null) {
            VxMainClass.LOGGER.error("Could not spawn client body with type ID '{}', type not registered on client.", typeId);
            return;
        }

        // Create the body instance via the registry factory
        VxBody body = registry.createClientBody(type, id);

        if (body == null) {
            // The registry's createClientBody method already logs the specific error.
            return;
        }

        // If the body is mountable, register its seats on the client via the MountingManager.
        if (body instanceof VxMountable mountable) {
            VxSeat.Builder seatBuilder = new VxSeat.Builder();
            mountable.defineSeats(seatBuilder);
            List<VxSeat> seats = seatBuilder.build();

            for (VxSeat seat : seats) {
                mountingManager.addSeat(id, seat);
            }
        }

        // Register in SoA DataStore
        int index = store.addBody(body, networkId);
        body.setDataStoreIndex(store, index);
        managedBodies.put(id, body);

        // Deserialize initial transform
        VxTransform transform = new VxTransform();
        transform.fromBuffer(data);

        // Read synchronized data entries (custom user data)
        body.getSynchronizedData().readEntries(data, body);

        // Initialize interpolation buffers
        initializeState(index, transform, timestamp);

        // Notify the body that it has been added to the client level.
        if (world.getLevel() != null) {
            body.onBodyAdded(world.getLevel());
        }

        // Notify listeners
        notifyBodyAdded(body);
    }

    /**
     * Initializes the state buffers for a newly spawned body.
     * Both state0 (old), state1 (new), prev (frame history), and render (current)
     * are set to the initial spawn state to prevent interpolating from (0,0,0).
     *
     * @param index     The data store index of the body.
     * @param transform The initial transform.
     * @param timestamp The spawn timestamp.
     */
    private void initializeState(int index, VxTransform transform, long timestamp) {
        // Extract values
        double x = transform.getTranslation().x();
        double y = transform.getTranslation().y();
        double z = transform.getTranslation().z();
        float rx = transform.getRotation().getX();
        float ry = transform.getRotation().getY();
        float rz = transform.getRotation().getZ();
        float rw = transform.getRotation().getW();

        // Initialize timestamps
        store.state0_timestamp[index] = timestamp;
        store.state1_timestamp[index] = timestamp;

        // Mark as active
        store.state0_isActive[index] = true;
        store.state1_isActive[index] = true;

        // Initialize Position Buffers (State 0, State 1, Render, Previous Frame)
        store.state0_posX[index] = store.state1_posX[index] = store.posX[index] = store.prev_posX[index] = x;
        store.state0_posY[index] = store.state1_posY[index] = store.posY[index] = store.prev_posY[index] = y;
        store.state0_posZ[index] = store.state1_posZ[index] = store.posZ[index] = store.prev_posZ[index] = z;

        // Initialize Rotation Buffers
        store.state0_rotX[index] = store.state1_rotX[index] = store.rotX[index] = store.prev_rotX[index] = rx;
        store.state0_rotY[index] = store.state1_rotY[index] = store.rotY[index] = store.prev_rotY[index] = ry;
        store.state0_rotZ[index] = store.state1_rotZ[index] = store.rotZ[index] = store.prev_rotZ[index] = rz;
        store.state0_rotW[index] = store.state1_rotW[index] = store.rotW[index] = store.prev_rotW[index] = rw;

        // Reset Velocities
        store.state0_velX[index] = store.state0_velY[index] = store.state0_velZ[index] = 0f;
        store.state1_velX[index] = store.state1_velX[index] = store.state1_velZ[index] = 0f;

        // Initialize Last Known Position (for Frustum Culling or Logic)
        if (store.lastKnownPosition[index] == null) {
            store.lastKnownPosition[index] = new RVec3();
        }
        store.lastKnownPosition[index].set(x, y, z);

        // Mark render state as initialized
        store.render_isInitialized[index] = true;
    }

    /**
     * Removes a physics body from the client using its network ID.
     *
     * @param networkId The network ID of the body to remove.
     */
    public void removeBody(int networkId) {
        Integer index = store.getIndexForNetworkId(networkId);
        if (index != null) {
            UUID id = store.getIdForIndex(index);
            if (id != null) {
                VxBody body = managedBodies.get(id);

                if (body != null) {
                    // Notify sync manager to stop tracking dirtiness for this body
                    syncManager.onBodyRemoved(body);

                    // Notify the body that it has been removed from the client level.
                    if (world.getLevel() != null) {
                        body.onBodyRemoved(world.getLevel());
                    }

                    // Notify listeners
                    notifyBodyRemoved(body);
                }

                managedBodies.remove(id);
                // Remove seats via the instance-based manager
                mountingManager.removeSeatsForBody(id);
            }
        }
        store.removeBodyByNetworkId(networkId);
    }

    /**
     * Marks a body as having modified CLIENT-authoritative data that needs
     * to be sent to the server (e.g., custom sync data).
     *
     * @param body The body that changed.
     */
    public void markBodyDirty(VxBody body) {
        syncManager.markBodyDirty(body);
    }

    /**
     * Delegates handling of server-sent synchronized data updates to the SyncManager.
     * This handles updates to custom SyncedDataEntries.
     *
     * @param networkId The network ID of the body.
     * @param data      The buffer containing the data.
     */
    public void updateSynchronizedData(int networkId, ByteBuf data) {
        syncManager.handleServerUpdate(networkId, data);
    }

    /**
     * Clears all client-side physics data.
     * Called when the client world is unloaded or the player disconnects.
     */
    public void clearAll() {
        store.clear();
        this.clearInternal();
        syncManager.clear();
        isClockOffsetInitialized = false;
        clockOffsetNanos = 0L;
        synchronized(clockOffsetSamples) {
            clockOffsetSamples.clear();
        }
    }

    /**
     * The main client-side tick method.
     * Synchronizes the clock, runs body callbacks, and triggers interpolation.
     */
    public void clientTick() {
        // Tick all active client bodies (e.g. for client-side particles or logic)
        for (VxBody body : managedBodies.values()) {
            body.onClientTick();
        }

        // Process synchronization tasks (sending C2S updates for dirty bodies)
        syncManager.tick();

        // Calculate and smooth clock offset
        synchronizeClock();

        if (isClockOffsetInitialized) {
            // Calculate the target render time based on the synced clock.
            // Formula: GameTime + ClockOffset - InterpolationDelay
            long renderTimestamp = world.getClock().getGameTimeNanos() + this.clockOffsetNanos - this.interpolationDelayNanos;

            // Perform interpolation for all bodies in the store
            interpolator.updateInterpolationTargets(store, renderTimestamp);
        }
    }

    /**
     * @return The client-side data store.
     */
    public VxClientBodyDataStore getStore() {
        return store;
    }

    /**
     * @return The client-side body interpolator.
     */
    public VxClientBodyInterpolator getInterpolator() {
        return interpolator;
    }

    /**
     * @return The manager responsible for mountable seats on the client.
     */
    public VxClientMountingManager getMountingManager() {
        return mountingManager;
    }

    /**
     * @return The client-side clock from the parent world.
     */
    public VxClientClock getClock() {
        return world.getClock();
    }

    /**
     * Gets a client-side physics body handle by its UUID.
     *
     * @param id The UUID of the body.
     * @return The {@link VxBody} instance, or null if not currently managed.
     */
    @Nullable
    public VxBody getBody(UUID id) {
        return managedBodies.get(id);
    }
}