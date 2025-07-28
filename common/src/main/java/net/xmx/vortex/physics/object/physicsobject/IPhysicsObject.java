package net.xmx.vortex.physics.object.physicsobject;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyLockRead;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.xmx.vortex.init.VxMainClass;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.riding.RidingProxyEntity;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.StampedLock;

public interface IPhysicsObject {
    UUID getPhysicsId();
    String getObjectTypeIdentifier();
    EObjectType getPhysicsObjectType();
    Level getLevel();
    boolean isRemoved();
    void markRemoved();
    VxTransform getCurrentTransform();
    StampedLock getTransformLock();
    int getBodyId();
    void setBodyId(int bodyId);
    void initializePhysics(VxPhysicsWorld physicsWorld);
    void removeFromPhysics(VxPhysicsWorld physicsWorld);

    void onRightClickWithTool(ServerPlayer player);

    boolean isPhysicsInitialized();
    void onLeftClick(ServerPlayer player, Vec3 hitPoint, Vec3 hitNormal);
    void onRightClick(ServerPlayer player, Vec3 hitPoint, Vec3 hitNormal);
    void confirmPhysicsInitialized();
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

    void writeData(FriendlyByteBuf buf);
    void readData(FriendlyByteBuf buf);

    void writeSyncData(FriendlyByteBuf buf);
    void readSyncData(FriendlyByteBuf buf);

    void setRidingProxy(@Nullable RidingProxyEntity proxy);

    @Nullable
    RidingProxyEntity getRidingProxy();

    @Nullable
    default Body getBody() {
        if (!isPhysicsInitialized()) {
            VxMainClass.LOGGER.warn("getBody returned null: physics not initialized");
            return null;
        }
        if (getBodyId() == 0) {
            VxMainClass.LOGGER.warn("getBody returned null: bodyId is 0");
            return null;
        }
        if (getLevel().isClientSide()) {
            VxMainClass.LOGGER.warn("getBody returned null: running on client side");
            return null;
        }

        VxPhysicsWorld world = VxPhysicsWorld.get(getLevel().dimension());
        if (world == null) {
            VxMainClass.LOGGER.warn("getBody returned null: physics world is null for dimension " + getLevel().dimension());
            return null;
        }

        Optional<IPhysicsObject> found = world.getObjectManager().getObjectByBodyId(getBodyId());
        if (found.isPresent() && found.get() == this) {
            BodyLockRead lock = new BodyLockRead(world.getBodyLockInterface(), getBodyId());
            try {
                if (lock.succeededAndIsInBroadPhase()) {
                    return lock.getBody();
                } else {
                    VxMainClass.LOGGER.warn("getBody returned null: lock did not succeed or object not in broad phase");
                }
            } finally {
                lock.releaseLock();
            }
        } else {
            VxMainClass.LOGGER.warn("getBody returned null: object not found or does not match this");
        }

        return null;
    }
}