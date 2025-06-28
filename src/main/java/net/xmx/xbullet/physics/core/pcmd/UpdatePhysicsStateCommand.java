package net.xmx.xbullet.physics.core.pcmd;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BodyLockRead;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.SoftBodyMotionProperties;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.core.PhysicsWorld;
import net.xmx.xbullet.physics.object.global.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;

import java.nio.FloatBuffer;

public record UpdatePhysicsStateCommand(long timestampNanos) implements ICommand {

    @Override
    public void execute(PhysicsWorld world) {
        PhysicsSystem system = world.getPhysicsSystem();
        if (system == null) return;

        BodyInterface bodyInterface = system.getBodyInterface();

        // Wichtig: Kopiere die Map oder die Values, um ConcurrentModificationExceptions zu vermeiden,
        // falls die Haupt-Map auf einem anderen Thread geändert wird. Hier ist es wahrscheinlich sicher,
        // da Änderungen über Commands laufen, aber es ist eine gute Praxis.
        for (IPhysicsObject obj : world.getPhysicsObjectsMap().values()) {
            if (obj.isRemoved() || obj.getBodyId() == 0) continue;

            int bodyId = obj.getBodyId();

            // ================== HIER IST DIE WICHTIGE ÄNDERUNG ==================
            // Überprüfe, ob der Body in der nativen Jolt-Welt noch existiert,
            // bevor du versuchst, darauf zuzugreifen.
            if (!bodyInterface.isAdded(bodyId)) {
                // Wenn der Body nicht mehr hinzugefügt ist, überspringe ihn.
                // Er wurde wahrscheinlich in diesem oder einem vorherigen Tick entfernt.
                continue;
            }
            // =====================================================================


            boolean isActive = bodyInterface.isActive(bodyId);

            world.getSyncedActiveStates().put(obj.getPhysicsId(), isActive);
            world.getSyncedStateTimestampsNanos().put(obj.getPhysicsId(), this.timestampNanos);

            if (obj.getPhysicsObjectType() == EObjectType.RIGID_BODY) {
                PhysicsTransform transform = new PhysicsTransform();
                bodyInterface.getPositionAndRotation(bodyId, transform.getTranslation(), transform.getRotation());
                world.getSyncedTransforms().put(obj.getPhysicsId(), transform);
                world.getSyncedLinearVelocities().put(obj.getPhysicsId(), bodyInterface.getLinearVelocity(bodyId));
                world.getSyncedAngularVelocities().put(obj.getPhysicsId(), bodyInterface.getAngularVelocity(bodyId));
            } else if (obj.getPhysicsObjectType() == EObjectType.SOFT_BODY) {
                // Das try-with-resources ist hier sicher, weil wir jetzt wissen, dass der bodyId gültig ist.
                try (BodyLockRead lock = new BodyLockRead(world.getBodyLockInterface(), bodyId)) {
                    Body body = lock.getBody();
                    if (body != null && body.isSoftBody()) {
                        SoftBodyMotionProperties motionProps = (SoftBodyMotionProperties) body.getMotionProperties();
                        var vertices = motionProps.getVertices();
                        if (vertices.length > 0) {
                            float[] vertexData = new float[vertices.length * 3];
                            FloatBuffer buffer = FloatBuffer.wrap(vertexData);

                            motionProps.putVertexLocations(body.getPosition(), buffer);
                            world.getSyncedSoftBodyVertexData().put(obj.getPhysicsId(), vertexData);
                        }
                    }
                }
            }
        }
    }
}