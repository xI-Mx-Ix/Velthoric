package net.xmx.xbullet.physics.object.global.physicsobject.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.xmx.xbullet.physics.object.global.physicsobject.client.ClientPhysicsObjectManager;
import net.xmx.xbullet.physics.object.global.physicsobject.PhysicsObjectState;

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
            this.objectStates.add(PhysicsObjectState.decode(buf));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(objectStates.size());
        for (PhysicsObjectState state : objectStates) {
            state.encode(buf);
        }
    }

    public static void handle(SyncAllPhysicsObjectsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientPhysicsObjectManager manager = ClientPhysicsObjectManager.getInstance();

            for (PhysicsObjectState state : msg.objectStates) {
                manager.updateObject(
                    state.id(),
                    state.objectType(),
                    state.transform(),
                    state.linearVelocity(),
                    state.angularVelocity(),
                    state.softBodyVertices(),
                    null, 
                    state.timestamp(),
                    state.isActive()
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }
}