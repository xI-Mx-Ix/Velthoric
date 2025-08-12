package net.xmx.vortex.item.physicsgun.packet;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.xmx.vortex.item.physicsgun.manager.PhysicsGunClientManager;

import java.util.UUID;
import java.util.function.Supplier;

public class PlayerTryingStatePacket {

    private final UUID playerUuid;
    private final boolean isTrying;

    public PlayerTryingStatePacket(UUID playerUuid, boolean isTrying) {
        this.playerUuid = playerUuid;
        this.isTrying = isTrying;
    }

    public static void encode(PlayerTryingStatePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerUuid);
        buf.writeBoolean(msg.isTrying);
    }

    public static PlayerTryingStatePacket decode(FriendlyByteBuf buf) {
        return new PlayerTryingStatePacket(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(PlayerTryingStatePacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            PhysicsGunClientManager manager = PhysicsGunClientManager.getInstance();
            if (msg.isTrying) {
                manager.getPlayersTryingToGrab().add(msg.playerUuid);
            } else {
                manager.getPlayersTryingToGrab().remove(msg.playerUuid);
            }
        });
    }
}