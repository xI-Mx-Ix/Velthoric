package net.xmx.xbullet.physics.object.global.physicsobject.packet;

import com.github.stephengold.joltjni.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.xmx.xbullet.math.PhysicsTransform;
import net.xmx.xbullet.physics.object.global.physicsobject.EObjectType;
import net.xmx.xbullet.physics.object.global.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.global.physicsobject.client.ClientPhysicsObjectManager;
import net.xmx.xbullet.physics.object.softphysicsobject.SoftPhysicsObject;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

public class SyncPhysicsObjectPacket {
    private final UUID id;
    private final EObjectType objectType;
    private final PhysicsTransform transform;
    private final Vec3 linearVelocity;
    private final Vec3 angularVelocity;
    @Nullable private final float[] softBodyVertices;
    private final long timestamp;
    private final boolean isActive;

    public SyncPhysicsObjectPacket(IPhysicsObject obj, long timestamp, boolean isActive) {
        this.id = obj.getPhysicsId();
        this.objectType = obj.getPhysicsObjectType();
        this.transform = obj.getCurrentTransform();
        this.linearVelocity = obj.getLastSyncedLinearVel();
        this.angularVelocity = obj.getLastSyncedAngularVel();
        this.timestamp = timestamp;
        this.isActive = isActive;

        if (obj instanceof SoftPhysicsObject spo) {
            this.softBodyVertices = spo.lastSyncedVertexData;
        } else {
            this.softBodyVertices = null;
        }
    }

    public SyncPhysicsObjectPacket(FriendlyByteBuf buf) {
        this.id = buf.readUUID();
        this.objectType = buf.readEnum(EObjectType.class);

        CompoundTag transformNbt = buf.readNbt();
        this.transform = PhysicsTransform.createFromNbt(transformNbt);

        this.linearVelocity = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        this.angularVelocity = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());

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
        buf.writeUUID(id);
        buf.writeEnum(objectType);

        CompoundTag transformNbt = new CompoundTag();
        transform.toNbt(transformNbt);
        buf.writeNbt(transformNbt);

        buf.writeFloat(linearVelocity.getX()); buf.writeFloat(linearVelocity.getY()); buf.writeFloat(linearVelocity.getZ());
        buf.writeFloat(angularVelocity.getX()); buf.writeFloat(angularVelocity.getY()); buf.writeFloat(angularVelocity.getZ());

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

    public static void handle(SyncPhysicsObjectPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientPhysicsObjectManager.getInstance().updateObject(
                msg.id, msg.objectType, msg.transform, msg.linearVelocity, msg.angularVelocity,
                msg.softBodyVertices, null, msg.timestamp, msg.isActive
        ));
        ctx.get().setPacketHandled(true);
    }
}