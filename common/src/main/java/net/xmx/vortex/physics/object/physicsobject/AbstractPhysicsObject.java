package net.xmx.vortex.physics.object.physicsobject;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyLockRead;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.properties.IPhysicsObjectProperties;
import net.xmx.vortex.physics.object.riding.RidingProxyEntity;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import org.jetbrains.annotations.Nullable;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.StampedLock;

public abstract class AbstractPhysicsObject implements IPhysicsObject {

    protected UUID physicsId;
    protected final Level level;
    protected final IPhysicsObjectProperties properties;
    protected final VxTransform currentTransform = new VxTransform();
    protected final StampedLock transformLock = new StampedLock();

    protected final Vec3 lastSyncedLinearVel = new Vec3();
    protected final Vec3 lastSyncedAngularVel = new Vec3();

    protected volatile boolean isActive = false;
    protected volatile long lastUpdateTimestampNanos = 0L;
    protected volatile boolean dataDirty = false;
    protected int bodyId = 0;
    protected boolean isRemoved = false;
    protected boolean physicsInitialized = false;

    @Nullable
    protected transient RidingProxyEntity ridingProxy = null;

    protected AbstractPhysicsObject(PhysicsObjectType<?> type, Level level) {
        this.level = level;
        this.properties = type.getDefaultProperties();
    }

    @Override
    public final void writeSpawnData(FriendlyByteBuf buf) {
        this.currentTransform.toBuffer(buf);
        addAdditionalSpawnData(buf);
    }

    @Override
    public final void readSpawnData(FriendlyByteBuf buf) {
        this.currentTransform.fromBuffer(buf);
        readAdditionalSpawnData(buf);
    }

    @Override
    public final void writeCustomSyncData(FriendlyByteBuf buf) {
        addAdditionalSpawnData(buf);
    }

    protected abstract void addAdditionalSpawnData(FriendlyByteBuf buf);
    protected abstract void readAdditionalSpawnData(FriendlyByteBuf buf);

    @Override
    public abstract PhysicsObjectType<?> getPhysicsObjectType();

    @Override
    public String getObjectTypeIdentifier() { return getPhysicsObjectType().getTypeId(); }

    @Override
    public EObjectType getEObjectType() { return getPhysicsObjectType().getObjectTypeEnum(); }

    @Override
    public UUID getPhysicsId() { return this.physicsId; }

    @Override
    public void setPhysicsId(UUID physicsId) { this.physicsId = physicsId; }

    @Override
    public Level getLevel() { return this.level; }

    @Override
    public boolean isRemoved() { return this.isRemoved; }

    @Override
    public void markRemoved() { this.isRemoved = true; }

    @Override
    public StampedLock getTransformLock() { return this.transformLock; }

    @Override
    public int getBodyId() { return this.bodyId; }

    @Override
    public void setBodyId(int bodyId) { this.bodyId = bodyId; }

    @Override
    public boolean isPhysicsInitialized() { return this.physicsInitialized; }

    @Override
    public void confirmPhysicsInitialized() { this.physicsInitialized = true; }

    @Override
    public void setInitialTransform(VxTransform transform) {
        this.currentTransform.set(transform);
    }

    @Override
    public VxTransform getCurrentTransform() {
        long stamp = transformLock.tryOptimisticRead();
        VxTransform transformCopy = this.currentTransform.copy();
        if (!transformLock.validate(stamp)) {
            stamp = transformLock.readLock();
            try {
                transformCopy.set(this.currentTransform);
            } finally {
                transformLock.unlockRead(stamp);
            }
        }
        return transformCopy;
    }

    @Override
    public void updateStateFromPhysicsThread(long timestampNanos, @Nullable VxTransform transform, @Nullable Vec3 linearVelocity, @Nullable Vec3 angularVelocity, @Nullable float[] softBodyVertices, boolean isActive) {
        if (this.isRemoved || level.isClientSide()) return;
        this.lastUpdateTimestampNanos = timestampNanos;
        this.isActive = isActive;
        if (isActive) {
            if (transform != null) {
                this.currentTransform.set(transform);
            }
            if (linearVelocity != null) {
                this.lastSyncedLinearVel.set(linearVelocity);
            }
            if (angularVelocity != null) {
                this.lastSyncedAngularVel.set(angularVelocity);
            }
        } else {
            this.lastSyncedLinearVel.loadZero();
            this.lastSyncedAngularVel.loadZero();
        }
    }

    @Override
    public Vec3 getLastSyncedLinearVel() { return new Vec3(this.lastSyncedLinearVel); }

    @Override
    public Vec3 getLastSyncedAngularVel() { return new Vec3(this.lastSyncedAngularVel); }

    @Override
    public boolean isPhysicsActive() { return this.isActive; }

    @Override
    public long getLastUpdateTimestampNanos() { return this.lastUpdateTimestampNanos; }

    @Override
    @Nullable
    public float[] getLastSyncedVertexData() { return null; }

    @Override
    public void markDataDirty() { this.dataDirty = true; }

    @Override
    public boolean isDataDirty() { return this.dataDirty; }

    @Override
    public void clearDataDirty() { this.dataDirty = false; }

    @Override
    public void onLeftClick(ServerPlayer player, Vec3 hitPoint, Vec3 hitNormal) {}

    @Override
    public void onRightClick(ServerPlayer player, Vec3 hitPoint, Vec3 hitNormal) {}

    @Override
    public abstract void onRightClickWithTool(ServerPlayer player);

    @Override
    public void setRidingProxy(@Nullable RidingProxyEntity proxy) { this.ridingProxy = proxy; }

    @Nullable
    @Override
    public RidingProxyEntity getRidingProxy() { return this.ridingProxy; }

    @Override
    public abstract void fixedGameTick(ServerLevel level);

    @Override
    public abstract void fixedPhysicsTick(VxPhysicsWorld physicsWorld);

    @Override
    @Nullable
    public Body getBody() {
        if (!isPhysicsInitialized() || getBodyId() == 0 || getLevel().isClientSide()) {
            return null;
        }
        VxPhysicsWorld world = VxPhysicsWorld.get(getLevel().dimension());
        if (world == null) return null;
        Optional<IPhysicsObject> found = world.getObjectManager().getUnsafe().getObjectContainer().getByBodyId(getBodyId());
        if (found.isPresent() && found.get() == this) {
            try (BodyLockRead lock = new BodyLockRead(world.getBodyLockInterface(), getBodyId())) {
                if (lock.succeededAndIsInBroadPhase()) {
                    return lock.getBody();
                }
            }
        }
        return null;
    }
}