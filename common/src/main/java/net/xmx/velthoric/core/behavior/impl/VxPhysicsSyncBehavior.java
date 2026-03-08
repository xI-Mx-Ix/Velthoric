/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.behavior.impl;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.*;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.xmx.velthoric.core.behavior.VxBehavior;
import net.xmx.velthoric.core.behavior.VxBehaviorId;
import net.xmx.velthoric.core.behavior.VxBehaviors;
import net.xmx.velthoric.core.body.server.VxServerBodyDataStore;
import net.xmx.velthoric.core.body.server.VxServerBodyManager;
import net.xmx.velthoric.core.body.tracking.VxSpatialManager;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

/**
 * Synchronizes the native Jolt simulation results with the Java-side data store.
 * <p>
 * This behavior utilizes batch JNI calls to minimize overhead when processing large numbers
 * of bodies. It extracts transformations, velocities, activity states, and vertex data
 * for soft bodies, updating the Structure of Arrays (SoA) layout directly.
 *
 * @author xI-Mx-Ix
 */
public class VxPhysicsSyncBehavior implements VxBehavior {

    /**
     * Number of bodies processed in a single JNI batch operation.
     */
    private static final int BATCH_SIZE = 512;

    /**
     * Reusable vector for position calculations.
     */
    private final ThreadLocal<RVec3> tempPos = ThreadLocal.withInitial(RVec3::new);

    /**
     * Reusable array for collecting body IDs for batching.
     */
    private final ThreadLocal<BodyIdArray> batchBodyIds = ThreadLocal.withInitial(() -> new BodyIdArray(BATCH_SIZE));

    /**
     * Reusable list for tracking data store indices during batching.
     */
    private final ThreadLocal<IntArrayList> batchDataIndices = ThreadLocal.withInitial(() -> new IntArrayList(BATCH_SIZE));

    /**
     * Buffer for batch position retrieval.
     */
    private final ThreadLocal<DoubleBuffer> posBuffer = ThreadLocal.withInitial(() -> Jolt.newDirectDoubleBuffer(BATCH_SIZE * 3));

    /**
     * Buffer for batch rotation retrieval.
     */
    private final ThreadLocal<FloatBuffer> rotBuffer = ThreadLocal.withInitial(() -> Jolt.newDirectFloatBuffer(BATCH_SIZE * 4));

    /**
     * Buffer for batch linear velocity retrieval.
     */
    private final ThreadLocal<FloatBuffer> linVelBuffer = ThreadLocal.withInitial(() -> Jolt.newDirectFloatBuffer(BATCH_SIZE * 3));

    /**
     * Buffer for batch angular velocity retrieval.
     */
    private final ThreadLocal<FloatBuffer> angVelBuffer = ThreadLocal.withInitial(() -> Jolt.newDirectFloatBuffer(BATCH_SIZE * 3));

    /**
     * Buffer for batch activity state retrieval.
     */
    private final ThreadLocal<ByteBuffer> activeBuffer = ThreadLocal.withInitial(() -> Jolt.newDirectByteBuffer(BATCH_SIZE));

    /**
     * Buffer for batch added-state verification.
     */
    private final ThreadLocal<ByteBuffer> addedBuffer = ThreadLocal.withInitial(() -> Jolt.newDirectByteBuffer(BATCH_SIZE));

    /**
     * Buffer for batch motion type retrieval.
     */
    private final ThreadLocal<ByteBuffer> motionTypeBuffer = ThreadLocal.withInitial(() -> Jolt.newDirectByteBuffer(BATCH_SIZE));

    /**
     * Cached buffer for soft body vertex extraction.
     */
    private final ThreadLocal<FloatBuffer> softBodyBufferCache = ThreadLocal.withInitial(() -> Jolt.newDirectFloatBuffer(1024));

    @Override
    public VxBehaviorId getId() {
        return VxBehaviors.PHYSICS_SYNC;
    }

    @Override
    public void onPrePhysicsTick(VxPhysicsWorld world, VxServerBodyDataStore dataStore) {
        final VxBody[] bodies = dataStore.bodies;
        final int capacity = dataStore.getCapacity();
        long mask = getId().getMask();

        for (int i = 0; i < capacity; ++i) {
            if ((dataStore.behaviorBits[i] & mask) == 0) continue;

            VxBody obj = bodies[i];
            if (obj == null) continue;

            int bodyId = obj.getBodyId();
            if (bodyId == 0) continue;

            if (dataStore.isActive[i]) {
                obj.onPrePhysicsTick(world);
            }
        }
    }

    @Override
    public void onPhysicsTick(VxPhysicsWorld world, VxServerBodyDataStore dataStore) {
        long timestampNanos = System.nanoTime();
        final BatchBodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterfaceNoLock();
        postUpdateSync(timestampNanos, world, dataStore, bodyInterface);
    }

    /**
     * Orchestrates the batch-based synchronization process.
     *
     * @param timestampNanos Current simulation timestamp.
     * @param world          The physics world.
     * @param dataStore      The server data store.
     * @param bodyInterface  The native batch interface.
     */
    private void postUpdateSync(long timestampNanos, VxPhysicsWorld world, VxServerBodyDataStore dataStore, BatchBodyInterface bodyInterface) {
        final VxBody[] bodies = dataStore.bodies;
        final int capacity = dataStore.getCapacity();
        long mask = getId().getMask();

        BodyIdArray localBatchIds = batchBodyIds.get();
        IntArrayList localIndices = batchDataIndices.get();

        int currentBatchCount = 0;

        for (int i = 0; i < capacity; ++i) {
            if ((dataStore.behaviorBits[i] & mask) == 0) continue;

            VxBody obj = bodies[i];
            if (obj == null) continue;

            int bodyId = obj.getBodyId();
            if (bodyId == 0) continue;

            localBatchIds.set(currentBatchCount, bodyId);
            localIndices.add(i);
            currentBatchCount++;

            if (currentBatchCount == BATCH_SIZE) {
                processUpdateBatch(timestampNanos, world, dataStore, bodyInterface, currentBatchCount);
                currentBatchCount = 0;
                localIndices.clear();
            }
        }

        if (currentBatchCount > 0) {
            processUpdateBatch(timestampNanos, world, dataStore, bodyInterface, currentBatchCount);
            localIndices.clear();
        }
    }

    /**
     * Executes a single synchronization batch.
     *
     * @param timestampNanos Current timestamp.
     * @param world          The physics world.
     * @param dataStore      The server data store.
     * @param bodyInterface  The native interface.
     * @param count          Number of bodies in this batch.
     */
    private void processUpdateBatch(long timestampNanos, VxPhysicsWorld world, VxServerBodyDataStore dataStore, BatchBodyInterface bodyInterface, int count) {
        BodyIdArray ids = batchBodyIds.get();
        IntArrayList indices = batchDataIndices.get();

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
        VxServerBodyManager manager = world.getBodyManager();

        for (int b = 0; b < count; b++) {
            if (addedStates.get(b) == 0) continue;

            int i = indices.getInt(b);
            VxBody obj = bodies[i];
            if (obj == null) continue;

            boolean isJoltBodyActive = activeStates.get(b) != 0;
            boolean wasDataStoreBodyActive = dataStore.isActive[i];

            if (isJoltBodyActive || wasDataStoreBodyActive) {
                obj.onPhysicsTick(world);

                dataStore.posX[i] = positions.get(b * 3);
                dataStore.posY[i] = positions.get(b * 3 + 1);
                dataStore.posZ[i] = positions.get(b * 3 + 2);

                dataStore.rotX[i] = rotations.get(b * 4);
                dataStore.rotY[i] = rotations.get(b * 4 + 1);
                dataStore.rotZ[i] = rotations.get(b * 4 + 2);
                dataStore.rotW[i] = rotations.get(b * 4 + 3);

                dataStore.velX[i] = linVels.get(b * 3);
                dataStore.velY[i] = linVels.get(b * 3 + 1);
                dataStore.velZ[i] = linVels.get(b * 3 + 2);

                dataStore.angVelX[i] = angVels.get(b * 3);
                dataStore.angVelY[i] = angVels.get(b * 3 + 1);
                dataStore.angVelZ[i] = angVels.get(b * 3 + 2);

                dataStore.motionType[i] = EMotionType.values()[motionTypes.get(b)];
                dataStore.isActive[i] = isJoltBodyActive;
                dataStore.lastUpdateTimestamp[i] = timestampNanos;
                dataStore.isTransformDirty[i] = true;

                final long lastKey = dataStore.chunkKey[i];
                final long currentKey = VxSpatialManager.calculateChunkKey(dataStore.posX[i], dataStore.posZ[i]);

                if (lastKey != currentKey) {
                    manager.updateBodyTracking(obj, lastKey, currentKey);
                }

                int bodyId = ids.get(b);
                try (BodyLockRead lock = new BodyLockRead(lockInterface, bodyId)) {
                    ConstBody body = lock.getBody();
                    if (body != null) {
                        ConstAaBox bounds = body.getWorldSpaceBounds();
                        Vec3 min = bounds.getMin();
                        Vec3 max = bounds.getMax();

                        dataStore.aabbMinX[i] = min.getX();
                        dataStore.aabbMinY[i] = min.getY();
                        dataStore.aabbMinZ[i] = min.getZ();
                        dataStore.aabbMaxX[i] = max.getX();
                        dataStore.aabbMaxY[i] = max.getY();
                        dataStore.aabbMaxZ[i] = max.getZ();

                        if (VxBehaviors.SOFT_PHYSICS.isSet(dataStore.behaviorBits[i])) {
                            RVec3 pos = tempPos.get();
                            pos.set(dataStore.posX[i], dataStore.posY[i], dataStore.posZ[i]);
                            updateSoftBodyVertices(dataStore, body, pos, i);
                        }
                    }
                }
            }
        }
    }

    /**
     * Synchronizes soft body vertex data for deformable objects.
     *
     * @param dataStore    The server data store.
     * @param body         The native body.
     * @param bodyPosition The body's center of mass position.
     * @param dataIndex    The index in the SoA store.
     */
    private void updateSoftBodyVertices(VxServerBodyDataStore dataStore, ConstBody body, RVec3Arg bodyPosition, int dataIndex) {
        ConstSoftBodyMotionProperties motionProps = (ConstSoftBodyMotionProperties) body.getMotionProperties();
        int numVertices = motionProps.getSettings().countVertices();
        if (numVertices <= 0) return;

        int requiredFloats = numVertices * 3;

        FloatBuffer buffer = softBodyBufferCache.get();
        if (buffer.capacity() < requiredFloats) {
            buffer = Jolt.newDirectFloatBuffer(Math.max(requiredFloats, buffer.capacity() * 2));
            softBodyBufferCache.set(buffer);
        }
        buffer.clear();
        buffer.limit(requiredFloats);

        motionProps.putVertexLocations(bodyPosition, buffer);
        buffer.flip();

        float[] existing = dataStore.vertexData[dataIndex];
        boolean changed = false;

        if (existing == null || existing.length != requiredFloats) {
            existing = new float[requiredFloats];
            dataStore.vertexData[dataIndex] = existing;
            changed = true;
            buffer.get(existing);
        } else {
            for (int k = 0; k < requiredFloats; k++) {
                float newVal = buffer.get(k);
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