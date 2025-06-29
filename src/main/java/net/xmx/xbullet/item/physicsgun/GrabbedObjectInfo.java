package net.xmx.xbullet.item.physicsgun;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.operator.Op;
import net.xmx.xbullet.physics.object.rigidphysicsobject.RigidPhysicsObject;
import net.xmx.xbullet.physics.object.softphysicsobject.SoftPhysicsObject;
import net.xmx.xbullet.physics.physicsworld.PhysicsWorldRegistry;

import java.util.UUID;

public class GrabbedObjectInfo {
    public final UUID objectId;
    public final boolean isRigid;
    public final int nodeId;
    public final Vec3 localGrabOffset;
    public final Quat localRotationOffset;
    public float targetDistance;

    public GrabbedObjectInfo(RigidPhysicsObject rpo, RVec3 worldHitPoint, Quat playerRotation, float initialDistance) {
        this.objectId = rpo.getPhysicsId();
        this.isRigid = true;
        this.nodeId = -1;
        this.targetDistance = initialDistance;

        try (BodyLockRead lock = new BodyLockRead(PhysicsWorldRegistry.getInstance().getPhysicsWorld(rpo.getLevel().dimension()).getBodyLockInterface(), rpo.getBodyId())) {
            Body body = lock.getBody();
            if (lock.succeeded() && body != null) {
                RVec3 bodyPosition = body.getCenterOfMassPosition();
                Quat bodyRotation = body.getRotation();
                RVec3 worldOffset = Op.minus(worldHitPoint, bodyPosition);
                this.localGrabOffset = Op.star(bodyRotation.conjugated(), worldOffset.toVec3());
                this.localRotationOffset = Op.star(playerRotation.conjugated(), bodyRotation);
            } else {
                this.localGrabOffset = new Vec3();
                this.localRotationOffset = new Quat();
            }
        }
    }

    public GrabbedObjectInfo(SoftPhysicsObject spo, int nodeId, float initialDistance) {
        this.objectId = spo.getPhysicsId();
        this.isRigid = false;
        this.nodeId = nodeId;
        this.targetDistance = initialDistance;
        this.localGrabOffset = new Vec3();
        this.localRotationOffset = new Quat();
    }
}