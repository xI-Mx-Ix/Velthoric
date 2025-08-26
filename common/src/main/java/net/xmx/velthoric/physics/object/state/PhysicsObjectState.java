package net.xmx.velthoric.physics.object.state;

import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import net.xmx.velthoric.math.VxTransform;
import net.xmx.velthoric.network.VxByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class PhysicsObjectState {

    private UUID id;
    private EBodyType eBodyType;
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

    public void from(UUID id, EBodyType eBodyType, VxTransform transform, @Nullable Vec3 linearVelocity, @Nullable Vec3 angularVelocity, @Nullable float[] softBodyVertices, long timestamp, boolean isActive) {
        this.id = id;
        this.eBodyType = eBodyType;
        this.transform.set(transform);
        this.timestamp = timestamp;
        this.isActive = isActive;

        if (isActive) {
            if (linearVelocity != null) this.linearVelocity.set(linearVelocity); else this.linearVelocity.loadZero();
            if (angularVelocity != null) this.angularVelocity.set(angularVelocity); else this.angularVelocity.loadZero();
            this.softBodyVertices = softBodyVertices;
        } else {
            this.linearVelocity.loadZero();
            this.angularVelocity.loadZero();
            this.softBodyVertices = null;
        }
    }

    public void decode(VxByteBuf buf) {
        this.id = buf.readUUID();
        this.timestamp = buf.readLong();
        this.isActive = buf.readBoolean();
        this.eBodyType = buf.readEnum(EBodyType.class);
        this.transform.fromBuffer(buf);

        if (this.isActive) {
            this.linearVelocity.set(buf.readVec3());
            this.angularVelocity.set(buf.readVec3());

            if (eBodyType == EBodyType.SoftBody && buf.readBoolean()) {
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

    public void encode(VxByteBuf buf) {
        buf.writeUUID(this.id);
        buf.writeLong(this.timestamp);
        buf.writeBoolean(this.isActive);
        buf.writeEnum(this.eBodyType);
        this.transform.toBuffer(buf);

        if (this.isActive) {
            buf.writeVec3(this.linearVelocity);
            buf.writeVec3(this.angularVelocity);

            if (eBodyType == EBodyType.SoftBody) {
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
        int size = 16 + 8 + 1 + 4 + 40;
        if(isActive) {
            size += 12 + 12;
            if(eBodyType == EBodyType.SoftBody) {
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
        this.eBodyType = null;
        this.softBodyVertices = null;
        this.timestamp = 0L;
        this.isActive = false;
        this.linearVelocity.loadZero();
        this.angularVelocity.loadZero();
        this.transform.loadIdentity();
    }

    public UUID getId() { return id; }
    public EBodyType getEObjectType() { return eBodyType; }
    public VxTransform getTransform() { return transform; }
    public Vec3 getLinearVelocity() { return linearVelocity; }
    public Vec3 getAngularVelocity() { return angularVelocity; }
    public float @Nullable [] getSoftBodyVertices() { return softBodyVertices; }
    public long getTimestamp() { return timestamp; }
    public boolean isActive() { return isActive; }
}