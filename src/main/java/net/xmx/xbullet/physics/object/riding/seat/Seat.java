package net.xmx.xbullet.physics.object.riding.seat;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.Mat44;
import com.github.stephengold.joltjni.RMat44;
import com.github.stephengold.joltjni.Vec3;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.physicsobject.IPhysicsObject;

import javax.annotation.Nullable;
import java.util.UUID;

public class Seat {

    private final IPhysicsObject parent;
    private final String seatId;
    private final PhysicsTransform relativeTransform;
    private final Vec3 seatSize;

    @Nullable
    private UUID mountedPlayerUUID;

    public Seat(IPhysicsObject parent, String seatId, PhysicsTransform relativeTransform, Vec3 seatSize) {
        this.parent = parent;
        this.seatId = seatId;
        this.relativeTransform = relativeTransform;
        this.seatSize = seatSize;
    }

    public String getSeatId() {
        return seatId;
    }

    public IPhysicsObject getParent() {
        return parent;
    }

    public PhysicsTransform getRelativeTransform() {
        return relativeTransform;
    }

    @Nullable
    public UUID getMountedPlayerUUID() {
        return mountedPlayerUUID;
    }

    public void setMountedPlayerUUID(@Nullable UUID mountedPlayerUUID) {
        this.mountedPlayerUUID = mountedPlayerUUID;
    }

    public boolean isOccupied() {
        return this.mountedPlayerUUID != null;
    }

    public PhysicsTransform calculateWorldTransform() {
        PhysicsTransform parentTransform = parent.getCurrentTransform();

        RMat44 parentMatrix = RMat44.sRotationTranslation(parentTransform.getRotation(), parentTransform.getTranslation());

        Mat44 relativeMatrix = Mat44.sRotationTranslation(relativeTransform.getRotation(), relativeTransform.getTranslation().toVec3());

        RMat44 finalMatrix = parentMatrix.multiply(relativeMatrix);

        return new PhysicsTransform(finalMatrix.getTranslation(), finalMatrix.getQuaternion());
    }

    public AaBox calculateWorldAABB() {
        PhysicsTransform worldTransform = calculateWorldTransform();
        Vec3 center = worldTransform.getTranslation().toVec3();

        return new AaBox(
                new Vec3(center.getX() - seatSize.getX(), center.getY() - seatSize.getY(), center.getZ() - seatSize.getZ()),
                new Vec3(center.getX() + seatSize.getX(), center.getY() + seatSize.getY(), center.getZ() + seatSize.getZ())
        );
    }
}