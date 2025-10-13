/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.manager;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
import com.github.stephengold.joltjni.readonly.ConstBody;
import com.github.stephengold.joltjni.readonly.ConstBodyLockInterfaceNoLock;
import com.github.stephengold.joltjni.readonly.ConstSoftBodyMotionProperties;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.physics.body.type.VxBody;
import net.xmx.velthoric.physics.body.type.VxSoftBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.UUID;

/**
 * Responsible for the bi-directional synchronization between the game state
 * (in VxBodyDataStore) and the Jolt physics simulation state.
 * This class is designed to be highly performant and GC-friendly by avoiding
 * allocations in its hot-path update loops. It follows a clear two-phase update:
 * 1. Pre-Sync: Pushes game state changes into Jolt.
 * 2. Post-Sync: Pulls simulation results from Jolt back into the game state.
 *
 * @author xI-Mx-Ix
 */
public class VxPhysicsUpdater {

    private final VxBodyManager manager;
    private final VxBodyDataStore dataStore;

    // Thread-local temporary objects to avoid GC pressure in the update loop.
    private final ThreadLocal<RVec3> tempPos = ThreadLocal.withInitial(RVec3::new);
    private final ThreadLocal<Quat> tempRot = ThreadLocal.withInitial(Quat::new);
    private final ThreadLocal<Vec3> tempLinVel = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<Vec3> tempAngVel = ThreadLocal.withInitial(Vec3::new);


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
        this.manager.getAllBodies().forEach(obj -> obj.gameTick(level));
    }

    /**
     * The core update loop, executed on the physics thread.
     * It performs a pre-sync (Game -> Jolt), steps the simulation (handled externally),
     * and then a post-sync (Jolt -> Game).
     */
    private void update(long timestampNanos, VxPhysicsWorld world) {
        final BodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterfaceNoLock();

        // Phase 1: Pre-Update Sync (Game State -> Jolt)
        preUpdateSync(bodyInterface);

        // Phase 2: Step the Physics Simulation (handled externally)

        // Phase 3: Post-Update Sync (Jolt -> Game State)
        postUpdateSync(timestampNanos, world, bodyInterface);
    }

    /**
     * Scans for objects marked as dirty by the game logic and applies their
     * state to the Jolt bodies.
     */
    private void preUpdateSync(BodyInterface bodyInterface) {
        for (int i = 0; i < dataStore.getCapacity(); ++i) {
            if (dataStore.isGameStateDirty[i]) {
                final UUID id = dataStore.getIdForIndex(i);
                if (id == null) {
                    dataStore.isGameStateDirty[i] = false;
                    continue;
                }

                final VxBody body = manager.getVxBody(id);
                if (body != null) {
                    final int bodyId = body.getBodyId();
                    if (bodyId != 0 && bodyInterface.isAdded(bodyId)) {
                        final RVec3 pos = tempPos.get();
                        pos.set(dataStore.posX[i], dataStore.posY[i], dataStore.posZ[i]);
                        final Quat rot = tempRot.get();
                        rot.set(dataStore.rotX[i], dataStore.rotY[i], dataStore.rotZ[i], dataStore.rotW[i]);
                        bodyInterface.setPositionAndRotation(bodyId, pos, rot, EActivation.Activate);

                        final Vec3 linVel = tempLinVel.get();
                        linVel.set(dataStore.velX[i], dataStore.velY[i], dataStore.velZ[i]);
                        bodyInterface.setLinearVelocity(bodyId, linVel);

                        final Vec3 angVel = tempAngVel.get();
                        angVel.set(dataStore.angVelX[i], dataStore.angVelY[i], dataStore.angVelZ[i]);
                        bodyInterface.setAngularVelocity(bodyId, angVel);
                    }
                }
                dataStore.isGameStateDirty[i] = false;
            }
        }
    }

    /**
     * Reads the state of Jolt bodies and writes it back into the data store.
     * It synchronizes a body's state if it is currently active, OR if it was active
     * on the previous tick and has just become inactive. This ensures clients receive
     * a final update when a body comes to rest, keeping it visible and correctly positioned.
     */
    private void postUpdateSync(long timestampNanos, VxPhysicsWorld world, BodyInterface bodyInterface) {
        ConstBodyLockInterfaceNoLock lockInterface = world.getPhysicsSystem().getBodyLockInterfaceNoLock();

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
                obj.physicsTick(world);

                // --- Phase 4.1: Sync transform and velocities using performant BodyInterface methods ---
                final RVec3 pos = tempPos.get();
                final Quat rot = tempRot.get();
                bodyInterface.getPositionAndRotation(bodyId, pos, rot);

                dataStore.posX[i] = pos.x();
                dataStore.posY[i] = pos.y();
                dataStore.posZ[i] = pos.z();
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

                // --- Phase 4.2: Use a lock only for data not available on BodyInterface ---
                try (BodyLockRead lock = new BodyLockRead(lockInterface, bodyId)) {
                    if (lock.succeededAndIsInBroadPhase()) {
                        ConstBody body = lock.getBody();

                        // Sync World Space AABB for terrain tracking
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
                        if (obj instanceof VxSoftBody softBody) {
                            float[] newVertexData = getSoftBodyVertices(body, pos);
                            if (newVertexData != null && !Arrays.equals(newVertexData, dataStore.vertexData[i])) {
                                dataStore.vertexData[i] = newVertexData;
                                dataStore.isVertexDataDirty[i] = true;
                                softBody.setLastSyncedVertexData(newVertexData);
                            }
                        }
                    }
                }

                // --- Phase 4.3: Update management flags ---
                dataStore.isActive[i] = isJoltBodyActive;
                dataStore.lastUpdateTimestamp[i] = timestampNanos;

                final long lastKey = dataStore.chunkKey[i];
                final long currentKey = new ChunkPos(SectionPos.posToSectionCoord(pos.x()), SectionPos.posToSectionCoord(pos.z())).toLong();
                if (lastKey != currentKey) {
                    manager.getChunkManager().updateBodyTracking(obj, lastKey, currentKey);
                }

                dataStore.isTransformDirty[i] = true;
            }
        }
    }

    /**
     * Extracts the vertex positions of a soft body from Jolt.
     * @param body The locked ConstBody instance of the soft body.
     * @param bodyPosition The current position of the body's center of mass.
     * @return An array of vertex coordinates, or null on failure.
     */
    private float @Nullable [] getSoftBodyVertices(ConstBody body, RVec3Arg bodyPosition) {
        if (body.isSoftBody()) {
            ConstSoftBodyMotionProperties motionProps = (ConstSoftBodyMotionProperties) body.getMotionProperties();
            int numVertices = motionProps.getSettings().countVertices();
            if (numVertices > 0) {
                int bufferSize = numVertices * 3;
                FloatBuffer vertexBuffer = Jolt.newDirectFloatBuffer(bufferSize);
                motionProps.putVertexLocations(bodyPosition, vertexBuffer);
                vertexBuffer.flip();
                float[] vertexArray = new float[bufferSize];
                vertexBuffer.get(vertexArray);
                return vertexArray;
            }
        }
        return null;
    }
}