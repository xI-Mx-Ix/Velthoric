package net.xmx.xbullet.physics.object.physicsobject.state;

import com.github.stephengold.joltjni.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.physicsobject.IPhysicsObject;

import javax.annotation.Nullable;
import java.util.UUID;

public final class PhysicsObjectState {

    private UUID id;
    private EObjectType objectType;
    private final PhysicsTransform transform;
    private final Vec3 linearVelocity;
    private final Vec3 angularVelocity;
    private float[] softBodyVertices;
    private long timestamp;
    private boolean isActive;

    private static final ThreadLocal<CompoundTag> TEMP_NBT_TAG = ThreadLocal.withInitial(CompoundTag::new);

    public PhysicsObjectState() {
        this.transform = new PhysicsTransform();
        this.linearVelocity = new Vec3();
        this.angularVelocity = new Vec3();
    }

    public void from(IPhysicsObject obj, long timestamp, boolean isActive) {
        this.id = obj.getPhysicsId();
        this.objectType = obj.getPhysicsObjectType();
        this.transform.set(obj.getCurrentTransform());
        this.linearVelocity.set(obj.getLastSyncedLinearVel());
        this.angularVelocity.set(obj.getLastSyncedAngularVel());

        if (obj.getPhysicsObjectType() == EObjectType.SOFT_BODY) {
            this.softBodyVertices = obj.getLastSyncedVertexData();
        } else {
            this.softBodyVertices = null;
        }

        this.timestamp = timestamp;
        this.isActive = isActive;
    }

    public void decode(FriendlyByteBuf buf) {
        this.id = buf.readUUID();
        this.objectType = buf.readEnum(EObjectType.class);

        CompoundTag transformNbt = buf.readNbt();
        if (transformNbt != null) {
            this.transform.fromNbt(transformNbt);
        }

        if (buf.readBoolean()) {
            this.linearVelocity.set(buf.readFloat(), buf.readFloat(), buf.readFloat());
        } else {
            this.linearVelocity.loadZero();
        }

        if (buf.readBoolean()) {
            this.angularVelocity.set(buf.readFloat(), buf.readFloat(), buf.readFloat());
        } else {
            this.angularVelocity.loadZero();
        }

        if (buf.readBoolean()) {
            int length = buf.readVarInt();
            this.softBodyVertices = new float[length];
            for (int i = 0; i < length; i++) {
                this.softBodyVertices[i] = buf.readFloat();
            }
        } else {
            this.softBodyVertices = null;
        }

        this.timestamp = buf.readLong();
        this.isActive = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.id);
        buf.writeEnum(this.objectType);

        CompoundTag tempTag = TEMP_NBT_TAG.get();

        tempTag.getAllKeys().clear();

        this.transform.toNbt(tempTag);

        buf.writeNbt(tempTag);

        boolean hasLinVel = !this.linearVelocity.isNearZero(1e-4f);
        buf.writeBoolean(hasLinVel);
        if (hasLinVel) {
            buf.writeFloat(this.linearVelocity.getX());
            buf.writeFloat(this.linearVelocity.getY());
            buf.writeFloat(this.linearVelocity.getZ());
        }

        boolean hasAngVel = !this.angularVelocity.isNearZero(1e-4f);
        buf.writeBoolean(hasAngVel);
        if (hasAngVel) {
            buf.writeFloat(this.angularVelocity.getX());
            buf.writeFloat(this.angularVelocity.getY());
            buf.writeFloat(this.angularVelocity.getZ());
        }

        boolean hasVertices = this.softBodyVertices != null && this.softBodyVertices.length > 0;
        buf.writeBoolean(hasVertices);
        if (hasVertices) {
            buf.writeVarInt(this.softBodyVertices.length);
            for (float v : this.softBodyVertices) {
                buf.writeFloat(v);
            }
        }

        buf.writeLong(this.timestamp);
        buf.writeBoolean(this.isActive);
    }

    public int estimateEncodedSize() {
        int size = 36;
        size += 1;
        if (!this.linearVelocity.isNearZero(1e-4f)) size += 12;
        size += 1;
        if (!this.angularVelocity.isNearZero(1e-4f)) size += 12;
        size += 1;
        if (this.softBodyVertices != null) size += 4 + this.softBodyVertices.length * 4;

        return size;
    }

    public void reset() {
        this.id = null;
        this.objectType = null;
        this.softBodyVertices = null;
    }

    public UUID id() {
        return id;
    }

    public EObjectType objectType() {
        return objectType;
    }

    public PhysicsTransform transform() {
        return transform;
    }

    public Vec3 linearVelocity() {
        return linearVelocity;
    }

    public Vec3 angularVelocity() {
        return angularVelocity;
    }

    @Nullable
    public float[] softBodyVertices() {
        return softBodyVertices;
    }

    public long timestamp() {
        return timestamp;
    }

    public boolean isActive() {
        return isActive;
    }

}