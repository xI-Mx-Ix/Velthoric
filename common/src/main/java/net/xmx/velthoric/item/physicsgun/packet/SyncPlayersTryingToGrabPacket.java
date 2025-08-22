package net.xmx.velthoric.item.physicsgun.packet;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.velthoric.item.physicsgun.manager.PhysicsGunClientManager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class SyncPlayersTryingToGrabPacket {

    private final Set<UUID> playersTrying;

    public SyncPlayersTryingToGrabPacket(Set<UUID> playersTrying) {
        this.playersTrying = playersTrying;
    }

    public static void encode(SyncPlayersTryingToGrabPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.playersTrying.size());
        for (UUID uuid : msg.playersTrying) {
            buf.writeUUID(uuid);
        }
    }

    public static SyncPlayersTryingToGrabPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Set<UUID> playersTrying = new HashSet<>();
        for (int i = 0; i < size; i++) {
            playersTrying.add(buf.readUUID());
        }
        return new SyncPlayersTryingToGrabPacket(playersTrying);
    }

    public static void handle(SyncPlayersTryingToGrabPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            PhysicsGunClientManager.getInstance().getPlayersTryingToGrab().clear();
            PhysicsGunClientManager.getInstance().getPlayersTryingToGrab().addAll(msg.playersTrying);
        });
    }
}