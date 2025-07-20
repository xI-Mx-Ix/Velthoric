package net.xmx.xbullet.item.physicsgun.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.xmx.xbullet.item.physicsgun.manager.PhysicsGunClientManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class SyncAllPhysicsGunGrabsPacket {

    private final Map<UUID, UUID> allGrabs;

    public SyncAllPhysicsGunGrabsPacket(Map<UUID, UUID> allGrabs) {
        this.allGrabs = allGrabs;
    }

    public static void encode(SyncAllPhysicsGunGrabsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.allGrabs.size());
        for (Map.Entry<UUID, UUID> entry : msg.allGrabs.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeUUID(entry.getValue());
        }
    }

    public static SyncAllPhysicsGunGrabsPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<UUID, UUID> allGrabs = new ConcurrentHashMap<>();
        for (int i = 0; i < size; i++) {
            allGrabs.put(buf.readUUID(), buf.readUUID());
        }
        return new SyncAllPhysicsGunGrabsPacket(allGrabs);
    }

    public static void handle(SyncAllPhysicsGunGrabsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            PhysicsGunClientManager.getInstance().setFullGrabState(msg.allGrabs);
        });
        ctx.get().setPacketHandled(true);
    }
}