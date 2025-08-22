package net.xmx.velthoric.builtin.sphere;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.physics.object.PhysicsObjectType;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

public class SphereRigidPhysicsObject extends VxRigidBody {

    private float radius;

    public SphereRigidPhysicsObject(PhysicsObjectType<SphereRigidPhysicsObject> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
        this.radius = 0.5f;
    }

    public void setRadius(float radius) {
        this.radius = radius > 0 ? radius : 0.5f;
        this.markDataDirty();
    }

    public float getRadius() {
        return radius;
    }

    @Override
    public ShapeSettings createShapeSettings() {
        return new SphereShapeSettings(this.radius);
    }

    @Override
    public BodyCreationSettings createBodyCreationSettings(ShapeRefC shapeRef) {
        return new BodyCreationSettings(
                shapeRef,
                this.getGameTransform().getTranslation(),
                this.getGameTransform().getRotation(),
                EMotionType.Dynamic,
                VxLayers.DYNAMIC);
    }

    @Override
    public void writeCreationData(FriendlyByteBuf buf) {
        buf.writeFloat(this.radius);
    }

    @Override
    public void readCreationData(FriendlyByteBuf buf) {
        this.radius = buf.readFloat();
    }
}