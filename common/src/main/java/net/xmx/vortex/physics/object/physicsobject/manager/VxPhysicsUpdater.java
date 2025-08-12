package net.xmx.vortex.physics.object.physicsobject.manager;

import com.github.stephengold.joltjni.*;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VxPhysicsUpdater {

    private final VxObjectManager manager;
    private final Map<UUID, Boolean> lastActiveState = new ConcurrentHashMap<>();
    private long physicsTickCounter = 0;
    private static final int INACTIVE_OBJECT_UPDATE_INTERVAL_TICKS = 3;

    private final ThreadLocal<VxTransform> tempTransform = ThreadLocal.withInitial(VxTransform::new);
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
            Boolean previousState = lastActiveState.get(obj.getPhysicsId());
            boolean stateChanged = previousState == null || !previousState.equals(isActive);

            if (stateChanged) {
                lastActiveState.put(obj.getPhysicsId(), isActive);
            }

            if (isActive || stateChanged || isPeriodicUpdateTick) {
                objectsToUpdate.add(obj);
            }
        }

        if (!objectsToUpdate.isEmpty()) {
            List<PhysicsObjectState> statesToSend = localStatesToSend.get();
            statesToSend.clear();
            for (VxAbstractBody obj : objectsToUpdate) {
                prepareNetworkData(obj, timestampNanos, bodyInterface, world.getBodyLockInterface(), statesToSend);
                if (obj.isDataDirty()) {
                    dispatcher.dispatchDataUpdate(obj);
                }
            }
            if (!statesToSend.isEmpty()) {
                dispatcher.queueStateUpdates(statesToSend);
            }
        }
    }

    private void prepareNetworkData(VxAbstractBody obj, long timestampNanos, BodyInterface bodyInterface, BodyLockInterface lockInterface, List<PhysicsObjectState> statesToSend) {
        final int bodyId = obj.getBodyId();
        final VxTransform transform = tempTransform.get();

        boolean isActive = bodyInterface.isActive(bodyId);

        bodyInterface.getPositionAndRotation(bodyId, transform.getTranslation(), transform.getRotation());
        obj.getGameTransform().set(transform);

        Vec3 linVel = isActive ? bodyInterface.getLinearVelocity(bodyId) : null;
        Vec3 angVel = isActive ? bodyInterface.getAngularVelocity(bodyId) : null;

        float[] vertexData = null;
        if (obj instanceof VxSoftBody softBody) {
            vertexData = getSoftBodyVertices(lockInterface, bodyId);
            softBody.setLastSyncedVertexData(vertexData);
        }

        EObjectType eObjectType = obj instanceof VxSoftBody ? EObjectType.SOFT_BODY : EObjectType.RIGID_BODY;
        PhysicsObjectState state = PhysicsObjectStatePool.acquire();
        state.from(obj.getPhysicsId(), eObjectType, transform, linVel, angVel, vertexData, timestampNanos, isActive);
        statesToSend.add(state);
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
                        return vertexBuffer;
                    }
                }
            }
        }
        return null;
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