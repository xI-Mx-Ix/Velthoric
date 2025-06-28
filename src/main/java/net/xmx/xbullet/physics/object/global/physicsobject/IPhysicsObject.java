// net/xmx/xbullet/physics/object/global/physicsobject/IPhysicsObject.java
package net.xmx.xbullet.physics.object.global.physicsobject;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.SoftBodyCreationSettings;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.core.PhysicsWorld;

import javax.annotation.Nullable;
import java.util.UUID;

public interface IPhysicsObject {
    UUID getPhysicsId();
    String getObjectTypeIdentifier();
    EObjectType getPhysicsObjectType();
    Level getLevel();
    boolean isRemoved();
    void markRemoved();
    PhysicsTransform getCurrentTransform();
    int getBodyId();
    void setBodyId(int bodyId);
    void initializePhysics(PhysicsWorld physicsWorld);
    void removeFromPhysics(PhysicsWorld physicsWorld);
    void serverTick(PhysicsWorld physicsWorld);
    CompoundTag saveToNbt(CompoundTag tag);
    void loadFromNbt(CompoundTag tag);
    void onRightClickWithTool(Player player);
    boolean isPhysicsInitialized();

    void onLeftClick(Player player, Vec3 hitPoint, Vec3 hitNormal);
    void onRightClick(Player player, Vec3 hitPoint, Vec3 hitNormal);

    void confirmPhysicsInitialized();
    @Nullable BodyCreationSettings createBodyCreationSettings();
    @Nullable SoftBodyCreationSettings createSoftBodyCreationSettings();
    void updateStateFromPhysicsThread(PhysicsTransform transform, Vec3 linearVelocity, Vec3 angularVelocity, @Nullable float[] softBodyVertices, boolean isActive);
}