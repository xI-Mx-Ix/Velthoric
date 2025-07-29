package net.xmx.vortex.physics.object.physicsobject.state;

import com.github.stephengold.joltjni.Vec3;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.math.VxTransform;
import net.xmx.vortex.physics.object.physicsobject.EObjectType;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class PhysicsObjectState {

    private UUID id;
    private EObjectType eobjectType;
    private final VxTransform transform;
    private final Vec3 linearVelocity;
    private final Vec3 angularVelocity;
    private float[] softBodyVertices;
    private long timestamp;
    private boolean isActive;

    public PhysicsObjectState() {
        this.transform = new VxTransform();
        this.linearVelocity = new Vec3();
        this.angularVelocity = new Vec3();
    }

    public void from(IPhysicsObject obj, long timestamp, boolean isActive) {
        this.id = obj.getPhysicsId();
        this.eobjectType = obj.getEObjectType();
        this.transform.set(obj.getCurrentTransform());
        this.timestamp = timestamp;
        this.isActive = isActive;

        if (isActive) {
            this.linearVelocity.set(obj.getLastSyncedLinearVel());
            this.angularVelocity.set(obj.getLastSyncedAngularVel());
            if (obj.getEObjectType() == EObjectType.SOFT_BODY) {
                this.softBodyVertices = obj.getLastSyncedVertexData();
            } else {
                this.softBodyVertices = null;
            }
        } else {
            this.linearVelocity.loadZero();
            this.angularVelocity.loadZero();
            this.softBodyVertices = null;
        }
    }

    public void decode(FriendlyByteBuf buf) {
        this.id = buf.readUUID();
        this.timestamp = buf.readLong();
        this.isActive = buf.readBoolean();
        this.eobjectType = buf.readEnum(EObjectType.class);
        this.transform.fromBuffer(buf);

        if (this.isActive) {
            this.linearVelocity.set(buf.readFloat(), buf.readFloat(), buf.readFloat());
            this.angularVelocity.set(buf.readFloat(), buf.readFloat(), buf.readFloat());

            if (eobjectType == EObjectType.SOFT_BODY && buf.readBoolean()) {
                int length = buf.readVarInt();
                this.softBodyVertices = new float[length];
                for (int i = 0; i < length; i++) {
                    this.softBodyVertices[i] = buf.readFloat();
                }
            } else {
                this.softBodyVertices = null;
            }
        } else {
            this.linearVelocity.loadZero();
            this.angularVelocity.loadZero();
            this.softBodyVertices = null;
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.id);
        buf.writeLong(this.timestamp);
        buf.writeBoolean(this.isActive);
        buf.writeEnum(this.eobjectType);
        this.transform.toBuffer(buf);

        if (this.isActive) {
            buf.writeFloat(this.linearVelocity.getX());
            buf.writeFloat(this.linearVelocity.getY());
            buf.writeFloat(this.linearVelocity.getZ());
            buf.writeFloat(this.angularVelocity.getX());
            buf.writeFloat(this.angularVelocity.getY());
            buf.writeFloat(this.angularVelocity.getZ());

            if (eobjectType == EObjectType.SOFT_BODY) {
                boolean hasVertices = this.softBodyVertices != null && this.softBodyVertices.length > 0;
                buf.writeBoolean(hasVertices);
                if (hasVertices) {
                    buf.writeVarInt(this.softBodyVertices.length);
                    for (float v : this.softBodyVertices) {
                        buf.writeFloat(v);
                    }
                }
            }
        }
    }

    public int estimateEncodedSize() {
        int size = 16 + 8 + 1 + 1 + 40;
        if(isActive) {
            size += 12 + 12;
            if(eobjectType == EObjectType.SOFT_BODY) {
                size += 1;
                if(this.softBodyVertices != null && this.softBodyVertices.length > 0) {
                    size += 5 + this.softBodyVertices.length * 4;
                }
            }
        }
        return size;
    }

    public void reset() {
        this.id = null;
        this.eobjectType = null;
        this.softBodyVertices = null;
        this.timestamp = 0L;
        this.isActive = false;
        this.linearVelocity.loadZero();
        this.angularVelocity.loadZero();
        this.transform.loadIdentity();
    }

    public UUID getId() {
        return id;
    }

    public EObjectType getEObjectType() {
        return eobjectType;
    }

    public VxTransform getTransform() {
        return transform;
    }

    public Vec3 getLinearVelocity() {
        return linearVelocity;
    }

    public Vec3 getAngularVelocity() {
        return angularVelocity;
    }

    @Nullable
    public float[] getSoftBodyVertices() {
        return softBodyVertices;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isActive() {
        return isActive;
    }

}