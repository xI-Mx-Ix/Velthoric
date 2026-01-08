/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.manager;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
import com.github.stephengold.joltjni.readonly.ConstBody;
import com.github.stephengold.joltjni.readonly.ConstBodyLockInterfaceNoLock;
import com.github.stephengold.joltjni.readonly.ConstSoftBodyMotionProperties;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Responsible for the synchronization between the Jolt physics simulation and
 * the game state (in VxServerBodyDataStore).
 * <p>
 * This class is designed to be highly performant and GC-friendly by avoiding
 * allocations in its hot-path update loops. It performs a post-simulation sync
 * to update the game state with the results from the physics engine.
 *
 * @author xI-Mx-Ix
 */
public class VxPhysicsUpdater {

    private final VxBodyManager manager;
    private final VxServerBodyDataStore dataStore;

    // Thread-local temporary objects to avoid GC pressure in the update loop.
    private final ThreadLocal<RVec3> tempPos = ThreadLocal.withInitial(RVec3::new);
    private final ThreadLocal<Quat> tempRot = ThreadLocal.withInitial(Quat::new);
    private final ThreadLocal<Vec3> tempLinVel = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<Vec3> tempAngVel = ThreadLocal.withInitial(Vec3::new);

    // Reusable lists for batch processing to avoid reallocation.
    private final ThreadLocal<List<Integer>> bodyIdsToLock = ThreadLocal.withInitial(ArrayList::new);
    private final ThreadLocal<List<Integer>> dataIndicesToLock = ThreadLocal.withInitial(ArrayList::new);

    // Reusable direct buffer for native soft body operations to prevent allocation every tick.
    private final ThreadLocal<FloatBuffer> softBodyBufferCache = ThreadLocal.withInitial(() -> Jolt.newDirectFloatBuffer(1024));

    public VxPhysicsUpdater(VxBodyManager manager) {
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
        final BodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterfaceNoLock();

        // Post-Update Sync: Retrieve simulation results and update the SoA DataStore.
        postUpdateSync(timestampNanos, world, bodyInterface);
    }

    /**
     * Reads the state of Jolt bodies and writes it back into the data store.
     * This is heavily optimized to run in two passes:
     * 1. A lock-free pass using BodyInterface for common data (transform, velocity, motion type).
     *    It collects bodies that need further data (AABB, soft body vertices).
     * 2. A single multi-lock pass to efficiently retrieve the remaining data for the collected bodies.
     */
    private void postUpdateSync(long timestampNanos, VxPhysicsWorld world, BodyInterface bodyInterface) {
        List<Integer> localBodyIdsToLock = this.bodyIdsToLock.get();
        List<Integer> localDataIndicesToLock = this.dataIndicesToLock.get();
        localBodyIdsToLock.clear();
        localDataIndicesToLock.clear();

        // --- Pass 1: Lock-free synchronization using BodyInterface ---
        for (int i = 0; i < dataStore.getCapacity(); ++i) {
            UUID id = dataStore.getIdForIndex(i);
            if (id == null) continue;

            VxBody obj = manager.getVxBody(id);
            if (obj == null) continue;

            int bodyId = obj.getBodyId();
            if (bodyId == 0 || !bodyInterface.isAdded(bodyId)) continue;

            boolean isJoltBodyActive = bodyInterface.isActive(bodyId);
            boolean wasDataStoreBodyActive = dataStore.isActive[i];

            if (isJoltBodyActive || wasDataStoreBodyActive) {
                obj.onPhysicsTick(world);

                // Sync transform, velocities, and motion type using performant BodyInterface methods
                final RVec3 pos = tempPos.get();
                final Quat rot = tempRot.get();
                bodyInterface.getPositionAndRotation(bodyId, pos, rot);
                dataStore.posX[i] = pos.xx();
                dataStore.posY[i] = pos.yy();
                dataStore.posZ[i] = pos.zz();

                dataStore.rotX[i] = rot.getX();
                dataStore.rotY[i] = rot.getY();
                dataStore.rotZ[i] = rot.getZ();
                dataStore.rotW[i] = rot.getW();

                final Vec3 linVel = tempLinVel.get();
                bodyInterface.getLinearVelocity(bodyId, linVel);
                dataStore.velX[i] = linVel.getX();
                dataStore.velY[i] = linVel.getY();
                dataStore.velZ[i] = linVel.getZ();

                final Vec3 angVel = tempAngVel.get();
                bodyInterface.getAngularVelocity(bodyId, angVel);
                dataStore.angVelX[i] = angVel.getX();
                dataStore.angVelY[i] = angVel.getY();
                dataStore.angVelZ[i] = angVel.getZ();

                dataStore.motionType[i] = bodyInterface.getMotionType(bodyId);

                // Collect this body to be included in the multi-lock for AABB and soft body data.
                localBodyIdsToLock.add(bodyId);
                localDataIndicesToLock.add(i);

                // Update management flags
                dataStore.isActive[i] = isJoltBodyActive;
                dataStore.lastUpdateTimestamp[i] = timestampNanos;
                dataStore.isTransformDirty[i] = true;

                final long lastKey = dataStore.chunkKey[i];
                final long currentKey = new ChunkPos(SectionPos.posToSectionCoord(pos.xx()), SectionPos.posToSectionCoord(pos.zz())).toLong();
                if (lastKey != currentKey) {
                    manager.getChunkManager().updateBodyTracking(obj, lastKey, currentKey);
                    dataStore.chunkKey[i] = currentKey;
                }
            }
        }

        // --- Pass 2: Efficiently sync remaining data using a single multi-lock ---
        if (!localBodyIdsToLock.isEmpty()) {
            ConstBodyLockInterfaceNoLock lockInterface = world.getPhysicsSystem().getBodyLockInterfaceNoLock();

            // Create array primitive for Jolt API
            int[] bodyIdArray = new int[localBodyIdsToLock.size()];
            for (int j = 0; j < localBodyIdsToLock.size(); j++) {
                bodyIdArray[j] = localBodyIdsToLock.get(j);
            }

            try (BodyLockMultiRead multiLock = new BodyLockMultiRead(lockInterface, bodyIdArray)) {
                for (int j = 0; j < bodyIdArray.length; ++j) {
                    ConstBody body = multiLock.getBody(j);
                    if (body != null) { // A null check is needed as multi-lock can fail for individual bodies
                        int i = localDataIndicesToLock.get(j);

                        // Check if the body was removed between pass 1 and 2
                        UUID id = dataStore.getIdForIndex(i);
                        if (id == null) continue;

                        // Sync World Space AABB
                        ConstAaBox bounds = body.getWorldSpaceBounds();
                        Vec3 min = bounds.getMin();
                        Vec3 max = bounds.getMax();
                        dataStore.aabbMinX[i] = min.getX();
                        dataStore.aabbMinY[i] = min.getY();
                        dataStore.aabbMinZ[i] = min.getZ();
                        dataStore.aabbMaxX[i] = max.getX();
                        dataStore.aabbMaxY[i] = max.getY();
                        dataStore.aabbMaxZ[i] = max.getZ();

                        // Sync soft body vertices if applicable
                        if (dataStore.bodyType[i] == com.github.stephengold.joltjni.enumerate.EBodyType.SoftBody) {
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