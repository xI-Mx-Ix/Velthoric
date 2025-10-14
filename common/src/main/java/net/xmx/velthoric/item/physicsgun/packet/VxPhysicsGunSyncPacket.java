/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.physicsgun.packet;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.item.physicsgun.manager.VxPhysicsGunClientManager;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class VxPhysicsGunSyncPacket {

    private final Map<UUID, VxPhysicsGunClientManager.ClientGrabData> activeGrabs;
    private final Set<UUID> playersTryingToGrab;

    public VxPhysicsGunSyncPacket(Map<UUID, VxPhysicsGunClientManager.ClientGrabData> activeGrabs, Set<UUID> playersTryingToGrab) {
        this.activeGrabs = activeGrabs;
        this.playersTryingToGrab = playersTryingToGrab;
    }

    public static void encode(VxPhysicsGunSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.activeGrabs.size());
        for (Map.Entry<UUID, VxPhysicsGunClientManager.ClientGrabData> entry : msg.activeGrabs.entrySet()) {
            buf.writeUUID(entry.getKey());
            VxPhysicsGunClientManager.ClientGrabData data = entry.getValue();
            buf.writeUUID(data.objectUuid());
            buf.writeDouble(data.localHitPoint().x());
            buf.writeDouble(data.localHitPoint().y());
            buf.writeDouble(data.localHitPoint().z());
        }

        buf.writeVarInt(msg.playersTryingToGrab.size());
        for (UUID uuid : msg.playersTryingToGrab) {
            buf.writeUUID(uuid);
        }
    }

    public static VxPhysicsGunSyncPacket decode(FriendlyByteBuf buf) {
        int grabsSize = buf.readVarInt();
        Map<UUID, VxPhysicsGunClientManager.ClientGrabData> activeGrabs = new ConcurrentHashMap<>();
        for (int i = 0; i < grabsSize; i++) {
            UUID playerUuid = buf.readUUID();
            UUID objectUuid = buf.readUUID();
            Vec3 localHitPoint = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            activeGrabs.put(playerUuid, new VxPhysicsGunClientManager.ClientGrabData(objectUuid, localHitPoint));
        }

        int tryingSize = buf.readVarInt();
        Set<UUID> playersTryingToGrab = IntStream.range(0, tryingSize)
                .mapToObj(i -> buf.readUUID())
                .collect(Collectors.toSet());

        return new VxPhysicsGunSyncPacket(activeGrabs, playersTryingToGrab);
    }

    public static void handle(VxPhysicsGunSyncPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            VxPhysicsGunClientManager.getInstance().updateState(msg.activeGrabs, msg.playersTryingToGrab);
        });
    }
}
