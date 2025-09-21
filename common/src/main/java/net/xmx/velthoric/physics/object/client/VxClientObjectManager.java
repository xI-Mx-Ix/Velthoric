/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.client;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import dev.architectury.event.events.client.ClientTickEvent;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.xmx.velthoric.event.api.VxClientPlayerNetworkEvent;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.physics.object.client.body.VxClientBody;
import net.xmx.velthoric.physics.object.client.body.VxClientRigidBody;
import net.xmx.velthoric.physics.object.client.body.VxClientSoftBody;
import net.xmx.velthoric.physics.object.client.time.VxClientClock;
import net.xmx.velthoric.physics.object.registry.VxObjectRegistry;
import net.xmx.velthoric.physics.object.state.PhysicsObjectState;
import net.xmx.velthoric.physics.object.state.PhysicsObjectStatePool;
import net.xmx.velthoric.physics.object.type.VxBody;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The main singleton manager for all client-side physics objects.
 * This class is responsible for:
 * - Storing and managing the state of all physics objects known to the client.
 * - Processing incoming state update packets from the server.
 * - Synchronizing the client-side clock with the server's clock.
 * - Triggering the interpolation of object states for smooth rendering.
 * - Handling the spawning and removal of objects via {@link VxClientBody} handles.
 *
 * @author xI-Mx-Ix
 */
public class VxClientObjectManager {
    // The singleton instance of the manager.
    private static final VxClientObjectManager INSTANCE = new VxClientObjectManager();

    // The delay applied to rendering to allow for interpolation. A larger value
    // can smooth over more network jitter but increases perceived latency.
    // Value is in nanoseconds.
    private static final long INTERPOLATION_DELAY_NANOS = 220_000_000L;

    // The data store holding all object states in a Structure of Arrays format.
    private final VxClientObjectDataStore store = new VxClientObjectDataStore();
    // The interpolator responsible for calculating smooth object transforms.
    private final VxClientObjectInterpolator interpolator = new VxClientObjectInterpolator();
    // The client-side clock, which can be paused.
    private final VxClientClock clock = VxClientClock.getInstance();

    // Map of all active client-side physics object handles.
    private final Map<UUID, VxClientBody> managedObjects = new ConcurrentHashMap<>();

    // The calculated time offset between the client and server clocks.
    private long clockOffsetNanos = 0L;
    // Flag indicating if the initial clock synchronization has completed.
    private boolean isClockOffsetInitialized = false;
    // A list of recent clock offset samples used for calculating an average.
    private final List<Long> clockOffsetSamples = new ArrayList<>();
    // A queue for incoming state updates to be processed on the client thread.
    private final ConcurrentLinkedQueue<PhysicsObjectState> stateUpdateQueue = new ConcurrentLinkedQueue<>();
    // A temporary transform object to avoid repeated allocations.
    private final VxTransform tempTransform = new VxTransform();

    // Private constructor to enforce singleton pattern.
    private VxClientObjectManager() {}

    /**
     * @return The singleton instance of the {@link VxClientObjectManager}.
     */
    public static VxClientObjectManager getInstance() {
        return INSTANCE;
    }

    /**
     * Schedules a list of incoming physics states to be processed in the next client tick.
     * This method is thread-safe.
     *
     * @param states The list of states received from the server.
     */
    public void scheduleStatesForUpdate(List<PhysicsObjectState> states) {
        stateUpdateQueue.addAll(states);
    }

    /**
     * Processes all pending state updates from the queue. This should be called on the main client thread.
     */
    private void processStateUpdates() {
        PhysicsObjectState state;
        long clientReceiptTime = clock.getGameTimeNanos();
        while ((state = stateUpdateQueue.poll()) != null) {
            // Add a new sample for clock synchronization.
            synchronized (clockOffsetSamples) {
                this.clockOffsetSamples.add(state.getTimestamp() - clientReceiptTime);
            }
            updateObjectState(state);
            // Release the state object back to the pool to be reused.
            PhysicsObjectStatePool.release(state);
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
     * Updates the state buffers for a single object with a new state from the server.
     * This involves shifting the previous "to" state to the "from" state and inserting the new state.
     *
     * @param state The new {@link PhysicsObjectState} from the server.
     */
    private void updateObjectState(PhysicsObjectState state) {
        Integer index = store.getIndexForId(state.getId());
        if (index == null) return; // Object might have been removed just before the update arrived.

        // Shift state1 (the previous target state) to state0 (the new source state).
        store.state0_timestamp[index] = store.state1_timestamp[index];
        store.state0_posX[index] = store.state1_posX[index];
        store.state0_posY[index] = store.state1_posY[index];
        store.state0_posZ[index] = store.state1_posZ[index];
        store.state0_rotX[index] = store.state1_rotX[index];
        store.state0_rotY[index] = store.state1_rotY[index];
        store.state0_rotZ[index] = store.state1_rotZ[index];
        store.state0_rotW[index] = store.state1_rotW[index];
        store.state0_velX[index] = store.state1_velX[index];
        store.state0_velY[index] = store.state1_velY[index];
        store.state0_velZ[index] = store.state1_velZ[index];
        store.state0_isActive[index] = store.state1_isActive[index];
        store.state0_vertexData[index] = store.state1_vertexData[index];

        // Apply the new state data to state1 (the new target state).
        store.state1_timestamp[index] = state.getTimestamp();
        RVec3 pos = state.getTransform().getTranslation();
        store.state1_posX[index] = pos.x();
        store.state1_posY[index] = pos.y();
        store.state1_posZ[index] = pos.z();
        Quat rot = state.getTransform().getRotation();
        store.state1_rotX[index] = rot.getX();
        store.state1_rotY[index] = rot.getY();
        store.state1_rotZ[index] = rot.getZ();
        store.state1_rotW[index] = rot.getW();
        store.state1_isActive[index] = state.isActive();

        if (store.state1_isActive[index]) {
            com.github.stephengold.joltjni.Vec3 linVel = state.getLinearVelocity();
            store.state1_velX[index] = linVel.getX();
            store.state1_velY[index] = linVel.getY();
            store.state1_velZ[index] = linVel.getZ();
        } else {
            // If the body is inactive, its velocity is zero.
            store.state1_velX[index] = 0.0f;
            store.state1_velY[index] = 0.0f;
            store.state1_velZ[index] = 0.0f;
        }

        store.state1_vertexData[index] = state.getSoftBodyVertices();

        // Update the last known position for culling purposes.
        if (store.lastKnownPosition[index] == null) {
            store.lastKnownPosition[index] = new RVec3();
        }
        store.lastKnownPosition[index].set(pos);
    }

    /**
     * Spawns a new physics object on the client.
     *
     * @param id              The UUID of the new object.
     * @param typeId          The type identifier for creating the correct renderer.
     * @param objType         The body type (Rigid or Soft).
     * @param data            A buffer containing initial transform and custom creation data.
     * @param serverTimestamp The server-side timestamp of the spawn event.
     */
    public void spawnObject(UUID id, ResourceLocation typeId, EBodyType objType, FriendlyByteBuf data, long serverTimestamp) {
        if (store.hasObject(id)) return;

        int index = store.addObject(id);
        store.objectType[index] = objType;
        tempTransform.fromBuffer(data);

        RVec3 pos = tempTransform.getTranslation();
        Quat rot = tempTransform.getRotation();

        // Initialize all state buffers to the initial spawn state to prevent interpolation from a zeroed state.
        store.state0_timestamp[index] = serverTimestamp;
        store.state1_timestamp[index] = serverTimestamp;
        store.state0_posX[index] = store.state1_posX[index] = pos.x();
        store.state0_posY[index] = store.state1_posY[index] = pos.y();
        store.state0_posZ[index] = store.state1_posZ[index] = pos.z();
        store.state0_rotX[index] = store.state1_rotX[index] = rot.getX();
        store.state0_rotY[index] = store.state1_rotY[index] = rot.getY();
        store.state0_rotZ[index] = store.state1_rotZ[index] = rot.getZ();
        store.state0_rotW[index] = store.state1_rotW[index] = rot.getW();
        store.state0_velX[index] = store.state1_velX[index] = 0f;
        store.state0_velY[index] = store.state1_velY[index] = 0f;
        store.state0_velZ[index] = store.state1_velZ[index] = 0f;
        store.state0_isActive[index] = store.state1_isActive[index] = true;

        // Initialize render and previous-render states as well.
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

        // Create the renderer and the client body handle.
        VxBody.Renderer renderer = null;
        VxClientBody body;
        if (objType == EBodyType.RigidBody) {
            renderer = VxObjectRegistry.getInstance().createRigidRenderer(typeId);
            body = new VxClientRigidBody(id, this, index, objType, renderer);
        } else if (objType == EBodyType.SoftBody) {
            renderer = VxObjectRegistry.getInstance().createSoftRenderer(typeId);
            body = new VxClientSoftBody(id, this, index, objType, renderer);
            store.state0_vertexData[index] = null;
            store.state1_vertexData[index] = null;
        } else {
            VxMainClass.LOGGER.error("Client: Unknown body type for spawning: {}", objType);
            store.removeObject(id);
            return;
        }
        managedObjects.put(id, body);

        if (renderer == null) {
            VxMainClass.LOGGER.warn("Client: No renderer for body type '{}'.", typeId);
        }

        // Add an initial clock sample from the spawn packet.
        long initialOffset = serverTimestamp - clock.getGameTimeNanos();
        synchronized (clockOffsetSamples) {
            this.clockOffsetSamples.add(initialOffset);
        }
        if (!isClockOffsetInitialized) synchronizeClock();

        // Store any custom data that came with the spawn packet.
        if (data.readableBytes() > 0) {
            ByteBuffer offHeapBuffer = ByteBuffer.allocateDirect(data.readableBytes());
            data.readBytes(offHeapBuffer);
            offHeapBuffer.flip();
            store.customData[index] = offHeapBuffer;
        }
    }

    /**
     * Removes a physics object from the client.
     *
     * @param id The UUID of the object to remove.
     */
    public void removeObject(UUID id) {
        managedObjects.remove(id);
        store.removeObject(id);
    }

    /**
     * Updates the custom data for a specific object.
     *
     * @param id   The UUID of the object to update.
     * @param data The buffer containing the new custom data.
     */
    public void updateCustomObjectData(UUID id, ByteBuf data) {
        Integer index = store.getIndexForId(id);
        if (index == null) return;
        ByteBuffer offHeapBuffer = ByteBuffer.allocateDirect(data.readableBytes());
        data.readBytes(offHeapBuffer);
        offHeapBuffer.flip();
        store.customData[index] = offHeapBuffer;
    }

    /**
     * Clears all client-side physics data. Called on disconnect.
     */
    public void clearAll() {
        store.clear();
        managedObjects.clear();
        stateUpdateQueue.clear();
        isClockOffsetInitialized = false;
        clockOffsetNanos = 0L;
        synchronized(clockOffsetSamples) {
            clockOffsetSamples.clear();
        }
    }

    /**
     * The main client-side tick method.
     * Processes updates, synchronizes the clock, and runs interpolation.
     */
    public void clientTick() {
        processStateUpdates();
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
     * @return A collection view of all {@link VxClientBody} instances.
     */
    public Collection<VxClientBody> getAllObjects() {
        return managedObjects.values();
    }

    /**
     * Gets a client-side physics object handle by its UUID.
     *
     * @param id The UUID of the object.
     * @return The {@link VxClientBody} instance, or null if not currently managed.
     */
    @Nullable
    public VxClientBody getObject(UUID id) {
        return managedObjects.get(id);
    }
}