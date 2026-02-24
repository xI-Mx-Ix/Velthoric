/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.body.server;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.*;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.server.level.ServerLevel;
import net.xmx.velthoric.core.body.tracking.VxSpatialManager;
import net.xmx.velthoric.core.body.type.VxBody;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

/**
 * Responsible for the synchronization between the Jolt physics simulation and
 * the game state (in VxServerBodyDataStore).
 * <p>
 * This class is designed to be highly performant and GC-friendly by avoiding
 * allocations in its hot-path update loops. It performs a post-simulation sync
 * to update the game state with the results from the physics engine using
 * batch operations to minimize JNI overhead.
 *
 * @author xI-Mx-Ix
 */
public class VxPhysicsExtractor {

    private final VxServerBodyManager manager;
    private final VxServerBodyDataStore dataStore;

    /**
     * The maximum number of bodies processed in a single JNI batch call.
     */
    private static final int BATCH_SIZE = 512;

    // Thread-local temporary objects to avoid GC pressure in the update loop.
    private final ThreadLocal<RVec3> tempPos = ThreadLocal.withInitial(RVec3::new);

    // Reusable batch containers to avoid reallocation.
    private final ThreadLocal<BodyIdArray> batchBodyIds = ThreadLocal.withInitial(() -> new BodyIdArray(BATCH_SIZE));
    private final ThreadLocal<IntArrayList> batchDataIndices = ThreadLocal.withInitial(() -> new IntArrayList(BATCH_SIZE));

    // Reusable direct buffers for batch JNI operations.
    private final ThreadLocal<DoubleBuffer> posBuffer = ThreadLocal.withInitial(() -> Jolt.newDirectDoubleBuffer(BATCH_SIZE * 3));
    private final ThreadLocal<FloatBuffer> rotBuffer = ThreadLocal.withInitial(() -> Jolt.newDirectFloatBuffer(BATCH_SIZE * 4));
    private final ThreadLocal<FloatBuffer> linVelBuffer = ThreadLocal.withInitial(() -> Jolt.newDirectFloatBuffer(BATCH_SIZE * 3));
    private final ThreadLocal<FloatBuffer> angVelBuffer = ThreadLocal.withInitial(() -> Jolt.newDirectFloatBuffer(BATCH_SIZE * 3));
    private final ThreadLocal<ByteBuffer> activeBuffer = ThreadLocal.withInitial(() -> Jolt.newDirectByteBuffer(BATCH_SIZE));
    private final ThreadLocal<ByteBuffer> addedBuffer = ThreadLocal.withInitial(() -> Jolt.newDirectByteBuffer(BATCH_SIZE));
    private final ThreadLocal<ByteBuffer> motionTypeBuffer = ThreadLocal.withInitial(() -> Jolt.newDirectByteBuffer(BATCH_SIZE));

    // Reusable direct buffer for native soft body operations to prevent allocation every tick.
    private final ThreadLocal<FloatBuffer> softBodyBufferCache = ThreadLocal.withInitial(() -> Jolt.newDirectFloatBuffer(1024));

    public VxPhysicsExtractor(VxServerBodyManager manager) {
        this.manager = manager;
        this.dataStore = manager.getDataStore();
    }

    /**
     * Entry point called from the main physics thread loop for each simulation step.
     * @param world The physics world instance.
     */
    public void onPhysicsTick(VxPhysicsWorld world) {
        this.update(System.nanoTime(), world);
    }

    /**
     * Entry point called from the main server thread for each game tick.
     * @param level The server level.
     */
    public void onGameTick(ServerLevel level) {
        this.manager.getAllBodies().forEach(obj -> obj.onServerTick(level));
    }

    /**
     * The core update loop, executed on the physics thread.
     * It performs a post-sync (Jolt -> Game State) after the simulation step.
     */
    private void update(long timestampNanos, VxPhysicsWorld world) {
        final BatchBodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterfaceNoLock();

        // Post-Update Sync: Retrieve simulation results and update the SoA DataStore.
        postUpdateSync(timestampNanos, world, bodyInterface);
    }

    /**
     * Reads the state of Jolt bodies and writes it back into the data store.
     * <p>
     * This method operates in two passes:
     * <ol>
     *     <li><b>Batch Pass:</b> Syncs Position, Rotation, and Velocity using the {@link BatchBodyInterface}.</li>
     *     <li><b>Locked Pass:</b> Syncs AABB and SoftBody vertices. This uses sequential locking to ensure
     *     stability across different JNI implementations and platforms.</li>
     * </ol>
     *
     * @param timestampNanos The current timestamp for interpolation tracking.
     * @param world          The physics world instance.
     * @param bodyInterface  The Jolt batch body interface for high-performance reads.
     */
    private void postUpdateSync(long timestampNanos, VxPhysicsWorld world, BatchBodyInterface bodyInterface) {
        final VxBody[] bodies = dataStore.bodies;
        final int capacity = dataStore.getCapacity();

        BodyIdArray localBatchIds = batchBodyIds.get();
        IntArrayList localIndices = batchDataIndices.get();

        int currentBatchCount = 0;

        for (int i = 0; i < capacity; ++i) {
            VxBody obj = bodies[i];
            if (obj == null) continue;

            int bodyId = obj.getBodyId();
            if (bodyId == 0) continue;

            localBatchIds.set(currentBatchCount, bodyId);
            localIndices.add(i);
            currentBatchCount++;

            // If the batch is full, process it immediately.
            if (currentBatchCount == BATCH_SIZE) {
                processBatch(timestampNanos, world, bodyInterface, currentBatchCount);
                currentBatchCount = 0;
                localIndices.clear();
            }
        }

        // Process remaining bodies in the final batch.
        if (currentBatchCount > 0) {
            processBatch(timestampNanos, world, bodyInterface, currentBatchCount);
            localIndices.clear();
        }
    }

    /**
     * Processes a single batch of bodies by invoking Jolt batch operations and updating the DataStore.
     *
     * @param timestampNanos The current simulation timestamp.
     * @param world          The physics world instance.
     * @param bodyInterface  The Jolt batch body interface.
     * @param count          The number of bodies in the current batch.
     */
    private void processBatch(long timestampNanos, VxPhysicsWorld world, BatchBodyInterface bodyInterface, int count) {
        BodyIdArray ids = batchBodyIds.get();
        IntArrayList indices = batchDataIndices.get();

        // Prepare direct buffers for batch retrieval.
        ByteBuffer addedStates = addedBuffer.get();
        addedStates.clear();
        bodyInterface.areAdded(ids, addedStates);

        ByteBuffer activeStates = activeBuffer.get();
        activeStates.clear();
        bodyInterface.areActive(ids, activeStates);

        DoubleBuffer positions = posBuffer.get();
        positions.clear();
        bodyInterface.getPositions(ids, positions);

        FloatBuffer rotations = rotBuffer.get();
        rotations.clear();
        bodyInterface.getRotations(ids, rotations);

        FloatBuffer linVels = linVelBuffer.get();
        linVels.clear();
        bodyInterface.getLinearVelocities(ids, linVels);

        FloatBuffer angVels = angVelBuffer.get();
        angVels.clear();
        bodyInterface.getAngularVelocities(ids, angVels);

        ByteBuffer motionTypes = motionTypeBuffer.get();
        motionTypes.clear();
        bodyInterface.getMotionTypes(ids, motionTypes);

        final VxBody[] bodies = dataStore.bodies;
        ConstBodyLockInterfaceNoLock lockInterface = world.getPhysicsSystem().getBodyLockInterfaceNoLock();

        for (int b = 0; b < count; b++) {
            if (addedStates.get(b) == 0) continue;

            int i = indices.getInt(b);
            VxBody obj = bodies[i];
            if (obj == null) continue;

            boolean isJoltBodyActive = activeStates.get(b) != 0;
            boolean wasDataStoreBodyActive = dataStore.isActive[i];

            if (isJoltBodyActive || wasDataStoreBodyActive) {
                obj.onPhysicsTick(world);

                // Update Position.
                dataStore.posX[i] = positions.get(b * 3);
                dataStore.posY[i] = positions.get(b * 3 + 1);
                dataStore.posZ[i] = positions.get(b * 3 + 2);

                // Update Rotation.
                dataStore.rotX[i] = rotations.get(b * 4);
                dataStore.rotY[i] = rotations.get(b * 4 + 1);
                dataStore.rotZ[i] = rotations.get(b * 4 + 2);
                dataStore.rotW[i] = rotations.get(b * 4 + 3);

                // Update Linear Velocity.
                dataStore.velX[i] = linVels.get(b * 3);
                dataStore.velY[i] = linVels.get(b * 3 + 1);
                dataStore.velZ[i] = linVels.get(b * 3 + 2);

                // Update Angular Velocity.
                dataStore.angVelX[i] = angVels.get(b * 3);
                dataStore.angVelY[i] = angVels.get(b * 3 + 1);
                dataStore.angVelZ[i] = angVels.get(b * 3 + 2);

                // Update Motion Type.
                dataStore.motionType[i] = EMotionType.values()[motionTypes.get(b)];

                // Update management flags.
                dataStore.isActive[i] = isJoltBodyActive;
                dataStore.lastUpdateTimestamp[i] = timestampNanos;
                dataStore.isTransformDirty[i] = true;

                // --- Spatial Tracking Update ---
                final long lastKey = dataStore.chunkKey[i];
                final long currentKey = VxSpatialManager.calculateChunkKey(dataStore.posX[i], dataStore.posZ[i]);

                if (lastKey != currentKey) {
                    manager.updateBodyTracking(obj, lastKey, currentKey);
                }

                // --- Pass 2: Locked data sync (AABB & SoftBody) ---
                int bodyId = ids.get(b);
                try (BodyLockRead lock = new BodyLockRead(lockInterface, bodyId)) {
                    ConstBody body = lock.getBody();
                    if (body != null) {
                        // Sync World Space AABB.
                        ConstAaBox bounds = body.getWorldSpaceBounds();
                        Vec3 min = bounds.getMin();
                        Vec3 max = bounds.getMax();

                        dataStore.aabbMinX[i] = min.getX();
                        dataStore.aabbMinY[i] = min.getY();
                        dataStore.aabbMinZ[i] = min.getZ();
                        dataStore.aabbMaxX[i] = max.getX();
                        dataStore.aabbMaxY[i] = max.getY();
                        dataStore.aabbMaxZ[i] = max.getZ();

                        // Sync soft body vertices if applicable.
                        if (dataStore.bodyType[i] == EBodyType.SoftBody) {
                            RVec3 pos = tempPos.get();
                            pos.set(dataStore.posX[i], dataStore.posY[i], dataStore.posZ[i]);
                            updateSoftBodyVertices(body, pos, i);
                        }
                    }
                }
            }
        }
    }

    /**
     * Efficiently extracts vertex positions for a soft body.
     * This method reuses a ThreadLocal direct FloatBuffer to avoid allocating new memory each tick.
     * It also checks if the vertices have actually changed before updating the array in the store
     * to minimize downstream processing and network syncs.
     *
     * @param body The locked ConstBody instance of the soft body.
     * @param bodyPosition The current position of the body's center of mass.
     * @param dataIndex The index of the body in the DataStore.
     */
    private void updateSoftBodyVertices(ConstBody body, RVec3Arg bodyPosition, int dataIndex) {
        ConstSoftBodyMotionProperties motionProps = (ConstSoftBodyMotionProperties) body.getMotionProperties();
        int numVertices = motionProps.getSettings().countVertices();
        if (numVertices <= 0) return;

        int requiredFloats = numVertices * 3;

        // 1. Get or resize the reusable direct buffer
        FloatBuffer buffer = softBodyBufferCache.get();
        if (buffer.capacity() < requiredFloats) {
            // Grow buffer with some headroom
            buffer = Jolt.newDirectFloatBuffer(Math.max(requiredFloats, buffer.capacity() * 2));
            softBodyBufferCache.set(buffer);
        }
        buffer.clear();
        buffer.limit(requiredFloats);

        // 2. Fetch data from Jolt into the reused buffer (Native -> Direct Buffer)
        motionProps.putVertexLocations(bodyPosition, buffer);
        buffer.flip(); // Prepare for reading

        // 3. Compare with existing array in DataStore to detect changes
        float[] existing = dataStore.vertexData[dataIndex];
        boolean changed = false;

        if (existing == null || existing.length != requiredFloats) {
            // Initial allocation or size change requires a new array
            existing = new float[requiredFloats];
            dataStore.vertexData[dataIndex] = existing;
            changed = true;
            buffer.get(existing); // Copy all data
        } else {
            // Check for differences without allocating a new array
            for (int k = 0; k < requiredFloats; k++) {
                float newVal = buffer.get(k);
                // Use a small epsilon for float comparison to avoid noise updates
                if (Math.abs(existing[k] - newVal) > 1e-6f) {
                    existing[k] = newVal;
                    changed = true;
                }
            }
        }

        if (changed) {
            dataStore.isVertexDataDirty[dataIndex] = true;
        }
    }
}