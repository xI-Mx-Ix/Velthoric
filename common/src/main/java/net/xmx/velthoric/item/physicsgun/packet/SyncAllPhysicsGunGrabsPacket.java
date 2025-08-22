package net.xmx.velthoric.item.physicsgun.packet;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.item.physicsgun.manager.PhysicsGunClientManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class SyncAllPhysicsGunGrabsPacket {

    private final Map<UUID, PhysicsGunClientManager.ClientGrabData> allGrabs;

    public SyncAllPhysicsGunGrabsPacket(Map<UUID, PhysicsGunClientManager.ClientGrabData> allGrabs) {
        this.allGrabs = allGrabs;
    }

    public static void encode(SyncAllPhysicsGunGrabsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.allGrabs.size());
        for (Map.Entry<UUID, PhysicsGunClientManager.ClientGrabData> entry : msg.allGrabs.entrySet()) {
            buf.writeUUID(entry.getKey());
            PhysicsGunClientManager.ClientGrabData data = entry.getValue();
            buf.writeUUID(data.objectUuid());
            buf.writeDouble(data.localHitPoint().x());
            buf.writeDouble(data.localHitPoint().y());
            buf.writeDouble(data.localHitPoint().z());
        }
    }

    public static SyncAllPhysicsGunGrabsPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<UUID, PhysicsGunClientManager.ClientGrabData> allGrabs = new ConcurrentHashMap<>();
        for (int i = 0; i < size; i++) {
            UUID playerUuid = buf.readUUID();
            UUID objectUuid = buf.readUUID();
            Vec3 localHitPoint = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            allGrabs.put(playerUuid, new PhysicsGunClientManager.ClientGrabData(objectUuid, localHitPoint));
        }
        return new SyncAllPhysicsGunGrabsPacket(allGrabs);
    }

    public static void handle(SyncAllPhysicsGunGrabsPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            PhysicsGunClientManager.getInstance().setFullGrabState(msg.allGrabs);
        });
    }
}