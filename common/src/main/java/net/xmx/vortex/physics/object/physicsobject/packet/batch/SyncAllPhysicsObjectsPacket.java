package net.xmx.vortex.physics.object.physicsobject.packet.batch;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.physics.object.physicsobject.client.ClientPhysicsObjectManager;
import net.xmx.vortex.physics.object.physicsobject.state.PhysicsObjectState;
import net.xmx.vortex.physics.object.physicsobject.state.PhysicsObjectStatePool;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncAllPhysicsObjectsPacket {
    private final List<PhysicsObjectState> objectStates;

    public SyncAllPhysicsObjectsPacket(List<PhysicsObjectState> objectStates) {
        this.objectStates = objectStates;
    }

    public SyncAllPhysicsObjectsPacket(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.objectStates = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            PhysicsObjectState state = PhysicsObjectStatePool.acquire();
            state.decode(buf);
            this.objectStates.add(state);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(objectStates.size());
        for (PhysicsObjectState state : objectStates) {
            state.encode(buf);
        }
    }

    public static void handle(SyncAllPhysicsObjectsPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            ClientPhysicsObjectManager.getInstance().scheduleStatesForUpdate(msg.objectStates);
        });
    }
}