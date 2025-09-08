package net.xmx.velthoric.physics.object.manager;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.github.stephengold.joltjni.readonly.ConstBody;
import com.github.stephengold.joltjni.readonly.ConstBodyLockInterfaceLocking;
import com.github.stephengold.joltjni.readonly.ConstSoftBodyMotionProperties;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.SectionPos;
import net.xmx.velthoric.physics.object.VxAbstractBody;
import net.xmx.velthoric.physics.object.type.VxSoftBody;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class VxPhysicsUpdater {

    private final VxObjectManager manager;
    private final VxObjectDataStore dataStore;
    private final ConcurrentLinkedQueue<Integer> dirtyIndicesQueue;

    private static final double POSITION_THRESHOLD_SQUARED = 0.01 * 0.01;
    private static final float ROTATION_THRESHOLD = 0.99999f;
    private static final float VELOCITY_THRESHOLD_SQUARED = 0.01f * 0.01f;

    private final ThreadLocal<Vec3> tempLinVel = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<Vec3> tempAngVel = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<Quat> tempRot = ThreadLocal.withInitial(Quat::new);
    private final ThreadLocal<RVec3> tempPos = ThreadLocal.withInitial(RVec3::new);
    private final ThreadLocal<BodyIdVector> localActiveBodyIdVector = ThreadLocal.withInitial(BodyIdVector::new);

    public VxPhysicsUpdater(VxObjectManager manager, ConcurrentLinkedQueue<Integer> dirtyIndicesQueue) {
        this.manager = manager;
        this.dataStore = manager.getDataStore();
        this.dirtyIndicesQueue = dirtyIndicesQueue;
    }

    public void update(long timestampNanos, VxPhysicsWorld world) {
        final BodyInterface bodyInterface = world.getBodyInterface();
        final PhysicsSystem physicsSystem = world.getPhysicsSystem();

        BodyIdVector activeBodiesVector = localActiveBodyIdVector.get();
        physicsSystem.getActiveBodies(EBodyType.RigidBody, activeBodiesVector);

        for (int i = 0; i < activeBodiesVector.size(); i++) {
            int bodyId = activeBodiesVector.get(i);
            manager.getByBodyId(bodyId).ifPresent(obj -> {
                obj.physicsTick(world);
                updateObjectState(obj, timestampNanos, bodyInterface, world.getBodyLockInterface());
            });
        }
    }

    private void updateObjectState(VxAbstractBody obj, long timestampNanos, BodyInterface bodyInterface, ConstBodyLockInterfaceLocking lockInterface) {
        final int bodyId = obj.getBodyId();
        final int index = obj.getDataStoreIndex();
        if (index < 0) return;

        boolean isActive = bodyInterface.isActive(bodyId);

        RVec3 pos = tempPos.get();
        Quat rot = tempRot.get();

        bodyInterface.getPositionAndRotation(bodyId, pos, rot);

        long lastKey = obj.getLastKnownChunkKey();
        long currentKey = new ChunkPos(SectionPos.posToSectionCoord(pos.x()), SectionPos.posToSectionCoord(pos.z())).toLong();
        if (lastKey != currentKey) {
            manager.updateObjectTracking(obj, lastKey, currentKey);
            obj.setLastKnownChunkKey(currentKey);
        }

        obj.getGameTransform().getTranslation().set(pos.x(), pos.y(), pos.z());
        obj.getGameTransform().getRotation().set(rot);

        Vec3 linVel;
        Vec3 angVel;

        if (isActive) {
            linVel = bodyInterface.getLinearVelocity(bodyId);
            angVel = bodyInterface.getAngularVelocity(bodyId);
        } else {
            linVel = tempLinVel.get();
            linVel.loadZero();
            angVel = tempAngVel.get();
            angVel.loadZero();
        }

        float[] vertexData = null;
        if (obj instanceof VxSoftBody) {
            vertexData = getSoftBodyVertices(lockInterface, bodyId);
        }

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
            dataStore.isDirty[index] = true;
            dirtyIndicesQueue.offer(index);
        }

        if (obj.isDataDirty()) {
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

    public void clearStateFor(UUID id) {

    }

    private float @Nullable [] getSoftBodyVertices(ConstBodyLockInterfaceLocking lockInterface, int bodyId) {
        try (BodyLockRead lock = new BodyLockRead(lockInterface, bodyId)) {
            if (lock.succeededAndIsInBroadPhase()) {
                ConstBody body = lock.getBody();
                if (body != null && body.isSoftBody()) {
                    ConstSoftBodyMotionProperties motionProps = (ConstSoftBodyMotionProperties) body.getMotionProperties();
                    int numVertices = motionProps.getSettings().countVertices();
                    if (numVertices > 0) {
                        int bufferSize = numVertices * 3;
                        java.nio.FloatBuffer vertexBuffer = Jolt.newDirectFloatBuffer(bufferSize);
                        motionProps.putVertexLocations(body.getPosition(), vertexBuffer);
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