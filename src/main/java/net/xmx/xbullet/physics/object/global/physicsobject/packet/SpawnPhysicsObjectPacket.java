package net.xmx.xbullet.physics.object.global.physicsobject.packet;

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

public class SpawnPhysicsObjectPacket {
    private final UUID id;
    private final String typeIdentifier;
    private final EObjectType objectType;
    private final PhysicsTransform transform;
    private final CompoundTag nbt;
    @Nullable
    private final float[] softBodyVertices;
    private final long timestamp;

    public SpawnPhysicsObjectPacket(IPhysicsObject obj, long timestamp) {
        this.id = obj.getPhysicsId();
        this.typeIdentifier = obj.getObjectTypeIdentifier();
        this.objectType = obj.getPhysicsObjectType();
        this.transform = obj.getCurrentTransform();
        this.nbt = obj.saveToNbt(new CompoundTag());
        this.timestamp = timestamp;

        if (obj instanceof SoftPhysicsObject spo) {
            this.softBodyVertices = spo.lastSyncedVertexData;
        } else {
            this.softBodyVertices = null;
        }
    }

    public SpawnPhysicsObjectPacket(FriendlyByteBuf buf) {
        this.id = buf.readUUID();
        this.typeIdentifier = buf.readUtf();
        this.objectType = buf.readEnum(EObjectType.class);

        CompoundTag transformNbt = buf.readNbt();
        this.transform = PhysicsTransform.createFromNbt(transformNbt);

        this.nbt = buf.readNbt();

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
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(typeIdentifier);
        buf.writeEnum(objectType);

        CompoundTag transformNbt = new CompoundTag();
        transform.toNbt(transformNbt);
        buf.writeNbt(transformNbt);

        buf.writeNbt(nbt);

        buf.writeBoolean(softBodyVertices != null);
        if (softBodyVertices != null) {
            buf.writeVarInt(softBodyVertices.length);
            for (float value : softBodyVertices) {
                buf.writeFloat(value);
            }
        }

        buf.writeLong(timestamp);
    }

    public static void handle(SpawnPhysicsObjectPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientPhysicsObjectManager.getInstance().spawnObject(
                    msg.id, msg.typeIdentifier, msg.objectType, msg.transform,
                    msg.softBodyVertices, msg.nbt, msg.timestamp
            );
        });
        ctx.get().setPacketHandled(true);
    }
}