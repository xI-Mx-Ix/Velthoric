package net.xmx.xbullet.physics.world.pcmd;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.readonly.ConstSoftBodySharedSettings;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.global.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.world.PhysicsWorld;

public record UpdatePhysicsStateCommand(long timestampNanos) implements ICommand {

    @Override
    public void execute(PhysicsWorld world) {
        PhysicsSystem system = world.getPhysicsSystem();
        if (system == null) return;

        BodyInterface bodyInterface = system.getBodyInterface();
        BodyLockInterface lockInterface = world.getBodyLockInterface();
        if (lockInterface == null) return;

        PhysicsTransform tempTransform = new PhysicsTransform();
        Vec3 tempLinVel = new Vec3();
        Vec3 tempAngVel = new Vec3();

        for (IPhysicsObject obj : world.getPhysicsObjectsMap().values()) {
            if (obj.isRemoved() || obj.getBodyId() == 0) continue;

            int bodyId = obj.getBodyId();
            if (!bodyInterface.isAdded(bodyId)) {
                continue;
            }

            boolean isActive = bodyInterface.isActive(bodyId);
            PhysicsTransform transform = null;
            Vec3 linVel = null;
            Vec3 angVel = null;
            float[] vertexData = null;

            if (isActive) {
                bodyInterface.getPositionAndRotation(bodyId, tempTransform.getTranslation(), tempTransform.getRotation());
                transform = tempTransform;

                tempLinVel.set(bodyInterface.getLinearVelocity(bodyId));
                linVel = tempLinVel;

                tempAngVel.set(bodyInterface.getAngularVelocity(bodyId));
                angVel = tempAngVel;

                if (obj.getPhysicsObjectType() == EObjectType.SOFT_BODY) {
                    vertexData = getSoftBodyVertices(lockInterface, bodyId);
                }
            }

            obj.updateStateFromPhysicsThread(this.timestampNanos, transform, linVel, angVel, vertexData, isActive);
        }
    }

    private float[] getSoftBodyVertices(BodyLockInterface lockInterface, int bodyId) {
        try (BodyLockRead lock = new BodyLockRead(lockInterface, bodyId)) {
            if (lock.succeededAndIsInBroadPhase()) {
                Body body = lock.getBody();
                if (body != null && body.isSoftBody()) {
                    SoftBodyMotionProperties motionProps = (SoftBodyMotionProperties) body.getMotionProperties();
                    ConstSoftBodySharedSettings sharedSettings = motionProps.getSettings();
                    int numVertices = sharedSettings.countVertices();

                    if (numVertices > 0) {
                        RMat44 worldTransform = body.getWorldTransform();
                        float[] vertexData = new float[numVertices * 3];

                        for (int i = 0; i < numVertices; i++) {
                            SoftBodyVertex vertex = motionProps.getVertex(i);
                            if (vertex == null) continue;

                            Vec3 localPos = vertex.getPosition();
                            RVec3 worldPos = worldTransform.multiply3x4(localPos);
                            int baseIndex = i * 3;
                            vertexData[baseIndex] = worldPos.x();
                            vertexData[baseIndex + 1] = worldPos.y();
                            vertexData[baseIndex + 2] = worldPos.z();
                        }
                        return vertexData;
                    }
                }
            }
        }
        return null;
    }
}