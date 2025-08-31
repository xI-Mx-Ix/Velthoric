package net.xmx.velthoric.builtin.box;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.PhysicsObjectType;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

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
    public void writeCreationData(VxByteBuf buf) {
        buf.writeVec3(halfExtents);
    }

    @Override
    public void readCreationData(VxByteBuf buf) {
        this.halfExtents = buf.readVec3();
    }
}