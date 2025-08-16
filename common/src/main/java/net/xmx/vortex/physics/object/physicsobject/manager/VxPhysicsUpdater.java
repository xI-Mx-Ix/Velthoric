package net.xmx.vortex.physics.object.physicsobject.manager;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import com.github.stephengold.joltjni.readonly.ConstSoftBodySharedSettings;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.EObjectType;
import net.xmx.vortex.physics.object.physicsobject.VxAbstractBody;
import net.xmx.vortex.physics.object.physicsobject.state.PhysicsObjectState;
import net.xmx.vortex.physics.object.physicsobject.state.PhysicsObjectStatePool;
import net.xmx.vortex.physics.object.physicsobject.type.soft.VxSoftBody;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VxPhysicsUpdater {

    private final VxObjectManager manager;
    private final Map<UUID, LastSentState> lastSentStates = new ConcurrentHashMap<>();
    private long physicsTickCounter = 0;
    private static final int INACTIVE_OBJECT_UPDATE_INTERVAL_TICKS = 3;

    private static final double POSITION_THRESHOLD_SQUARED = 0.01 * 0.01;

    private static final float ROTATION_THRESHOLD = 0.99999f;

    private static final float VELOCITY_THRESHOLD_SQUARED = 0.01f * 0.01f;

    private final ThreadLocal<VxTransform> tempTransform = ThreadLocal.withInitial(VxTransform::new);
    private final ThreadLocal<Vec3> tempLinVel = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<Vec3> tempAngVel = ThreadLocal.withInitial(Vec3::new);
    private final ThreadLocal<ReusableFloatBuffer> tempVertexBuffer = ThreadLocal.withInitial(ReusableFloatBuffer::new);
    private final ThreadLocal<List<VxAbstractBody>> localObjectsToUpdate = ThreadLocal.withInitial(ArrayList::new);
    private final ThreadLocal<List<PhysicsObjectState>> localStatesToSend = ThreadLocal.withInitial(ArrayList::new);

    public VxPhysicsUpdater(VxObjectManager manager) {
        this.manager = manager;
    }

    public void update(long timestampNanos, VxPhysicsWorld world) {
        final VxObjectContainer container = manager.getObjectContainer();
        final VxObjectNetworkDispatcher dispatcher = manager.getNetworkDispatcher();
        final BodyInterface bodyInterface = world.getBodyInterface();

        physicsTickCounter++;
        final boolean isPeriodicUpdateTick = (physicsTickCounter % INACTIVE_OBJECT_UPDATE_INTERVAL_TICKS == 0);

        List<VxAbstractBody> objectsToUpdate = localObjectsToUpdate.get();
        objectsToUpdate.clear();

        for (VxAbstractBody obj : container.getAllObjects()) {
            final int bodyId = obj.getBodyId();
            if (bodyId == 0 || !bodyInterface.isAdded(bodyId)) continue;

            obj.physicsTick(world);

            boolean isActive = bodyInterface.isActive(bodyId);
            LastSentState lastState = lastSentStates.get(obj.getPhysicsId());
            boolean stateChanged = (lastState == null) || (lastState.isActive != isActive);

            if (isActive || stateChanged || isPeriodicUpdateTick) {
                objectsToUpdate.add(obj);
            }
        }

        if (!objectsToUpdate.isEmpty()) {
            List<PhysicsObjectState> statesToSend = localStatesToSend.get();
            statesToSend.clear();
            for (VxAbstractBody obj : objectsToUpdate) {
                prepareAndQueueStateUpdate(obj, timestampNanos, bodyInterface, world.getBodyLockInterface(), statesToSend);
                if (obj.isDataDirty()) {
                    dispatcher.dispatchDataUpdate(obj);
                }
            }
            if (!statesToSend.isEmpty()) {
                dispatcher.queueStateUpdates(statesToSend);
            }
        }
    }

    private void prepareAndQueueStateUpdate(VxAbstractBody obj, long timestampNanos, BodyInterface bodyInterface, BodyLockInterface lockInterface, List<PhysicsObjectState> statesToSend) {
        final int bodyId = obj.getBodyId();
        final UUID id = obj.getPhysicsId();
        final VxTransform currentTransform = tempTransform.get();

        boolean isActive = bodyInterface.isActive(bodyId);
        bodyInterface.getPositionAndRotation(bodyId, currentTransform.getTranslation(), currentTransform.getRotation());
        obj.getGameTransform().set(currentTransform);

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
        if (obj instanceof VxSoftBody softBody) {
            vertexData = getSoftBodyVertices(lockInterface, bodyId);
        }

        LastSentState lastState = lastSentStates.get(id);

        if (hasStateChanged(lastState, currentTransform, linVel, angVel, vertexData, isActive)) {
            if (lastState == null) {
                lastState = new LastSentState();
                lastSentStates.put(id, lastState);
            }
            lastState.update(currentTransform, linVel, angVel, vertexData, isActive);
            if (obj instanceof VxSoftBody softBody && vertexData != null) {
                softBody.setLastSyncedVertexData(Arrays.copyOf(vertexData, vertexData.length));
            }

            EObjectType eObjectType = obj instanceof VxSoftBody ? EObjectType.SOFT_BODY : EObjectType.RIGID_BODY;
            PhysicsObjectState state = PhysicsObjectStatePool.acquire();
            state.from(id, eObjectType, currentTransform, linVel, angVel, vertexData, timestampNanos, isActive);
            statesToSend.add(state);
        }
    }

    private boolean hasStateChanged(LastSentState lastState, VxTransform currentTransform, Vec3 currentLinVel, Vec3 currentAngVel, float[] currentVertices, boolean isActive) {
        if (lastState == null || lastState.isActive != isActive) {
            return true;
        }

        if (isActive) {

            if (Op.minus(lastState.linearVelocity, currentLinVel).lengthSq() > VELOCITY_THRESHOLD_SQUARED ||
                    Op.minus(lastState.angularVelocity, currentAngVel).lengthSq() > VELOCITY_THRESHOLD_SQUARED) {
                return true;
            }

        }

        if (Op.minus(lastState.transform.getTranslation(), currentTransform.getTranslation()).lengthSq() > POSITION_THRESHOLD_SQUARED) {
            return true;
        }

        Quat q1 = lastState.transform.getRotation();
        Quat q2 = currentTransform.getRotation();
        float dot = q1.getX() * q2.getX() + q1.getY() * q2.getY() + q1.getZ() * q2.getZ() + q1.getW() * q2.getW();
        if (Math.abs(dot) < ROTATION_THRESHOLD) {
            return true;
        }

        if (!Arrays.equals(lastState.vertexData, currentVertices)) {
            return true;
        }

        return false;
    }

    public void clearStateFor(UUID id) {
        lastSentStates.remove(id);
    }

    private float @Nullable [] getSoftBodyVertices(BodyLockInterface lockInterface, int bodyId) {
        try (BodyLockRead lock = new BodyLockRead(lockInterface, bodyId)) {
            if (lock.succeededAndIsInBroadPhase()) {
                Body body = lock.getBody();
                if (body != null && body.isSoftBody()) {
                    SoftBodyMotionProperties motionProps = (SoftBodyMotionProperties) body.getMotionProperties();
                    ConstSoftBodySharedSettings sharedSettings = motionProps.getSettings();
                    int numVertices = sharedSettings.countVertices();
                    if (numVertices > 0) {
                        float[] vertexBuffer = tempVertexBuffer.get().getBuffer(numVertices * 3);
                        RMat44 worldTransform = body.getWorldTransform();
                        for (int i = 0; i < numVertices; i++) {
                            SoftBodyVertex vertex = motionProps.getVertex(i);
                            if (vertex == null) continue;
                            Vec3 localPos = vertex.getPosition();
                            RVec3 worldPos = worldTransform.multiply3x4(localPos);
                            int baseIndex = i * 3;
                            vertexBuffer[baseIndex] = worldPos.x();
                            vertexBuffer[baseIndex + 1] = worldPos.y();
                            vertexBuffer[baseIndex + 2] = worldPos.z();
                        }

                        return Arrays.copyOf(vertexBuffer, numVertices * 3);
                    }
                }
            }
        }
        return null;
    }

    private static class LastSentState {
        final VxTransform transform = new VxTransform();
        final Vec3 linearVelocity = new Vec3();
        final Vec3 angularVelocity = new Vec3();
        @Nullable
        float[] vertexData;
        boolean isActive;

        void update(VxTransform transform, Vec3 linVel, Vec3 angVel, @Nullable float[] vertices, boolean isActive) {
            this.transform.set(transform);
            this.linearVelocity.set(linVel);
            this.angularVelocity.set(angVel);
            this.isActive = isActive;

            if (vertices != null) {
                if (this.vertexData == null || this.vertexData.length != vertices.length) {
                    this.vertexData = new float[vertices.length];
                }
                System.arraycopy(vertices, 0, this.vertexData, 0, vertices.length);
            } else {
                this.vertexData = null;
            }
        }
    }

    private static class ReusableFloatBuffer {
        private float[] buffer = new float[0];

        float[] getBuffer(int requiredSize) {
            if (buffer.length < requiredSize) {
                buffer = new float[requiredSize];
            }
            return buffer;
        }
    }
}