/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.manager;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.github.stephengold.joltjni.readonly.ConstBody;
import com.github.stephengold.joltjni.readonly.ConstBodyLockInterfaceNoLock;
import com.github.stephengold.joltjni.readonly.ConstSoftBodyMotionProperties;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.UUID;

/**
 * Responsible for the bi-directional synchronization between the game state
 * (in VxObjectDataStore) and the Jolt physics simulation state.
 * This class is designed to be highly performant and GC-friendly by avoiding
 * allocations in its hot-path update loops. It follows a clear two-phase update:
 * 1. Pre-Sync: Pushes game state changes into Jolt.
 * 2. Post-Sync: Pulls simulation results from Jolt back into the game state.
 *
 * @author xI-Mx-Ix
 */
public class VxPhysicsUpdater {

    private final VxObjectManager manager;
    private final VxObjectDataStore dataStore;

    // Thread-local temporary objects to avoid GC pressure in the update loop.
    private final ThreadLocal<RVec3> tempPos = ThreadLocal.withInitial(RVec3::new);
    private final ThreadLocal<Quat> tempRot = ThreadLocal.withInitial(Quat::new);
    private final ThreadLocal<Vec3> tempLinVel = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<Vec3> tempAngVel = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<BodyIdVector> localBodyIdVector = ThreadLocal.withInitial(BodyIdVector::new);

    public VxPhysicsUpdater(VxObjectManager manager) {
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
        // Game Ticks are still useful for game logic on the main thread.
        this.manager.getAllObjects().forEach(obj -> obj.gameTick(level));
    }

    /**
     * The core update loop, executed on the physics thread.
     * It performs a pre-sync (Game -> Jolt), steps the simulation (handled externally),
     * and then a post-sync (Jolt -> Game).
     */
    private void update(long timestampNanos, VxPhysicsWorld world) {
        final BodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterfaceNoLock();

        // Phase 1: Pre-Update Sync (Game State -> Jolt)
        // Pushes changes made by game logic (e.g., teleporting) into the simulation.
        preUpdateSync(bodyInterface);

        // Phase 2: Step the Physics Simulation
        // This is handled by VxPhysicsWorld's run loop, which calls physicsSystem.update().

        // Phase 3: Post-Update Sync (Jolt -> Game State)
        // Reads the results from Jolt and updates our data store for networking and game logic.
        postUpdateSync(timestampNanos, world, bodyInterface);
    }

    /**
     * Scans for objects marked as dirty by the game logic and applies their
     * state (position, rotation, velocity) to the Jolt bodies.
     * This is a fast scan over a boolean array.
     */
    private void preUpdateSync(BodyInterface bodyInterface) {
        // We iterate up to the current capacity. This is faster than iterating
        // a collection and avoids creating an iterator object (less GC).
        for (int i = 0; i < dataStore.getCapacity(); ++i) {
            if (dataStore.isGameStateDirty[i]) {
                final UUID id = dataStore.getIdForIndex(i);
                if (id == null) {
                    dataStore.isGameStateDirty[i] = false; // Clean up stale flag
                    continue;
                }

                final VxBody body = manager.getObject(id);
                if (body != null) {
                    final int bodyId = body.getBodyId();
                    if (bodyId != 0 && bodyInterface.isAdded(bodyId)) {
                        // Apply state from DataStore to Jolt Body
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
                // Reset the flag after processing
                dataStore.isGameStateDirty[i] = false;
            }
        }
    }

    /**
     * Reads the state of all *active* Jolt bodies and writes it back into the
     * data store. It marks the object as dirty for the network dispatcher. This avoids
     * expensive state-change checks in Java by simply trusting Jolt's active-body list.
     */
    private void postUpdateSync(long timestampNanos, VxPhysicsWorld world, BodyInterface bodyInterface) {
        final PhysicsSystem physicsSystem = world.getPhysicsSystem();
        final BodyIdVector activeBodiesVector = localBodyIdVector.get();
        activeBodiesVector.resize(0);

        // Get all active bodies (both rigid and soft)
        physicsSystem.getActiveBodies(EBodyType.RigidBody, activeBodiesVector);
        try (BodyIdVector activeSoftBodiesVector = new BodyIdVector()) {
            physicsSystem.getActiveBodies(EBodyType.SoftBody, activeSoftBodiesVector);
            for (int i = 0; i < activeSoftBodiesVector.size(); ++i) {
                activeBodiesVector.pushBack(activeSoftBodiesVector.get(i));
            }
        }

        final int totalActiveBodies = activeBodiesVector.size();
        if (totalActiveBodies == 0) {
            return;
        }

        for (int i = 0; i < totalActiveBodies; i++) {
            final int bodyId = activeBodiesVector.get(i);
            if (!bodyInterface.isAdded(bodyId)) continue;

            final VxBody obj = manager.getByBodyId(bodyId);
            if (obj != null) {
                obj.physicsTick(world); // Allow object-specific logic

                final int index = obj.getDataStoreIndex();
                if (index < 0) continue;

                // --- Unconditionally read from Jolt and write to DataStore ---
                // We trust that if Jolt says a body is active, its state is worth syncing.
                final RVec3 pos = tempPos.get();
                final Quat rot = tempRot.get();
                bodyInterface.getPositionAndRotation(bodyId, pos, rot);

                dataStore.posX[index] = pos.x();
                dataStore.posY[index] = pos.y();
                dataStore.posZ[index] = pos.z();
                dataStore.rotX[index] = rot.getX();
                dataStore.rotY[index] = rot.getY();
                dataStore.rotZ[index] = rot.getZ();
                dataStore.rotW[index] = rot.getW();

                final Vec3 linVel = tempLinVel.get();
                bodyInterface.getLinearVelocity(bodyId, linVel);
                dataStore.velX[index] = linVel.getX();
                dataStore.velY[index] = linVel.getY();
                dataStore.velZ[index] = linVel.getZ();

                final Vec3 angVel = tempAngVel.get();
                bodyInterface.getAngularVelocity(bodyId, angVel);
                dataStore.angVelX[index] = angVel.getX();
                dataStore.angVelY[index] = angVel.getY();
                dataStore.angVelZ[index] = angVel.getZ();

                if (obj instanceof VxSoftBody softBody) {
                    float[] newVertexData = getSoftBodyVertices(world.getBodyLockInterfaceNoLock(), bodyId, pos);
                    // Only mark as dirty if the vertex data has actually changed.
                    if (newVertexData != null && !Arrays.equals(newVertexData, dataStore.vertexData[index])) {
                        dataStore.vertexData[index] = newVertexData;
                        dataStore.isVertexDataDirty[index] = true;
                        softBody.setLastSyncedVertexData(newVertexData); // Update cache on the body
                    }
                }

                dataStore.isActive[index] = bodyInterface.isActive(bodyId);
                dataStore.lastUpdateTimestamp[index] = timestampNanos;

                // Update chunk tracking
                final long lastKey = dataStore.chunkKey[index];
                final long currentKey = new ChunkPos(SectionPos.posToSectionCoord(pos.x()), SectionPos.posToSectionCoord(pos.z())).toLong();
                if (lastKey != currentKey) {
                    manager.updateObjectTracking(obj, lastKey, currentKey);
                }

                // Mark the transform as dirty for the network dispatcher.
                // This is done for all active bodies.
                dataStore.isTransformDirty[index] = true;
            }
        }
    }

    /**
     * Extracts the vertex positions of a soft body from Jolt.
     * @param lockInterface Jolt's body lock interface.
     * @param bodyId The ID of the soft body.
     * @param bodyPosition The current position of the body's center of mass.
     * @return An array of vertex coordinates [x1, y1, z1, x2, y2, z2, ...], or null on failure.
     */
    private float @Nullable [] getSoftBodyVertices(ConstBodyLockInterfaceNoLock lockInterface, int bodyId, RVec3Arg bodyPosition) {
        try (BodyLockRead lock = new BodyLockRead(lockInterface, bodyId)) {
            if (lock.succeededAndIsInBroadPhase()) {
                ConstBody body = lock.getBody();
                if (body.isSoftBody()) {
                    ConstSoftBodyMotionProperties motionProps = (ConstSoftBodyMotionProperties) body.getMotionProperties();
                    int numVertices = motionProps.getSettings().countVertices();
                    if (numVertices > 0) {
                        int bufferSize = numVertices * 3;
                        // Use a direct buffer for efficient JNI transfer.
                        FloatBuffer vertexBuffer = Jolt.newDirectFloatBuffer(bufferSize);
                        motionProps.putVertexLocations(bodyPosition, vertexBuffer);
                        vertexBuffer.flip(); // Prepare buffer for reading
                        float[] vertexArray = new float[bufferSize];
                        vertexBuffer.get(vertexArray); // Copy data to a heap array
                        return vertexArray;
                    }
                }
            }
        }
        return null;
    }
}