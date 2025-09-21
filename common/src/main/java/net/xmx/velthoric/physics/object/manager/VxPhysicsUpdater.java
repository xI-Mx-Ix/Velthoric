/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.object.manager;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.github.stephengold.joltjni.readonly.*;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.physics.object.type.VxBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Responsible for reading the state from the Jolt physics simulation
 * and writing it into the VxObjectDataStore. It detects changes to minimize
 * network updates.
 *
 * @author xI-Mx-Ix
 */
public class VxPhysicsUpdater {

    private final VxObjectManager manager;
    private final VxObjectDataStore dataStore;
    private final ConcurrentLinkedQueue<Integer> dirtyIndicesQueue;

    private static final double POSITION_THRESHOLD_SQUARED = 0.01 * 0.01;
    private static final float ROTATION_THRESHOLD = 0.99999f;
    private static final float VELOCITY_THRESHOLD_SQUARED = 0.01f * 0.01f;

    // Thread-local temporary objects to avoid allocations in the update loop.
    private final ThreadLocal<Quat> tempRot = ThreadLocal.withInitial(Quat::new);
    private final ThreadLocal<RVec3> tempPos = ThreadLocal.withInitial(RVec3::new);
    private final ThreadLocal<Vec3> tempLinVel = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<Vec3> tempAngVel = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<BodyIdVector> localBodyIdVector = ThreadLocal.withInitial(BodyIdVector::new);

    public VxPhysicsUpdater(VxObjectManager manager, ConcurrentLinkedQueue<Integer> dirtyIndicesQueue) {
        this.manager = manager;
        this.dataStore = manager.getDataStore();
        this.dirtyIndicesQueue = dirtyIndicesQueue;
    }

    public void onPhysicsTick(VxPhysicsWorld world) {
        this.update(System.nanoTime(), world);
    }

    public void onGameTick(ServerLevel level) {
        this.manager.getAllObjects().forEach(
                obj -> obj.gameTick(level));
    }

    /**
     * Updates the state of all active physics bodies for a single physics tick.
     * This method is expected to run on the main physics thread.
     *
     * @param timestampNanos The current time in nanoseconds.
     * @param world The physics world.
     */
    public void update(long timestampNanos, VxPhysicsWorld world) {
        final PhysicsSystem physicsSystem = world.getPhysicsSystem();
        // Use the NoLock interface for better performance, assuming this runs on the physics thread.
        final BodyInterface bodyInterface = world.getPhysicsSystem().getBodyInterfaceNoLock();

        BodyIdVector activeBodiesVector = localBodyIdVector.get();
        activeBodiesVector.resize(0);

        // Collect active rigid bodies.
        physicsSystem.getActiveBodies(EBodyType.RigidBody, activeBodiesVector);

        // Use a temporary vector to collect soft bodies and append them.
        try (BodyIdVector activeSoftBodiesVector = new BodyIdVector()) {
            physicsSystem.getActiveBodies(EBodyType.SoftBody, activeSoftBodiesVector);
            for (int i = 0; i < activeSoftBodiesVector.size(); ++i) {
                activeBodiesVector.pushBack(activeSoftBodiesVector.get(i));
            }
        }

        int totalActiveBodies = activeBodiesVector.size();
        if (totalActiveBodies == 0) {
            return;
        }

        // Iterate through all active bodies using their IDs.
        for (int i = 0; i < totalActiveBodies; i++) {
            int bodyId = activeBodiesVector.get(i);

            // A body might have been removed since the list was generated; isAdded is a safe check.
            if (!bodyInterface.isAdded(bodyId)) {
                continue;
            }

            VxBody obj = manager.getByBodyId(bodyId);
            if (obj != null) {
                obj.physicsTick(world);
                updateObjectState(obj, timestampNanos, bodyInterface, world.getBodyLockInterfaceNoLock());
            }
        }
    }

    private void updateObjectState(VxBody obj, long timestampNanos, BodyInterface bodyInterface, ConstBodyLockInterfaceNoLock lockInterface) {
        final int bodyId = obj.getBodyId();
        final int index = obj.getDataStoreIndex();
        if (index < 0) return;

        boolean isActive = bodyInterface.isActive(bodyId);

        // Reuse thread-local objects to get data without new allocations.
        RVec3 pos = tempPos.get();
        Quat rot = tempRot.get();
        bodyInterface.getPositionAndRotation(bodyId, pos, rot);

        // Update chunk tracking if the object moved across a chunk border.
        long lastKey = dataStore.chunkKey[index];
        long currentKey = new ChunkPos(SectionPos.posToSectionCoord(pos.x()), SectionPos.posToSectionCoord(pos.z())).toLong();
        if (lastKey != currentKey) {
            manager.updateObjectTracking(obj, lastKey, currentKey);
        }

        Vec3 linVel = tempLinVel.get();
        Vec3 angVel = tempAngVel.get();
        // These methods now write into the provided Vec3 objects instead of creating new ones.
        bodyInterface.getLinearVelocity(bodyId, linVel);
        bodyInterface.getAngularVelocity(bodyId, angVel);

        float[] vertexData = null;
        if (obj instanceof VxSoftBody) {
            vertexData = getSoftBodyVertices(lockInterface, bodyId, pos);
        }

        // Check if the state has changed significantly enough to warrant a network update.
        if (hasStateChanged(index, pos, rot, linVel, angVel, vertexData, isActive)) {
            dataStore.posX[index] = pos.x();
            dataStore.posY[index] = pos.y();
            dataStore.posZ[index] = pos.z();
            dataStore.rotX[index] = rot.getX();
            dataStore.rotY[index] = rot.getY();
            dataStore.rotZ[index] = rot.getZ();
            dataStore.rotW[index] = rot.getW();
            dataStore.velX[index] = linVel.getX();
            dataStore.velY[index] = linVel.getY();
            dataStore.velZ[index] = linVel.getZ();
            dataStore.angVelX[index] = angVel.getX();
            dataStore.angVelY[index] = angVel.getY();
            dataStore.angVelZ[index] = angVel.getZ();
            dataStore.vertexData[index] = vertexData;
            dataStore.isActive[index] = isActive;
            dataStore.lastUpdateTimestamp[index] = timestampNanos;

            if (!dataStore.isPhysicsStateDirty[index]) {
                dataStore.isPhysicsStateDirty[index] = true;
                dirtyIndicesQueue.offer(index);
            }
        }

        if (obj.isCustomDataDirty()) {
            manager.getNetworkDispatcher().dispatchDataUpdate(obj);
        }
    }

    private boolean hasStateChanged(int index, RVec3 pos, Quat rot, Vec3 linVel, Vec3 angVel, float[] vertices, boolean isActive) {
        if (dataStore.isActive[index] != isActive) return true;

        if (isActive) {
            if (squaredDifference(dataStore.velX[index], dataStore.velY[index], dataStore.velZ[index], linVel) > VELOCITY_THRESHOLD_SQUARED ||
                    squaredDifference(dataStore.angVelX[index], dataStore.angVelY[index], dataStore.angVelZ[index], angVel) > VELOCITY_THRESHOLD_SQUARED) {
                return true;
            }
        }

        if (squaredDifference(dataStore.posX[index], dataStore.posY[index], dataStore.posZ[index], pos) > POSITION_THRESHOLD_SQUARED) {
            return true;
        }

        float dot = dataStore.rotX[index] * rot.getX() + dataStore.rotY[index] * rot.getY() + dataStore.rotZ[index] * rot.getZ() + dataStore.rotW[index] * rot.getW();
        if (Math.abs(dot) < ROTATION_THRESHOLD) {
            return true;
        }

        return !Arrays.equals(dataStore.vertexData[index], vertices);
    }

    private float squaredDifference(float x1, float y1, float z1, Vec3 v2) {
        float dx = x1 - v2.getX();
        float dy = y1 - v2.getY();
        float dz = z1 - v2.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private float squaredDifference(float x1, float y1, float z1, RVec3 v2) {
        float dx = x1 - v2.x();
        float dy = y1 - v2.y();
        float dz = z1 - v2.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private float @Nullable [] getSoftBodyVertices(ConstBodyLockInterfaceNoLock lockInterface, int bodyId, RVec3Arg bodyPosition) {
        try (BodyLockRead lock = new BodyLockRead(lockInterface, bodyId)) {
            if (lock.succeededAndIsInBroadPhase()) {
                ConstBody body = lock.getBody();
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
            }
        }
        return null;
    }
}