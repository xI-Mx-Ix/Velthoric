package net.xmx.vortex.physics.object.physicsobject.packet.batch;

import dev.architectury.networking.NetworkManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.physics.object.physicsobject.client.ClientPhysicsObjectManager;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class RemovePhysicsObjectBatchPacket {

    private final List<UUID> ids;

    public RemovePhysicsObjectBatchPacket(List<UUID> ids) {
        this.ids = ids;
    }

    public RemovePhysicsObjectBatchPacket(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.ids = new ObjectArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.ids.add(buf.readUUID());
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(ids.size());
        for (UUID id : ids) {
            buf.writeUUID(id);
        }
    }

    public static void handle(RemovePhysicsObjectBatchPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            ClientPhysicsObjectManager manager = ClientPhysicsObjectManager.getInstance();
            for (UUID id : msg.ids) {
                manager.removeObject(id);
            }
        });
    }
}