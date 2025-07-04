package net.xmx.xbullet.physics.object.global.physicsobject;

import com.github.stephengold.joltjni.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.softphysicsobject.SoftPhysicsObject;

import javax.annotation.Nullable;
import java.util.UUID;

public record PhysicsObjectState(
        UUID id,
        EObjectType objectType,
        PhysicsTransform transform,
        Vec3 linearVelocity,
        Vec3 angularVelocity,
        @Nullable float[] softBodyVertices,
        long timestamp,
        boolean isActive
) {

    public static PhysicsObjectState from(IPhysicsObject obj, long timestamp, boolean isActive) {
        float[] vertices = null;
        if (obj instanceof SoftPhysicsObject spo) {
            vertices = spo.getLastSyncedVertexData();
        }
        return new PhysicsObjectState(
                obj.getPhysicsId(),
                obj.getPhysicsObjectType(),
                obj.getCurrentTransform(),
                obj.getLastSyncedLinearVel(),
                obj.getLastSyncedAngularVel(),
                vertices,
                timestamp,
                isActive
        );
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeEnum(objectType);

        CompoundTag transformNbt = new CompoundTag();
        transform.toNbt(transformNbt);
        buf.writeNbt(transformNbt);

        buf.writeFloat(linearVelocity.getX());
        buf.writeFloat(linearVelocity.getY());
        buf.writeFloat(linearVelocity.getZ());
        buf.writeFloat(angularVelocity.getX());
        buf.writeFloat(angularVelocity.getY());
        buf.writeFloat(angularVelocity.getZ());

        buf.writeBoolean(softBodyVertices != null);
        if (softBodyVertices != null) {
            buf.writeVarInt(softBodyVertices.length);
            for (float value : softBodyVertices) {
                buf.writeFloat(value);
            }
        }

        buf.writeLong(timestamp);
        buf.writeBoolean(isActive);
    }

    public static PhysicsObjectState decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        EObjectType objectType = buf.readEnum(EObjectType.class);

        CompoundTag transformNbt = buf.readNbt();
        PhysicsTransform transform = PhysicsTransform.createFromNbt(transformNbt);

        Vec3 linearVelocity = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        Vec3 angularVelocity = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());

        float[] softBodyVertices = null;
        if (buf.readBoolean()) {
            int length = buf.readVarInt();
            softBodyVertices = new float[length];
            for (int i = 0; i < length; i++) {
                softBodyVertices[i] = buf.readFloat();
            }
        }

        long timestamp = buf.readLong();
        boolean isActive = buf.readBoolean();

        return new PhysicsObjectState(id, objectType, transform, linearVelocity, angularVelocity, softBodyVertices, timestamp, isActive);
    }

    public int estimateEncodedSize() {
        int size = 0;
        size += 16;
        size += 1;
        size += 80;
        size += 12;
        size += 12;
        size += 1;
        if (this.softBodyVertices != null) {
            size += 4;
            size += this.softBodyVertices.length * 4;
        }
        size += 8;
        size += 1;
        return size;
    }
}