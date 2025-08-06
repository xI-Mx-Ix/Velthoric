package net.xmx.vortex.builtin.box;

import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.xmx.vortex.physics.object.physicsobject.PhysicsObjectType;
import net.xmx.vortex.physics.object.physicsobject.type.rigid.RigidPhysicsObject;
import net.xmx.vortex.physics.world.VxPhysicsWorld;

public class BoxRigidPhysicsObject extends RigidPhysicsObject {

    private Vec3 halfExtents;

    public BoxRigidPhysicsObject(PhysicsObjectType<? extends RigidPhysicsObject> type, Level level) {
        super(type, level);
        this.halfExtents = new Vec3(0.5f, 0.5f, 0.5f);
    }

    public void setHalfExtents(Vec3 halfExtents) {
        this.halfExtents = halfExtents;
    }

    public Vec3 getHalfExtents() {
        return halfExtents;
    }

    @Override
    public ShapeSettings buildShapeSettings() {
        BoxShapeSettings settings = new BoxShapeSettings(this.halfExtents);
        return settings;
    }

    @Override
    protected void addAdditionalSpawnData(FriendlyByteBuf buf) {
        buf.writeFloat(this.halfExtents.getX());
        buf.writeFloat(this.halfExtents.getY());
        buf.writeFloat(this.halfExtents.getZ());
    }

    @Override
    protected void readAdditionalSpawnData(FriendlyByteBuf buf) {
        this.halfExtents = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public void onRightClick(ServerPlayer player, com.github.stephengold.joltjni.Vec3 hitPoint, com.github.stephengold.joltjni.Vec3 hitNormal) {
        if (this.level instanceof ServerLevel serverLevel) {
            VxPhysicsWorld world = VxPhysicsWorld.get(serverLevel.dimension());
            if (world != null) {
                world.getObjectManager().getRidingManager().startRiding(player, this);
            }
        }
    }
}