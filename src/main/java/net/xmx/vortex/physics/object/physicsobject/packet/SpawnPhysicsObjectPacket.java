package net.xmx.vortex.physics.object.physicsobject.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.xmx.vortex.physics.object.physicsobject.EObjectType;
import net.xmx.vortex.physics.object.physicsobject.IPhysicsObject;
import net.xmx.vortex.physics.object.physicsobject.client.ClientPhysicsObjectManager;

import java.util.UUID;
import java.util.function.Supplier;

public class SpawnPhysicsObjectPacket {
    private final UUID id;
    private final String typeIdentifier;
    private final EObjectType objectType;
    private final byte[] data;
    private final long timestamp;

    public SpawnPhysicsObjectPacket(IPhysicsObject obj, long timestamp) {
        this.id = obj.getPhysicsId();
        this.typeIdentifier = obj.getObjectTypeIdentifier();
        this.objectType = obj.getPhysicsObjectType();
        this.timestamp = timestamp;

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        obj.writeSyncData(buf);

        this.data = new byte[buf.readableBytes()];
        buf.readBytes(this.data);
    }

    public SpawnPhysicsObjectPacket(FriendlyByteBuf buf) {
        this.id = buf.readUUID();
        this.typeIdentifier = buf.readUtf();
        this.objectType = buf.readEnum(EObjectType.class);
        this.timestamp = buf.readLong();
        this.data = buf.readByteArray();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(typeIdentifier);
        buf.writeEnum(objectType);
        buf.writeLong(timestamp);
        buf.writeByteArray(data);
    }

    public static void handle(SpawnPhysicsObjectPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            FriendlyByteBuf dataBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(msg.data));
            ClientPhysicsObjectManager.getInstance().spawnObject(
                    msg.id, msg.typeIdentifier, msg.objectType, dataBuf, msg.timestamp
            );
        });
        ctx.get().setPacketHandled(true);
    }
}