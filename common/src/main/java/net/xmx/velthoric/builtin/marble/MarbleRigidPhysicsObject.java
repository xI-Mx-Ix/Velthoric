package net.xmx.velthoric.builtin.marble;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.SphereShapeSettings;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import net.xmx.velthoric.network.VxByteBuf;
import net.xmx.velthoric.physics.object.VxObjectType;
import net.xmx.velthoric.physics.object.type.VxRigidBody;
import net.xmx.velthoric.physics.world.VxLayers;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;

import java.util.UUID;

public class MarbleRigidPhysicsObject extends VxRigidBody {

    private float radius;

    public MarbleRigidPhysicsObject(VxObjectType<MarbleRigidPhysicsObject> type, VxPhysicsWorld world, UUID id) {
        super(type, world, id);
        this.radius = 0.15f;
    }

    public void setRadius(float radius) {
        this.radius = radius;
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
        var settings = new BodyCreationSettings(
                shapeRef,
                this.getGameTransform().getTranslation(),
                this.getGameTransform().getRotation(),
                EMotionType.Dynamic,
                VxLayers.DYNAMIC);

        settings.setRestitution(0.6f);
        settings.setFriction(0.4f);
        return settings;
    }

    @Override
    public void writeCreationData(VxByteBuf buf) {
        buf.writeFloat(radius);
    }

    @Override
    public void readCreationData(VxByteBuf buf) {
        this.radius = buf.readFloat();
    }
}