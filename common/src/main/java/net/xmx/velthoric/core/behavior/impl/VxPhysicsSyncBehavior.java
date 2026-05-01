/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.core.behavior.impl;

import com.github.stephengold.joltjni.BatchBodyInterface;
import com.github.stephengold.joltjni.BodyIdArray;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.ConstBodyIdArray;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.xmx.velthoric.core.behavior.VxBehavior;
import net.xmx.velthoric.core.behavior.VxBehaviorId;
import net.xmx.velthoric.core.body.VxBody;
import net.xmx.velthoric.core.body.server.VxServerBodyDataContainer;
import net.xmx.velthoric.core.body.server.VxServerBodyDataStore;
import net.xmx.velthoric.core.body.server.VxServerBodyManager;
import net.xmx.velthoric.core.body.tracking.VxSpatialManager;
import net.xmx.velthoric.core.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.jni.BatchPhysicsSync;

/**
 * Synchronizes the native Jolt simulation results with the Java-side data store.
 * <p>
 * This behavior utilizes high-performance native batch calls to minimize JNI overhead when processing
 * large numbers of bodies. It extracts transformations, velocities, activity states, and vertex data
 * for soft bodies, updating the Structure of Arrays (SoA) layout directly in memory.
 *
 * @author xI-Mx-Ix
 */
public class VxPhysicsSyncBehavior implements VxBehavior {

    /**
     * The unique identifier for this behavior.
     * Consumed by the behavior manager for bitmask allocation and dispatch.
     */
    public static final VxBehaviorId ID = new VxBehaviorId(VxMainClass.MODID, "PhysicsSync");

    /**
     * Number of bodies processed in a single JNI batch operation.
     */
    private static final int BATCH_SIZE = 512;

    /**
     * Reusable array for collecting body IDs for batching.
     * This avoids per-tick allocations on the physics thread.
     */
    private final ThreadLocal<BodyIdArray> batchBodyIds = ThreadLocal.withInitial(() -> new BodyIdArray(BATCH_SIZE));

    /**
     * Reusable list for tracking data store indices during batching.
     * Maps the batch-local body IDs to their global SoA indices.
     */
    private final ThreadLocal<IntArrayList> batchDataIndices = ThreadLocal.withInitial(() -> new IntArrayList(BATCH_SIZE));

    /**
     * Buffer for dirty indices returned from native code.
     * Populated by the native sync function to indicate which bodies require network updates.
     */
    private final ThreadLocal<int[]> dirtyIndicesBuffer = ThreadLocal.withInitial(() -> new int[BATCH_SIZE]);

    /**
     * Buffer for motion types (ordinals) returned from native code.
     * Used to sync the mobility state (Static, Kinematic, Dynamic) from Jolt to Java.
     */
    private final ThreadLocal<byte[]> motionTypeOutputBuffer = ThreadLocal.withInitial(() -> new byte[BATCH_SIZE]);

    /**
     * Buffer for behavior bits passed to native code.
     * Allows the native side to filter logic (e.g., only processing vertices for soft bodies).
     */
    private final ThreadLocal<long[]> behaviorBitsBuffer = ThreadLocal.withInitial(() -> new long[BATCH_SIZE]);

    /**
     * Default constructor for the synchronization behavior.
     */
    public VxPhysicsSyncBehavior() {
    }

    /**
     * Retrieves the unique identifier for this behavior.
     *
     * @return The behavior ID.
     */
    @Override
    public VxBehaviorId getId() {
        return ID;
    }

    /**
     * Extracts results from the Jolt simulation and synchronizes them to the data store.
     * Called by the behavior manager during the post-simulation sync phase.
     *
     * @param world     The physics world.
     * @param dataStore The server body data store.
     */
    @Override
    public void onPhysicsTick(VxPhysicsWorld world, VxServerBodyDataStore dataStore) {
        long timestampNanos = System.nanoTime();
        final BatchBodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterfaceNoLock();
        postUpdateSync(timestampNanos, world, dataStore, bodyInterface);
    }

    /**
     * Orchestrates the batch-based synchronization process by segmenting active bodies into batches.
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
            // Only process bodies that have the PhysicsSync behavior attached.
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

        // Process remaining bodies in the final partial batch.
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
     * <p>
     * This method offloads the extraction of all physical properties (transforms, velocities,
     * AABBs, and soft body vertices) to a highly optimized native bridge.
     *
     * @param timestampNanos Current timestamp.
     * @param world          The physics world.
     * @param dataStore      The server data store.
     * @param c              The data container.
     * @param bodyInterface  The native interface.
     * @param ids            The array of body IDs to process.
     * @param count          Number of bodies in this batch.
     */
    private void processUpdateBatch(long timestampNanos, VxPhysicsWorld world, VxServerBodyDataStore dataStore, VxServerBodyDataContainer c, BatchBodyInterface bodyInterface, ConstBodyIdArray ids, int count) {
        IntArrayList indices = batchDataIndices.get();
        int[] bodyIds = new int[count];
        long[] behaviorBits = behaviorBitsBuffer.get();
        for (int b = 0; b < count; b++) {
            bodyIds[b] = ids.get(b);
            behaviorBits[b] = c.behaviorBits[indices.getInt(b)];
        }

        int[] dirtyIndices = dirtyIndicesBuffer.get();
        byte[] motionTypes = motionTypeOutputBuffer.get();

        int dirtyCount = BatchPhysicsSync.syncPhysicsNative(
                world.getPhysicsSystemPtr(),
                count,
                indices.elements(),
                bodyIds,
                behaviorBits,
                c.posX, c.posY, c.posZ,
                c.rotX, c.rotY, c.rotZ, c.rotW,
                c.velX, c.velY, c.velZ,
                c.angVelX, c.angVelY, c.angVelZ,
                c.aabbMinX, c.aabbMinY, c.aabbMinZ,
                c.aabbMaxX, c.aabbMaxY, c.aabbMaxZ,
                c.isActive,
                c.isTransformDirty,
                c.isVertexDataDirty,
                c.lastUpdateTimestamp,
                motionTypes,
                dirtyIndices,
                c.vertexData,
                VxSoftPhysicsBehavior.ID.getMask(),
                timestampNanos
        );

        if (dirtyCount > 0) {
            synchronized (dataStore) {
                for (int i = 0; i < dirtyCount; i++) {
                    c.dirtyIndices.add(dirtyIndices[i]);
                }
            }
        }

        VxServerBodyManager manager = world.getBodyManager();

        // Handle post-sync metadata updates (chunk tracking, motion types)
        for (int b = 0; b < count; b++) {
            int i = indices.getInt(b);
            if (c.isActive[i] || c.isTransformDirty[i] || c.isVertexDataDirty[i]) {
                VxBody obj = c.bodies[i];
                if (obj == null) continue;

                // Sync motion type ordinal from native to Java enum.
                c.motionType[i] = EMotionType.values()[motionTypes[b]];

                // Update spatial tracking if the body crossed a chunk boundary.
                final long lastKey = c.chunkKey[i];
                final long currentKey = VxSpatialManager.calculateChunkKey(c.posX[i], c.posZ[i]);
                if (lastKey != currentKey) {
                    manager.updateBodyTracking(obj, lastKey, currentKey);
                }
            }
        }
    }
}