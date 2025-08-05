package net.xmx.vortex.physics.object.physicsobject.manager;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.readonly.ConstSoftBodySharedSettings;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.EObjectType;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.state.PhysicsObjectState;
import net.xmx.vortex.physics.object.physicsobject.state.PhysicsObjectStatePool;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;

public class VxPhysicsUpdater {

    private final VxObjectManager manager;
    private final Map<UUID, Boolean> lastActiveState = new ConcurrentHashMap<>();
    private long physicsTickCounter = 0;
    private static final int INACTIVE_OBJECT_UPDATE_INTERVAL_TICKS = 3;

    private final ThreadLocal<VxTransform> tempTransform = ThreadLocal.withInitial(VxTransform::new);
    private final ThreadLocal<ReusableFloatBuffer> tempVertexBuffer = ThreadLocal.withInitial(ReusableFloatBuffer::new);
    private final ThreadLocal<List<IPhysicsObject>> localObjectsToUpdate = ThreadLocal.withInitial(ArrayList::new);
    private final ThreadLocal<List<PhysicsObjectState>> localStatesToSend = ThreadLocal.withInitial(ArrayList::new);

    public VxPhysicsUpdater(VxObjectManager manager) {
        this.manager = manager;
    }

    public void update(long timestampNanos, BodyLockInterface lockInterface) {
        final VxObjectContainer container = manager.getObjectContainer();
        final VxObjectNetworkDispatcher dispatcher = manager.getNetworkDispatcher();
        final BodyInterface bodyInterfaceNoLock = manager.getWorld().getPhysicsSystem().getBodyInterfaceNoLock();

        physicsTickCounter++;
        final boolean isPeriodicUpdateTick = (physicsTickCounter % INACTIVE_OBJECT_UPDATE_INTERVAL_TICKS == 0);

        List<IPhysicsObject> objectsToUpdate = localObjectsToUpdate.get();
        objectsToUpdate.clear();

        for (IPhysicsObject obj : container.getAllObjects()) {
            if (obj == null || obj.isRemoved()) continue;
            final int bodyId = obj.getBodyId();
            if (bodyId == 0 || !bodyInterfaceNoLock.isAdded(bodyId)) continue;

            obj.fixedPhysicsTick(manager.getWorld());
            obj.physicsTick(manager.getWorld());

            boolean isActive = bodyInterfaceNoLock.isActive(bodyId);
            Boolean previousState = lastActiveState.get(obj.getPhysicsId());
            boolean stateChanged = previousState == null || previousState != isActive;

            if (stateChanged) {
                lastActiveState.put(obj.getPhysicsId(), isActive);
            }

            if (isActive || stateChanged || obj.isDataDirty() || isPeriodicUpdateTick) {
                objectsToUpdate.add(obj);
            }
        }

        if (!objectsToUpdate.isEmpty()) {
            List<PhysicsObjectState> statesToSend = localStatesToSend.get();
            statesToSend.clear();
            for (IPhysicsObject obj : objectsToUpdate) {
                updateObjectState(obj, timestampNanos, bodyInterfaceNoLock, lockInterface, statesToSend);
            }
            if (!statesToSend.isEmpty()) {
                dispatcher.dispatchStateUpdates(new ArrayList<>(statesToSend));
            }
        }
    }

    private void updateObjectState(IPhysicsObject obj, long timestampNanos, BodyInterface bodyInterfaceNoLock, BodyLockInterface lockInterface, List<PhysicsObjectState> statesToSend) {
        final int bodyId = obj.getBodyId();
        final VxTransform transform = tempTransform.get();
        Vec3 linVel = null;
        Vec3 angVel = null;
        float[] vertexData = null;
        boolean isActive = bodyInterfaceNoLock.isActive(bodyId);

        final StampedLock transformLock = obj.getTransformLock();
        long stamp = transformLock.writeLock();
        try {
            bodyInterfaceNoLock.getPositionAndRotation(bodyId, transform.getTranslation(), transform.getRotation());
            if (isActive) {
                linVel = bodyInterfaceNoLock.getLinearVelocity(bodyId);
                angVel = bodyInterfaceNoLock.getAngularVelocity(bodyId);

                if (obj.getEObjectType() == EObjectType.SOFT_BODY) {
                    vertexData = getSoftBodyVertices(lockInterface, bodyId);
                }
            }
            obj.updateStateFromPhysicsThread(timestampNanos, transform, linVel, angVel, vertexData, isActive);
        } finally {
            transformLock.unlockWrite(stamp);
        }

        PhysicsObjectState state = PhysicsObjectStatePool.acquire();
        state.from(obj, timestampNanos, isActive);
        statesToSend.add(state);
    }

    @Nullable
    private float[] getSoftBodyVertices(BodyLockInterface lockInterface, int bodyId) {
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