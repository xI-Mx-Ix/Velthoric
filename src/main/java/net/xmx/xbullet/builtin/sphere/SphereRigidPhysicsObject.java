package net.xmx.xbullet.builtin.sphere;

import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.SphereShapeSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.physicsobject.properties.IPhysicsObjectProperties;
import net.xmx.xbullet.physics.object.physicsobject.type.rigid.RigidPhysicsObject;

import javax.annotation.Nullable;
import java.util.UUID;

public class SphereRigidPhysicsObject extends RigidPhysicsObject {
    public static final String TYPE_IDENTIFIER = "xbullet:sphere_obj";
    public static final String NBT_RADIUS_KEY = "radius";

    private float radius;

    public SphereRigidPhysicsObject(UUID physicsId, Level level, String objectTypeIdentifier, PhysicsTransform initialTransform, IPhysicsObjectProperties properties, @Nullable CompoundTag initialNbt) {
        super(physicsId, level, objectTypeIdentifier, initialTransform, properties, initialNbt);
        if (this.radius <= 0) {
            this.radius = 0.5f;
        }
    }

    @Override
    public ShapeSettings buildShapeSettings() {
        return new SphereShapeSettings(this.radius);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat(NBT_RADIUS_KEY, this.radius);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(NBT_RADIUS_KEY, CompoundTag.TAG_FLOAT)) {
            this.radius = tag.getFloat(NBT_RADIUS_KEY);
        } else {
            this.radius = 0.5f;
        }
    }

    public float getRadius() {
        return radius;
    }
}