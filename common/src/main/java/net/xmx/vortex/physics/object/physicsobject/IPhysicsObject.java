package net.xmx.vortex.physics.object.physicsobject;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.riding.RidingProxyEntity;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.locks.StampedLock;

public interface IPhysicsObject {
    UUID getPhysicsId();
    Level getLevel();
    boolean isRemoved();
    void markRemoved();
    VxTransform getCurrentTransform();
    StampedLock getTransformLock();
    int getBodyId();
    void setBodyId(int bodyId);
    void initializePhysics(VxPhysicsWorld physicsWorld);
    void removeFromPhysics(VxPhysicsWorld physicsWorld);
    void confirmPhysicsInitialized();
    boolean isPhysicsInitialized();

    PhysicsObjectType<?> getPhysicsObjectType();
    String getObjectTypeIdentifier();
    EObjectType getEObjectType();

    void onLeftClick(ServerPlayer player, Vec3 hitPoint, Vec3 hitNormal);
    void onRightClick(ServerPlayer player, Vec3 hitPoint, Vec3 hitNormal);
    void onRightClickWithTool(ServerPlayer player);

    void updateStateFromPhysicsThread(long timestampNanos, @Nullable VxTransform transform, @Nullable Vec3 linearVelocity, @Nullable Vec3 angularVelocity, @Nullable float[] softBodyVertices, boolean isActive);
    Vec3 getLastSyncedLinearVel();
    Vec3 getLastSyncedAngularVel();
    boolean isPhysicsActive();
    long getLastUpdateTimestampNanos();
    @Nullable float[] getLastSyncedVertexData();

    void markDataDirty();
    boolean isDataDirty();
    void clearDataDirty();

    void gameTick(ServerLevel level);
    void physicsTick(VxPhysicsWorld physicsWorld);
    void fixedGameTick(ServerLevel level);
    void fixedPhysicsTick(VxPhysicsWorld physicsWorld);

    void writeSpawnData(FriendlyByteBuf buf);
    void readSpawnData(FriendlyByteBuf buf);

    void setPhysicsId(UUID uuid);
    void setInitialTransform(VxTransform transform);

    void setRidingProxy(@Nullable RidingProxyEntity proxy);
    @Nullable RidingProxyEntity getRidingProxy();

    @Nullable
    Body getBody();

    void writeCustomSyncData(FriendlyByteBuf buf);
}