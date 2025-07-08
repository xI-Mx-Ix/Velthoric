package net.xmx.xbullet.physics.object.physicsobject;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.SoftBodyCreationSettings;
import com.github.stephengold.joltjni.Vec3;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import net.xmx.xbullet.item.PhysicsRemoverItem;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.physicsobject.properties.IPhysicsObjectProperties;
import net.xmx.xbullet.physics.object.riding.RidingManager;
import net.xmx.xbullet.physics.object.riding.seat.Seat;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractPhysicsObject implements IPhysicsObject {

    protected final UUID physicsId;
    protected final Level level;
    protected final String objectTypeIdentifier;
    protected final PhysicsTransform currentTransform;
    protected final IPhysicsObjectProperties properties;

    protected volatile boolean nbtDirty = false;

    protected final Map<String, Seat> seats = new ConcurrentHashMap<>();

    protected final Vec3 lastSyncedLinearVel = new Vec3();
    protected final Vec3 lastSyncedAngularVel = new Vec3();

    protected volatile boolean isActive = false;
    protected volatile long lastUpdateTimestampNanos = 0L;

    protected int bodyId = 0;
    protected boolean isRemoved = false;
    protected boolean physicsInitialized = false;

    protected AbstractPhysicsObject(UUID physicsId, Level level, String objectTypeIdentifier, PhysicsTransform initialTransform, IPhysicsObjectProperties properties, @Nullable CompoundTag initialNbt) {
        this.physicsId = physicsId;
        this.level = level;
        this.objectTypeIdentifier = objectTypeIdentifier;
        this.properties = properties;
        this.currentTransform = initialTransform != null ? initialTransform.copy() : new PhysicsTransform();
        if (initialNbt != null) {
            this.loadFromNbt(initialNbt);
        }
    }

    @Override public UUID getPhysicsId() { return this.physicsId; }
    @Override public String getObjectTypeIdentifier() { return this.objectTypeIdentifier; }
    @Override public Level getLevel() { return this.level; }
    @Override public boolean isRemoved() { return this.isRemoved; }
    @Override public void markRemoved() { if (!this.isRemoved) this.isRemoved = true; }
    @Override public synchronized PhysicsTransform getCurrentTransform() { return this.currentTransform.copy(); }
    @Override public int getBodyId() { return this.bodyId; }
    @Override public void setBodyId(int bodyId) { this.bodyId = bodyId; }
    @Override public boolean isPhysicsInitialized() { return this.physicsInitialized; }
    @Override public void confirmPhysicsInitialized() { if (!this.physicsInitialized) this.physicsInitialized = true; }
    @Override public synchronized Vec3 getLastSyncedLinearVel() { return new Vec3(this.lastSyncedLinearVel); }
    @Override public synchronized Vec3 getLastSyncedAngularVel() { return new Vec3(this.lastSyncedAngularVel); }
    @Override public boolean isPhysicsActive() { return this.isActive; }
    @Override public long getLastUpdateTimestampNanos() { return this.lastUpdateTimestampNanos; }
    @Override @Nullable public float[] getLastSyncedVertexData() { return null; }

    @Override
    public final CompoundTag saveToNbt(CompoundTag tag) {
        tag.putUUID("physicsId", this.physicsId);
        tag.putString("objectTypeIdentifier", this.objectTypeIdentifier);
        CompoundTag transformTag = new CompoundTag();
        getCurrentTransform().toNbt(transformTag);
        tag.put("transform", transformTag);

        addAdditionalSaveData(tag);
        return tag;
    }

    @Override
    public final void loadFromNbt(CompoundTag tag) {
        if (tag.contains("transform", 10)) {
            currentTransform.fromNbt(tag.getCompound("transform"));
        }

        readAdditionalSaveData(tag);
    }

    protected static ListTag newFloatList(float... values) {
        ListTag tag = new ListTag();
        for (float v : values) tag.add(FloatTag.valueOf(v));
        return tag;
    }

    @Override @Nullable public BodyCreationSettings createBodyCreationSettings() { return null; }
    @Override @Nullable public SoftBodyCreationSettings createSoftBodyCreationSettings() { return null; }

    protected abstract void addAdditionalSaveData(CompoundTag tag);
    protected abstract void readAdditionalSaveData(CompoundTag tag);

    @Override public void onLeftClick(Player player, Vec3 hitPoint, Vec3 hitNormal) {}
    @Override public void onRightClick(Player player, Vec3 hitPoint, Vec3 hitNormal) {}

    @Override
    public void onRightClickWithTool(Player player) {
        if (player.getItemInHand(InteractionHand.MAIN_HAND).getItem()
                instanceof PhysicsRemoverItem) {
            this.markRemoved();
        }
    }

    @Override
    public void tryStartRiding(Player player, Vec3 hitPoint, Vec3 hitNormal) {
        if (level.isClientSide) return;
        for (Seat seat : seats.values()) {
            if (!seat.isOccupied() && seat.calculateWorldAABB().contains(hitPoint)) {
                RidingManager.mount((ServerPlayer) player, this, seat);
                return;
            }
        }
    }

    @Override
    public void gameTick(ServerLevel level) {
    }

    @Override
    public Collection<Seat> getSeats() {
        return seats.values();
    }

    @Override
    public Seat getSeat(String seatId) {
        return seats.get(seatId);
    }

    @Override
    public void addSeat(Seat seat) {
        this.seats.put(seat.getSeatId(), seat);
    }

    @Override
    public void removeSeat(String seatId) {
        this.seats.remove(seatId);
    }

    @Override
    public void markNbtDirty() {
        this.nbtDirty = true;
    }

    @Override
    public boolean isNbtDirty() {
        return this.nbtDirty;
    }

    @Override
    public void clearNbtDirty() {
        this.nbtDirty = false;
    }
}