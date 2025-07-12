package net.xmx.xbullet.builtin.sphere;

import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.SphereShapeSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.physicsobject.properties.IPhysicsObjectProperties;
import net.xmx.xbullet.physics.object.physicsobject.type.rigid.RigidPhysicsObject;

import javax.annotation.Nullable;
import java.util.UUID;

public class SphereRigidPhysicsObject extends RigidPhysicsObject {
    public static final String TYPE_IDENTIFIER = "xbullet:sphere_obj";

    private float radius;

    public SphereRigidPhysicsObject(UUID physicsId, Level level, PhysicsTransform initialTransform, IPhysicsObjectProperties properties, float radius) {
        super(physicsId, level, TYPE_IDENTIFIER, initialTransform, properties);
        this.radius = radius > 0 ? radius : 0.5f;
    }

    public SphereRigidPhysicsObject(UUID physicsId, Level level, String typeId, PhysicsTransform initialTransform, IPhysicsObjectProperties properties, @Nullable FriendlyByteBuf initialData) {
        super(physicsId, level, typeId, initialTransform, properties);
        this.radius = 0.5f;
    }

    @Override
    public ShapeSettings buildShapeSettings() {
        return new SphereShapeSettings(this.radius);
    }

    @Override
    protected void addAdditionalData(FriendlyByteBuf buf) {
        super.addBodySpecificData(buf);
        buf.writeFloat(this.radius);
    }

    @Override
    protected void readAdditionalData(FriendlyByteBuf buf) {
        super.readBodySpecificData(buf);
        this.radius = buf.readFloat();
    }

    public float getRadius() {
        return radius;
    }
}