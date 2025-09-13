/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.physicsgun.packet;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.xmx.velthoric.item.physicsgun.manager.PhysicsGunClientManager;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PhysicsGunSyncPacket {

    private final Map<UUID, PhysicsGunClientManager.ClientGrabData> activeGrabs;
    private final Set<UUID> playersTryingToGrab;

    public PhysicsGunSyncPacket(Map<UUID, PhysicsGunClientManager.ClientGrabData> activeGrabs, Set<UUID> playersTryingToGrab) {
        this.activeGrabs = activeGrabs;
        this.playersTryingToGrab = playersTryingToGrab;
    }

    public static void encode(PhysicsGunSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.activeGrabs.size());
        for (Map.Entry<UUID, PhysicsGunClientManager.ClientGrabData> entry : msg.activeGrabs.entrySet()) {
            buf.writeUUID(entry.getKey());
            PhysicsGunClientManager.ClientGrabData data = entry.getValue();
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

    public static PhysicsGunSyncPacket decode(FriendlyByteBuf buf) {
        int grabsSize = buf.readVarInt();
        Map<UUID, PhysicsGunClientManager.ClientGrabData> activeGrabs = new ConcurrentHashMap<>();
        for (int i = 0; i < grabsSize; i++) {
            UUID playerUuid = buf.readUUID();
            UUID objectUuid = buf.readUUID();
            Vec3 localHitPoint = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            activeGrabs.put(playerUuid, new PhysicsGunClientManager.ClientGrabData(objectUuid, localHitPoint));
        }

        int tryingSize = buf.readVarInt();
        Set<UUID> playersTryingToGrab = IntStream.range(0, tryingSize)
                .mapToObj(i -> buf.readUUID())
                .collect(Collectors.toSet());

        return new PhysicsGunSyncPacket(activeGrabs, playersTryingToGrab);
    }

    public static void handle(PhysicsGunSyncPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            PhysicsGunClientManager.getInstance().updateState(msg.activeGrabs, msg.playersTryingToGrab);
        });
    }
}
