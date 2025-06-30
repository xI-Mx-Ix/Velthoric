package net.xmx.xbullet.physics.physicsworld.pcmd;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.readonly.ConstSoftBodySharedSettings;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorld;
import net.xmx.xbullet.physics.object.global.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;

import java.nio.FloatBuffer;

public record UpdatePhysicsStateCommand(long timestampNanos) implements ICommand {

    @Override
    public void execute(PhysicsWorld world) {
        PhysicsSystem system = world.getPhysicsSystem();
        if (system == null) return;

        BodyInterface bodyInterface = system.getBodyInterface();
        BodyLockInterface lockInterface = world.getBodyLockInterface();
        if (lockInterface == null) return;

        for (IPhysicsObject obj : world.getPhysicsObjectsMap().values()) {
            if (obj.isRemoved() || obj.getBodyId() == 0) continue;

            int bodyId = obj.getBodyId();

            if (!bodyInterface.isAdded(bodyId)) {
                continue;
            }

            boolean isActive = bodyInterface.isActive(bodyId);

            world.getSyncedActiveStates().put(obj.getPhysicsId(), isActive);
            world.getSyncedStateTimestampsNanos().put(obj.getPhysicsId(), this.timestampNanos);

            PhysicsTransform transform = new PhysicsTransform();
            bodyInterface.getPositionAndRotation(bodyId, transform.getTranslation(), transform.getRotation());
            world.getSyncedTransforms().put(obj.getPhysicsId(), transform);
            world.getSyncedLinearVelocities().put(obj.getPhysicsId(), bodyInterface.getLinearVelocity(bodyId));
            world.getSyncedAngularVelocities().put(obj.getPhysicsId(), bodyInterface.getAngularVelocity(bodyId));

            if (obj.getPhysicsObjectType() == EObjectType.SOFT_BODY) {
                try (BodyLockRead lock = new BodyLockRead(lockInterface, bodyId)) {
                    if (lock.succeededAndIsInBroadPhase()) {
                        Body body = lock.getBody();
                        if (body != null && body.isSoftBody()) {
                            // Cast zu SoftBodyMotionProperties ist sicher, da wir isSoftBody() geprüft haben.
                            SoftBodyMotionProperties motionProps = (SoftBodyMotionProperties) body.getMotionProperties();

                            // Sicherere Methode: Anzahl der Vertices holen und dann einzeln abrufen.
                            ConstSoftBodySharedSettings sharedSettings = motionProps.getSettings();
                            int numVertices = sharedSettings.countVertices();

                            if (numVertices > 0) {
                                RMat44 worldTransform = body.getWorldTransform();
                                float[] vertexData = new float[numVertices * 3];

                                for (int i = 0; i < numVertices; i++) {
                                    // Jeden Vertex einzeln holen, anstatt das ganze Array.
                                    SoftBodyVertex vertex = motionProps.getVertex(i);
                                    if (vertex == null) continue; // Sicherheitsabfrage

                                    Vec3 localPos = vertex.getPosition();
                                    RVec3 worldPos = worldTransform.multiply3x4(localPos);
                                    vertexData[i * 3] = worldPos.x();
                                    vertexData[i * 3 + 1] = worldPos.y();
                                    vertexData[i * 3 + 2] = worldPos.z();
                                }
                                world.getSyncedSoftBodyVertexData().put(obj.getPhysicsId(), vertexData);
                            }
                        }
                    }
                }
            }
        }
    }
}