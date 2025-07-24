package net.xmx.vortex.physics.object.physicsobject.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.xmx.vortex.physics.object.physicsobject.client.ClientPhysicsObjectManager;

import java.util.UUID;
import java.util.function.Supplier;

public class RemovePhysicsObjectPacket {

    private final UUID id;

    public RemovePhysicsObjectPacket(UUID id) {
        this.id = id;
    }

    public RemovePhysicsObjectPacket(FriendlyByteBuf buf) {
        this.id = buf.readUUID();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.id);
    }

    public static void handle(RemovePhysicsObjectPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {

            ClientPhysicsObjectManager.getInstance().removeObject(msg.id);
        });
        ctx.get().setPacketHandled(true);
    }
}