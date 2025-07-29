package net.xmx.vortex.builtin.sphere;

import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.SphereShapeSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.xmx.vortex.physics.object.physicsobject.PhysicsObjectType;
import net.xmx.vortex.physics.object.physicsobject.type.rigid.RigidPhysicsObject;

public class SphereRigidPhysicsObject extends RigidPhysicsObject {

    private float radius;

    public SphereRigidPhysicsObject(PhysicsObjectType<? extends RigidPhysicsObject> type, Level level) {
        super(type, level);
        this.radius = 0.5f;
    }

    public void setRadius(float radius) {
        this.radius = radius > 0 ? radius : 0.5f;
    }

    public float getRadius() {
        return radius;
    }

    @Override
    public ShapeSettings buildShapeSettings() {
        return new SphereShapeSettings(this.radius);
    }

    @Override
    protected void addAdditionalSpawnData(FriendlyByteBuf buf) {
        buf.writeFloat(this.radius);
    }

    @Override
    protected void readAdditionalSpawnData(FriendlyByteBuf buf) {
        this.radius = buf.readFloat();
    }
}