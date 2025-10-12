/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.client;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import dev.architectury.event.events.client.ClientTickEvent;
import io.netty.buffer.ByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.event.api.VxClientPlayerNetworkEvent;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.mounting.VxMountable;
import net.xmx.velthoric.physics.mounting.manager.VxClientMountingManager;
import net.xmx.velthoric.physics.mounting.seat.VxSeat;
import net.xmx.velthoric.physics.object.registry.VxObjectType;
import net.xmx.velthoric.physics.object.client.time.VxClientClock;
import net.xmx.velthoric.physics.object.registry.VxObjectRegistry;
import net.xmx.velthoric.physics.object.type.VxBody;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The main singleton manager for all client-side physics objects.
 * This class is responsible for:
 * - Storing and managing the state of all physics objects known to the client.
 * - Processing incoming state update packets from the server.
 * - Synchronizing the client-side clock with the server's clock.
 * - Triggering the interpolation of object states for smooth rendering.
 * - Handling the spawning and removal of objects via {@link VxBody} handles.
 *
 * @author xI-Mx-Ix
 */
public class VxClientObjectManager {
    // The singleton instance of the manager.
    private static final VxClientObjectManager INSTANCE = new VxClientObjectManager();

    // The delay applied to rendering to allow for interpolation. A larger value
    // can smooth over more network jitter but increases perceived latency.
    // Value is in nanoseconds.
    private static final long INTERPOLATION_DELAY_NANOS = 150_000_000L;

    // The data store holding all object states in a Structure of Arrays format.
    private final VxClientObjectDataStore store = new VxClientObjectDataStore();
    // The interpolator responsible for calculating smooth object transforms.
    private final VxClientObjectInterpolator interpolator = new VxClientObjectInterpolator();
    // The client-side clock, which can be paused.
    private final VxClientClock clock = VxClientClock.getInstance();

    // Map of all active client-side physics object handles.
    private final Map<UUID, VxBody> managedObjects = new ConcurrentHashMap<>();

    // The calculated time offset between the client and server clocks.
    private long clockOffsetNanos = 0L;
    // Flag indicating if the initial clock synchronization has completed.
    private boolean isClockOffsetInitialized = false;
    // A list of recent clock offset samples used for calculating an average.
    private final List<Long> clockOffsetSamples = new ArrayList<>();

    // Private constructor to enforce singleton pattern.
    private VxClientObjectManager() {}

    /**
     * @return The singleton instance of the {@link VxClientObjectManager}.
     */
    public static VxClientObjectManager getInstance() {
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
     * Spawns a new physics object on the client based on data from a spawn packet.
     *
     * @param id          The UUID of the new object.
     * @param typeId      The ResourceLocation identifying the object's type.
     * @param data        A buffer containing the initial transform and custom sync data.
     * @param timestamp   The server-side timestamp of the spawn event.
     */
    public void spawnObject(UUID id, ResourceLocation typeId, VxByteBuf data, long timestamp) {
        if (store.hasObject(id)) {
            VxMainClass.LOGGER.warn("Client received spawn request for already existing object: {}", id);
            return;
        }

        VxObjectRegistry registry = VxObjectRegistry.getInstance();
        VxObjectType<?> type = registry.getRegistrationData(typeId);

        if (type == null) {
            VxMainClass.LOGGER.error("Could not spawn client object with type ID '{}', type not registered on client.", typeId);
            return;
        }

        VxBody body = registry.createClientBody(type, id);

        if (body == null) {
            // The registry's createClientBody method already logs the specific error.
            return;
        }

        // If the object is mountable, register its seats on the client.
        if (body instanceof VxMountable mountable) {
            List<VxSeat> seats = mountable.defineSeats();
            if (seats != null) {
                for (VxSeat seat : seats) {
                    VxClientMountingManager.getInstance().addSeat(id, seat);
                }
            }
        }

        int index = store.addObject(id);
        body.setDataStoreIndex(index);
        managedObjects.put(id, body);

        VxTransform transform = new VxTransform();
        transform.fromBuffer(data);

        body.getSynchronizedData().readEntries(data, body);

        initializeState(index, transform, timestamp);
    }

    /**
     * Initializes the state buffers for a newly spawned object.
     * Both state0 and state1 are set to the initial spawn state to prevent interpolation from zero.
     *
     * @param index The data store index of the object.
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
     * Removes a physics object from the client.
     *
     * @param id The UUID of the object to remove.
     */
    public void removeObject(UUID id) {
        managedObjects.remove(id);
        store.removeObject(id);
        VxClientMountingManager.getInstance().removeSeatsForObject(id);
    }

    /**
     * Updates the synchronized data for a specific object.
     *
     * @param id   The UUID of the object to update.
     * @param data The buffer containing the new synchronized data.
     */
    public void updateSynchronizedData(UUID id, ByteBuf data) {
        VxBody body = managedObjects.get(id);
        if (body != null) {
            try {
                // The incoming ByteBuf is wrapped in our custom VxByteBuf for deserialization.
                // We now pass the body instance itself to the readEntries method.
                body.getSynchronizedData().readEntries(new VxByteBuf(data), body);
            } catch (Exception e) {
                VxMainClass.LOGGER.error("Failed to read synchronized data for object {}", id, e);
            }
        }
    }

    /**
     * Clears all client-side physics data. Called on disconnect.
     */
    public void clearAll() {
        store.clear();
        managedObjects.clear();
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
    }

    /**
     * @return The client-side data store.
     */
    public VxClientObjectDataStore getStore() {
        return store;
    }

    /**
     * @return The client-side object interpolator.
     */
    public VxClientObjectInterpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Gets a collection of all managed client-side physics object handles.
     *
     * @return A collection view of all {@link VxBody} instances.
     */
    public Collection<VxBody> getAllObjects() {
        return managedObjects.values();
    }

    /**
     * @return The client-side clock.
     */
    public VxClientClock getClock() {
        return clock;
    }

    /**
     * Gets a client-side physics object handle by its UUID.
     *
     * @param id The UUID of the object.
     * @return The {@link VxBody} instance, or null if not currently managed.
     */
    @Nullable
    public VxBody getObject(UUID id) {
        return managedObjects.get(id);
    }
}