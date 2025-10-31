/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.client;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import dev.architectury.event.events.client.ClientTickEvent;
import io.netty.buffer.ByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.event.api.VxClientLevelEvent;
import net.xmx.velthoric.event.api.VxClientPlayerNetworkEvent;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.mounting.VxMountable;
import net.xmx.velthoric.physics.mounting.manager.VxClientMountingManager;
import net.xmx.velthoric.physics.mounting.seat.VxSeat;
import net.xmx.velthoric.physics.body.registry.VxBodyType;
import net.xmx.velthoric.physics.body.client.time.VxClientClock;
import net.xmx.velthoric.physics.body.registry.VxBodyRegistry;
import net.xmx.velthoric.physics.body.type.VxBody;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The main singleton manager for all client-side physics bodies.
 * This class is responsible for:
 * - Storing and managing the state of all physics bodies known to the client.
 * - Processing incoming state update packets from the server.
 * - Synchronizing the client-side clock with the server's clock.
 * - Triggering the interpolation of body states for smooth rendering.
 * - Handling the spawning and removal of bodies via {@link VxBody} handles.
 *
 * @author xI-Mx-Ix
 */
public class VxClientBodyManager {
    // The singleton instance of the manager.
    private static final VxClientBodyManager INSTANCE = new VxClientBodyManager();

    // The delay applied to rendering to allow for interpolation. A larger value
    // can smooth over more network jitter but increases perceived latency.
    // Value is in nanoseconds.
    private static final long INTERPOLATION_DELAY_NANOS = 150_000_000L;

    // The data store holding all body states in a Structure of Arrays format.
    private final VxClientBodyDataStore store = new VxClientBodyDataStore();
    // The interpolator responsible for calculating smooth body transforms.
    private final VxClientBodyInterpolator interpolator = new VxClientBodyInterpolator();
    // The client-side clock, which can be paused.
    private final VxClientClock clock = VxClientClock.INSTANCE;

    // Map of all active client-side physics body handles, keyed by their persistent UUID.
    private final Map<UUID, VxBody> managedBodies = new ConcurrentHashMap<>();

    // The calculated time offset between the client and server clocks.
    private long clockOffsetNanos = 0L;
    // Flag indicating if the initial clock synchronization has completed.
    private boolean isClockOffsetInitialized = false;
    // A list of recent clock offset samples used for calculating an average.
    private final List<Long> clockOffsetSamples = new ArrayList<>();

    // Private constructor to enforce singleton pattern.
    private VxClientBodyManager() {}

    /**
     * @return The singleton instance of the {@link VxClientBodyManager}.
     */
    public static VxClientBodyManager getInstance() {
        return INSTANCE;
    }

    /**
     * Adds a sample to be used for server-client clock synchronization.
     * This is called from packet handlers.
     * @param offsetSample The calculated time offset for a single packet.
     */
    public void addClockSyncSample(long offsetSample) {
        synchronized (clockOffsetSamples) {
            this.clockOffsetSamples.add(offsetSample);
        }
    }

    /**
     * Synchronizes the client clock with the server clock using collected samples.
     * It uses a trimmed mean to discard outliers caused by network spikes.
     */
    private void synchronizeClock() {
        synchronized (clockOffsetSamples) {
            // Wait for a sufficient number of samples before calculating.
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

            // Smoothly adjust the clock offset to the new average.
            if (!isClockOffsetInitialized) {
                this.clockOffsetNanos = averageOffset;
                this.isClockOffsetInitialized = true;
            } else {
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

        VxBody body = registry.createClientBody(type, id);

        if (body == null) {
            // The registry's createClientBody method already logs the specific error.
            return;
        }

        // If the body is mountable, register its seats on the client.
        if (body instanceof VxMountable mountable) {
            VxSeat.Builder seatBuilder = new VxSeat.Builder();
            mountable.defineSeats(seatBuilder);
            List<VxSeat> seats = seatBuilder.build();

            for (VxSeat seat : seats) {
                VxClientMountingManager.INSTANCE.addSeat(id, seat);
            }
        }

        int index = store.addBody(id, networkId);
        body.setDataStoreIndex(index);
        managedBodies.put(id, body);

        VxTransform transform = new VxTransform();
        transform.fromBuffer(data);

        body.getSynchronizedData().readEntries(data, body);

        initializeState(index, transform, timestamp);
    }

    /**
     * Initializes the state buffers for a newly spawned body.
     * Both state0 and state1 are set to the initial spawn state to prevent interpolation from zero.
     *
     * @param index The data store index of the body.
     * @param transform The initial transform.
     * @param timestamp The spawn timestamp.
     */
    private void initializeState(int index, VxTransform transform, long timestamp) {
        RVec3 pos = transform.getTranslation();
        Quat rot = transform.getRotation();

        store.state0_timestamp[index] = timestamp;
        store.state1_timestamp[index] = timestamp;
        store.state0_posX[index] = store.state1_posX[index] = pos.x();
        store.state0_posY[index] = store.state1_posY[index] = pos.y();
        store.state0_posZ[index] = store.state1_posZ[index] = pos.z();
        store.state0_rotX[index] = store.state1_rotX[index] = rot.getX();
        store.state0_rotY[index] = store.state1_rotY[index] = rot.getY();
        store.state0_rotZ[index] = store.state1_rotZ[index] = rot.getZ();
        store.state0_rotW[index] = store.state1_rotW[index] = rot.getW();
        store.state0_isActive[index] = store.state1_isActive[index] = true;

        store.render_posX[index] = pos.x();
        store.render_posY[index] = pos.y();
        store.render_posZ[index] = pos.z();
        store.render_rotX[index] = rot.getX();
        store.render_rotY[index] = rot.getY();
        store.render_rotZ[index] = rot.getZ();
        store.render_rotW[index] = rot.getW();

        store.prev_posX[index] = pos.x();
        store.prev_posY[index] = pos.y();
        store.prev_posZ[index] = pos.z();
        store.prev_rotX[index] = rot.getX();
        store.prev_rotY[index] = rot.getY();
        store.prev_rotZ[index] = rot.getZ();
        store.prev_rotW[index] = rot.getW();

        store.render_isInitialized[index] = true;

        if (store.lastKnownPosition[index] == null) {
            store.lastKnownPosition[index] = new RVec3();
        }
        store.lastKnownPosition[index].set(pos);
    }

    /**
     * Removes a physics body from the client using its network ID.
     *
     * @param networkId The network ID of the body to remove.
     */
    public void removeBody(int networkId) {
        Integer index = store.getIndexForNetworkId(networkId);
        if (index != null) {
            UUID id = store.getUuidForIndex(index);
            if (id != null) {
                managedBodies.remove(id);
                VxClientMountingManager.INSTANCE.removeSeatsForBody(id);
            }
        }
        store.removeBodyByNetworkId(networkId);
    }

    /**
     * Updates the synchronized data for a specific body.
     *
     * @param networkId The network ID of the body to update.
     * @param data The buffer containing the new synchronized data.
     */
    public void updateSynchronizedData(int networkId, ByteBuf data) {
        Integer index = store.getIndexForNetworkId(networkId);
        if (index == null) return;

        UUID id = store.getUuidForIndex(index);
        if (id == null) return;

        VxBody body = managedBodies.get(id);
        if (body != null) {
            try {
                // The incoming ByteBuf is wrapped in our custom VxByteBuf for deserialization.
                // We now pass the body instance itself to the readEntries method.
                body.getSynchronizedData().readEntries(new VxByteBuf(data), body);
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Failed to read synchronized data for body {}", id, e);
            }
        }
    }

    /**
     * Clears all client-side physics data. Called on disconnect.
     */
    public void clearAll() {
        store.clear();
        managedBodies.clear();
        isClockOffsetInitialized = false;
        clockOffsetNanos = 0L;
        synchronized(clockOffsetSamples) {
            clockOffsetSamples.clear();
        }
    }

    /**
     * The main client-side tick method.
     * Synchronizes the clock, and runs interpolation.
     */
    public void clientTick() {
        synchronizeClock();
        if (isClockOffsetInitialized) {
            // Calculate the target render time, accounting for the clock offset and interpolation delay.
            long renderTimestamp = clock.getGameTimeNanos() + this.clockOffsetNanos - INTERPOLATION_DELAY_NANOS;
            interpolator.updateInterpolationTargets(store, renderTimestamp);
        }
    }

    /**
     * Registers the necessary client-side event listeners.
     */
    public static void registerEvents() {
        ClientTickEvent.CLIENT_PRE.register(client -> INSTANCE.clientTick());
        VxClientPlayerNetworkEvent.LoggingOut.EVENT.register(event -> INSTANCE.clearAll());
        VxClientLevelEvent.Load.EVENT.register(event -> INSTANCE.clearAll());
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
     * Gets a collection of all managed client-side physics body handles.
     *
     * @return A collection view of all {@link VxBody} instances.
     */
    public Collection<VxBody> getAllBodies() {
        return managedBodies.values();
    }

    /**
     * @return The client-side clock.
     */
    public VxClientClock getClock() {
        return clock;
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