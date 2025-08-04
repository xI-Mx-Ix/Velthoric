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
    private final ThreadLocal<VxTransform> tempTransform = ThreadLocal.withInitial(VxTransform::new);
    private long physicsTickCounter = 0;
    private static final int INACTIVE_OBJECT_UPDATE_INTERVAL_TICKS = 3;

    public VxPhysicsUpdater(VxObjectManager manager) {
        this.manager = manager;
    }

    public void update(long timestampNanos, BodyLockInterface lockInterface) {
        VxObjectContainer container = manager.getObjectContainer();
        VxObjectNetworkDispatcher dispatcher = manager.getNetworkDispatcher();
        BodyInterface bodyInterfaceNoLock = manager.getWorld().getPhysicsSystem().getBodyInterfaceNoLock();

        physicsTickCounter++;
        final boolean isPeriodicUpdateTick = (physicsTickCounter % INACTIVE_OBJECT_UPDATE_INTERVAL_TICKS == 0);

        List<IPhysicsObject> objectsToUpdate = container.getAllObjects().parallelStream()
                .filter(obj -> obj != null && !obj.isRemoved() && obj.getBodyId() != 0 && bodyInterfaceNoLock.isAdded(obj.getBodyId()))
                .peek(obj -> {
                    obj.fixedPhysicsTick(manager.getWorld());
                    obj.physicsTick(manager.getWorld());
                })
                .filter(obj -> {
                    boolean isActive = bodyInterfaceNoLock.isActive(obj.getBodyId());
                    if (lastActiveState.getOrDefault(obj.getPhysicsId(), !isActive) != isActive) {
                        lastActiveState.put(obj.getPhysicsId(), isActive);
                        return true;
                    }
                    return isActive || obj.isDataDirty() || isPeriodicUpdateTick;
                })
                .toList();

        if (!objectsToUpdate.isEmpty()) {
            List<PhysicsObjectState> statesToSend = new ArrayList<>(objectsToUpdate.size());
            for (IPhysicsObject obj : objectsToUpdate) {
                updateObjectState(obj, timestampNanos, bodyInterfaceNoLock, lockInterface, statesToSend);
            }
            if (!statesToSend.isEmpty()) {
                dispatcher.dispatchStateUpdates(statesToSend);
            }
        }
    }

    private void updateObjectState(IPhysicsObject obj, long timestampNanos, BodyInterface bodyInterfaceNoLock, BodyLockInterface lockInterface, List<PhysicsObjectState> statesToSend) {
        VxTransform transform = tempTransform.get();
        Vec3 linVel = null;
        Vec3 angVel = null;
        float[] vertexData = null;
        boolean isActive = bodyInterfaceNoLock.isActive(obj.getBodyId());

        final StampedLock transformLock = obj.getTransformLock();
        long stamp = transformLock.writeLock();
        try {
            bodyInterfaceNoLock.getPositionAndRotation(obj.getBodyId(), transform.getTranslation(), transform.getRotation());
            if (isActive) {
                linVel = bodyInterfaceNoLock.getLinearVelocity(obj.getBodyId());
                angVel = bodyInterfaceNoLock.getAngularVelocity(obj.getBodyId());
                if (obj.getEObjectType() == EObjectType.SOFT_BODY) {
                    vertexData = getSoftBodyVertices(lockInterface, obj.getBodyId());
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
                        float[] vertexBuffer = new float[numVertices * 3];
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
}