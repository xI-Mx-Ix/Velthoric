package net.xmx.xbullet.builtin.box;

import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.physicsobject.properties.IPhysicsObjectProperties;
import net.xmx.xbullet.physics.object.physicsobject.type.rigid.RigidPhysicsObject;
import net.xmx.xbullet.physics.object.riding.PlayerRidingSystem;

import javax.annotation.Nullable;
import java.util.UUID;

public class BoxRigidPhysicsObject extends RigidPhysicsObject {
    public static final String TYPE_IDENTIFIER = "xbullet:box_obj";

    private Vec3 halfExtents;

    public BoxRigidPhysicsObject(UUID physicsId, Level level, String typeId, PhysicsTransform initialTransform, IPhysicsObjectProperties properties, @Nullable FriendlyByteBuf initialData) {
        super(physicsId, level, typeId, initialTransform, properties);
    }

    public void setHalfExtents(Vec3 halfExtents) {
        this.halfExtents = halfExtents;
    }

    @Override
    public ShapeSettings buildShapeSettings() {
        BoxShapeSettings settings = new BoxShapeSettings(this.halfExtents);
        settings.setConvexRadius(0.04f);
        return settings;
    }


    @Override
    protected void addAdditionalData(FriendlyByteBuf buf) {
        super.addAdditionalData(buf);
        buf.writeFloat(this.halfExtents.getX());
        buf.writeFloat(this.halfExtents.getY());
        buf.writeFloat(this.halfExtents.getZ());
    }

    @Override
    protected void readAdditionalData(FriendlyByteBuf buf) {
        super.readAdditionalData(buf);
        this.halfExtents = new Vec3(
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat()
        );
    }

    public Vec3 getHalfExtents() {
        return halfExtents;
    }

    @Override
    public void onRightClick(ServerPlayer player, Vec3 hitPoint, Vec3 hitNormal) {
        PlayerRidingSystem.startRiding(player, this);
    }
}