package net.xmx.xbullet.physics.object.physicsobject;

import com.github.stephengold.joltjni.Vec3;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.xmx.xbullet.item.PhysicsRemoverItem;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.physicsobject.properties.IPhysicsObjectProperties;
import net.xmx.xbullet.physics.object.riding.RidingProxyEntity;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import javax.annotation.Nullable;
import java.util.UUID;

public abstract class AbstractPhysicsObject implements IPhysicsObject {

    protected final UUID physicsId;
    protected final Level level;
    protected final String objectTypeIdentifier;
    protected final PhysicsTransform currentTransform;
    protected final IPhysicsObjectProperties properties;
    protected volatile boolean dataDirty = false;
    protected final Vec3 lastSyncedLinearVel = new Vec3();
    protected final Vec3 lastSyncedAngularVel = new Vec3();
    protected volatile boolean isActive = false;
    protected volatile long lastUpdateTimestampNanos = 0L;
    protected int bodyId = 0;
    protected boolean isRemoved = false;
    protected boolean physicsInitialized = false;

    @Nullable
    protected transient RidingProxyEntity ridingProxy = null;

    protected AbstractPhysicsObject(UUID physicsId, Level level, String objectTypeIdentifier, PhysicsTransform initialTransform, IPhysicsObjectProperties properties) {
        this.physicsId = physicsId;
        this.level = level;
        this.objectTypeIdentifier = objectTypeIdentifier;
        this.properties = properties;
        this.currentTransform = initialTransform != null ? initialTransform.copy() : new PhysicsTransform();
    }

    @Override
    public final void writeData(FriendlyByteBuf buf) {
        buf.writeUtf(this.getObjectTypeIdentifier());
        this.currentTransform.toBuffer(buf);
        addBodySpecificData(buf);
    }

    @Override
    public final void readData(FriendlyByteBuf buf) {
        buf.readUtf();
        this.currentTransform.fromBuffer(buf);
        readBodySpecificData(buf);
    }

    @Override
    public final void writeSyncData(FriendlyByteBuf buf) {
        this.currentTransform.toBuffer(buf);
        addBodySpecificData(buf);
    }

    @Override
    public final void readSyncData(FriendlyByteBuf buf) {
        this.currentTransform.fromBuffer(buf);
        readBodySpecificData(buf);
    }

    protected abstract void addBodySpecificData(FriendlyByteBuf buf);

    protected abstract void readBodySpecificData(FriendlyByteBuf buf);

    @Override
    public UUID getPhysicsId() {
        return this.physicsId;
    }

    @Override
    public String getObjectTypeIdentifier() {
        return this.objectTypeIdentifier;
    }

    @Override
    public Level getLevel() {
        return this.level;
    }

    @Override
    public boolean isRemoved() {
        return this.isRemoved;
    }

    @Override
    public void markRemoved() {
        if (!this.isRemoved) {
            this.isRemoved = true;
        }
    }

    @Override
    public synchronized PhysicsTransform getCurrentTransform() {
        return this.currentTransform.copy();
    }

    @Override
    public int getBodyId() {
        return this.bodyId;
    }

    @Override
    public void setBodyId(int bodyId) {
        this.bodyId = bodyId;
    }

    @Override
    public boolean isPhysicsInitialized() {
        return this.physicsInitialized;
    }

    @Override
    public void confirmPhysicsInitialized() {
        if (!this.physicsInitialized) {
            this.physicsInitialized = true;
        }
    }

    @Override
    public synchronized Vec3 getLastSyncedLinearVel() {
        return new Vec3(this.lastSyncedLinearVel);
    }

    @Override
    public synchronized Vec3 getLastSyncedAngularVel() {
        return new Vec3(this.lastSyncedAngularVel);
    }

    @Override
    public boolean isPhysicsActive() {
        return this.isActive;
    }

    @Override
    public long getLastUpdateTimestampNanos() {
        return this.lastUpdateTimestampNanos;
    }

    @Override
    @Nullable
    public float[] getLastSyncedVertexData() {
        return null;
    }

    @Override
    public void onLeftClick(ServerPlayer player, Vec3 hitPoint, Vec3 hitNormal) {
    }

    @Override
    public void onRightClick(ServerPlayer player, Vec3 hitPoint, Vec3 hitNormal) {
    }

    @Override
    public void onRightClickWithTool(ServerPlayer player) {
        if (player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof PhysicsRemoverItem) {
            this.markRemoved();
        }
    }

    @Override
    public void markDataDirty() {
        this.dataDirty = true;
    }

    @Override
    public boolean isDataDirty() {
        return this.dataDirty;
    }

    @Override
    public void clearDataDirty() {
        this.dataDirty = false;
    }

    @Override
    public void setRidingProxy(@Nullable RidingProxyEntity proxy) {
        this.ridingProxy = proxy;
    }

    @Override
    public abstract void fixedGameTick(ServerLevel level);

    @Override
    public abstract void fixedPhysicsTick(PhysicsWorld physicsWorld);

    @Override
    @Nullable
    public RidingProxyEntity getRidingProxy() {
        return this.ridingProxy;
    }
}