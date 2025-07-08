package net.xmx.xbullet.physics.object.physicsobject.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.xmx.xbullet.physics.object.physicsobject.IPhysicsObject;
import net.xmx.xbullet.physics.object.physicsobject.client.ClientPhysicsObjectData;
import net.xmx.xbullet.physics.object.physicsobject.client.ClientPhysicsObjectManager;

import java.util.UUID;
import java.util.function.Supplier;

public class SyncPhysicsObjectNbtPacket {

    private final UUID id;
    private final CompoundTag nbt;

    public SyncPhysicsObjectNbtPacket(IPhysicsObject obj) {
        this.id = obj.getPhysicsId();
        this.nbt = obj.saveToNbt(new CompoundTag());
    }

    public SyncPhysicsObjectNbtPacket(FriendlyByteBuf buf) {
        this.id = buf.readUUID();
        this.nbt = buf.readNbt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeNbt(nbt);
    }

    public static void handle(SyncPhysicsObjectNbtPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientPhysicsObjectManager manager = ClientPhysicsObjectManager.getInstance();
            if (manager != null) {
                ClientPhysicsObjectData data = manager.getObjectData(msg.id);
                if (data != null) {

                    data.updateNbt(msg.nbt);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}