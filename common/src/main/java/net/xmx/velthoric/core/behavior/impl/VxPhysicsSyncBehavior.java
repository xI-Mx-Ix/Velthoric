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
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.body.server.VxServerBodyDataStore;
import net.xmx.velthoric.core.body.server.VxServerBodyDataContainer;
import net.xmx.velthoric.core.body.server.VxServerBodyManager;
import net.xmx.velthoric.core.body.tracking.VxSpatialManager;
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
        VxServerBodyDataContainer c = dataStore.serverCurrent();
        final VxBody[] bodies = c.bodies;
        final int capacity = c.getCapacity();
        long mask = getId().getMask();

        for (int i = 0; i < capacity; ++i) {
            if ((c.behaviorBits[i] & mask) == 0) continue;

            VxBody obj = bodies[i];
            if (obj == null) continue;

            int bodyId = obj.getBodyId();
            if (bodyId == 0) continue;

            if (c.isActive[i]) {
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
        VxServerBodyDataContainer c = dataStore.serverCurrent();
        final VxBody[] bodies = c.bodies;
        final int capacity = c.getCapacity();
        long mask = getId().getMask();

        BodyIdArray localBatchIds = batchBodyIds.get();
        IntArrayList localIndices = batchDataIndices.get();

        int currentBatchCount = 0;

        for (int i = 0; i < capacity; ++i) {
            if ((c.behaviorBits[i] & mask) == 0) continue;

            VxBody obj = bodies[i];
            if (obj == null) continue;

            int bodyId = obj.getBodyId();
            if (bodyId == 0) continue;

            localBatchIds.set(currentBatchCount, bodyId);
            localIndices.add(i);
            currentBatchCount++;

            if (currentBatchCount == BATCH_SIZE) {
                processUpdateBatch(timestampNanos, world, dataStore, c, bodyInterface, localBatchIds, currentBatchCount);
                currentBatchCount = 0;
                localIndices.clear();
            }
        }

        if (currentBatchCount > 0) {
            BodyIdArray tailIds = new BodyIdArray(currentBatchCount);
            for (int j = 0; j < currentBatchCount; j++) {
                tailIds.set(j, localBatchIds.get(j));
            }
            processUpdateBatch(timestampNanos, world, dataStore, c, bodyInterface, tailIds, currentBatchCount);
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
     * @param ids            The array of body IDs to process.
     * @param count          Number of bodies in this batch.
     */
    private void processUpdateBatch(long timestampNanos, VxPhysicsWorld world, VxServerBodyDataStore dataStore, VxServerBodyDataContainer c, BatchBodyInterface bodyInterface, ConstBodyIdArray ids, int count) {
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

        final VxBody[] bodies = c.bodies;
        ConstBodyLockInterfaceNoLock lockInterface = world.getPhysicsSystem().getBodyLockInterfaceNoLock();
        VxServerBodyManager manager = world.getBodyManager();

        try (BodyLockMultiRead multiLock = new BodyLockMultiRead(lockInterface, ids)) {
            ConstBody[] lockedBodies = multiLock.getBodies();

            for (int b = 0; b < count; b++) {
                if (addedStates.get(b) == 0) continue;

                int i = indices.getInt(b);
                // Critical bounds check: ensure the index is valid for our cached container reference
                if (i >= c.getCapacity()) continue;
                VxBody obj = bodies[i];
                if (obj == null) continue;

                boolean isJoltBodyActive = activeStates.get(b) != 0;
                boolean wasDataStoreBodyActive = c.isActive[i];

                if (isJoltBodyActive || wasDataStoreBodyActive) {
                    obj.onPhysicsTick(world);

                    double nx = positions.get(b * 3);
                    double ny = positions.get(b * 3 + 1);
                    double nz = positions.get(b * 3 + 2);

                    if (isJoltBodyActive || isJoltBodyActive != wasDataStoreBodyActive) {
                        c.isTransformDirty[i] = true;
                        synchronized (dataStore) {
                            c.dirtyIndices.add(i);
                        }
                    }

                    c.posX[i] = nx;
                    c.posY[i] = ny;
                    c.posZ[i] = nz;

                    c.rotX[i] = rotations.get(b * 4);
                    c.rotY[i] = rotations.get(b * 4 + 1);
                    c.rotZ[i] = rotations.get(b * 4 + 2);
                    c.rotW[i] = rotations.get(b * 4 + 3);

                    c.velX[i] = linVels.get(b * 3);
                    c.velY[i] = linVels.get(b * 3 + 1);
                    c.velZ[i] = linVels.get(b * 3 + 2);

                    c.angVelX[i] = angVels.get(b * 3);
                    c.angVelY[i] = angVels.get(b * 3 + 1);
                    c.angVelZ[i] = angVels.get(b * 3 + 2);

                    c.motionType[i] = EMotionType.values()[motionTypes.get(b)];
                    c.isActive[i] = isJoltBodyActive;
                    c.lastUpdateTimestamp[i] = timestampNanos;

                    final long lastKey = c.chunkKey[i];
                    final long currentKey = VxSpatialManager.calculateChunkKey(c.posX[i], c.posZ[i]);

                    if (lastKey != currentKey) {
                        manager.updateBodyTracking(obj, lastKey, currentKey);
                    }

                    // Retrieve AABB from the batch-locked body reference
                    ConstBody body = lockedBodies[b];
                    if (body != null) {
                        ConstAaBox bounds = body.getWorldSpaceBounds();
                        Vec3 min = bounds.getMin();
                        Vec3 max = bounds.getMax();

                        c.aabbMinX[i] = min.getX();
                        c.aabbMinY[i] = min.getY();
                        c.aabbMinZ[i] = min.getZ();
                        c.aabbMaxX[i] = max.getX();
                        c.aabbMaxY[i] = max.getY();
                        c.aabbMaxZ[i] = max.getZ();

                        if (isJoltBodyActive && VxBehaviors.SOFT_PHYSICS.isSet(c.behaviorBits[i])) {
                            RVec3 pos = tempPos.get();
                            pos.set(c.posX[i], c.posY[i], c.posZ[i]);
                            updateSoftBodyVertices(c, dataStore, body, pos, i);
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
    private void updateSoftBodyVertices(VxServerBodyDataContainer c, VxServerBodyDataStore dataStore, ConstBody body, RVec3Arg bodyPosition, int dataIndex) {
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

        float[] existing = c.vertexData[dataIndex];
        boolean changed = false;

        if (existing == null || existing.length != requiredFloats) {
            existing = new float[requiredFloats];
            c.vertexData[dataIndex] = existing;
            changed = true;
            buffer.get(existing);
        } else {
            for (int k = 0; k < requiredFloats; k++) {
                float newVal = buffer.get(k);
                if (Math.abs(existing[k] - newVal) > 1e-4f) {
                    existing[k] = newVal;
                    changed = true;
                }
            }
        }

        if (changed) {
            c.isVertexDataDirty[dataIndex] = true;
            synchronized (dataStore) {
                c.dirtyIndices.add(dataIndex);
            }
        }
    }
}