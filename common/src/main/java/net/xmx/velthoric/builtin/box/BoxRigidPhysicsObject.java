package net.xmx.velthoric.builtin.box;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.physics.object.physicsobject.PhysicsObjectType;
import net.xmx.velthoric.physics.object.physicsobject.type.rigid.VxRigidBody;
import net.xmx.velthoric.physics.object.riding.seat.Seat;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import org.joml.Vector3f;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

public class BoxRigidPhysicsObject extends VxRigidBody {

    private Vec3 halfExtents;

    public BoxRigidPhysicsObject(PhysicsObjectType<BoxRigidPhysicsObject> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
        this.halfExtents = new Vec3(0.5f, 0.5f, 0.5f);
    }

    public void setHalfExtents(Vec3 halfExtents) {
        this.halfExtents = halfExtents;
        this.markDataDirty();
    }

    public Vec3 getHalfExtents() {
        return halfExtents;
    }

    @Override
    public Seat[] defineSeats() {
        Seat leftSeat = new Seat("leftSeat",
                new AABB(-2f, -1f, -1f, 0f, 1f, 1f),
                new Vector3f(-1f, 0f, 0f)
        );

        Seat rightSeat = new Seat("rightSeat",
                new AABB(0f, -1f, -1f, 2f, 1f, 1f),
                new Vector3f(1f, 0f, 0f)
        );

        return new Seat[]{leftSeat, rightSeat};
    }

    @Override
    public ShapeSettings createShapeSettings() {
        return new BoxShapeSettings(this.halfExtents);
    }

    @Override
    public BodyCreationSettings createBodyCreationSettings(ShapeRefC shapeRef) {
        var settings = new BodyCreationSettings(
                shapeRef,
                this.getGameTransform().getTranslation(),
                this.getGameTransform().getRotation(),
                EMotionType.Dynamic,
                VxLayers.DYNAMIC);

        settings.setRestitution(0.4f);
        return settings;
    }

    @Override
    public void writeCreationData(FriendlyByteBuf buf) {
        buf.writeFloat(this.halfExtents.getX());
        buf.writeFloat(this.halfExtents.getY());
        buf.writeFloat(this.halfExtents.getZ());
    }

    @Override
    public void readCreationData(FriendlyByteBuf buf) {
        this.halfExtents = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }
}