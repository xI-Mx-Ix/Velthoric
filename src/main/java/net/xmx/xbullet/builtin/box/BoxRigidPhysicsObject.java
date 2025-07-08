package net.xmx.xbullet.builtin.box;

import com.github.stephengold.joltjni.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.physicsobject.properties.IPhysicsObjectProperties;
import net.xmx.xbullet.physics.object.physicsobject.type.rigid.RigidPhysicsObject;

import javax.annotation.Nullable;
import java.util.UUID;

public class BoxRigidPhysicsObject extends RigidPhysicsObject {
    public static final String TYPE_IDENTIFIER = "xbullet:box_obj";
    public static final String NBT_HALF_EXTENTS_KEY = "halfExtents";

    private Vec3 halfExtents;

    public BoxRigidPhysicsObject(UUID physicsId, Level level, String objectTypeIdentifier, PhysicsTransform initialTransform, IPhysicsObjectProperties properties, @Nullable CompoundTag initialNbt) {
        super(physicsId, level, objectTypeIdentifier, initialTransform, properties, initialNbt);

        if (this.halfExtents == null) {
            this.halfExtents = new Vec3(0.5f, 0.5f, 0.5f);
        }
    }

    @Override
    public ShapeSettings buildShapeSettings() {
        return new BoxShapeSettings(this.halfExtents);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        CompoundTag extentsTag = new CompoundTag();
        extentsTag.putFloat("x", this.halfExtents.getX());
        extentsTag.putFloat("y", this.halfExtents.getY());
        extentsTag.putFloat("z", this.halfExtents.getZ());
        tag.put(NBT_HALF_EXTENTS_KEY, extentsTag);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(NBT_HALF_EXTENTS_KEY, CompoundTag.TAG_COMPOUND)) {
            CompoundTag extentsTag = tag.getCompound(NBT_HALF_EXTENTS_KEY);
            this.halfExtents = new Vec3(
                    extentsTag.getFloat("x"),
                    extentsTag.getFloat("y"),
                    extentsTag.getFloat("z")
            );
        }
    }
    
    public Vec3 getHalfExtents() {
        return halfExtents;
    }
}