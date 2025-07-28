package net.xmx.vortex.physics.object.raycast.packet;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.vortex.physics.object.raycast.PhysicsClickManager;

import java.util.function.Supplier;

public record PhysicsClickPacket(
        float rayOriginX, float rayOriginY, float rayOriginZ,
        float rayDirectionX, float rayDirectionY, float rayDirectionZ,
        boolean isRightClick) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeFloat(rayOriginX);
        buffer.writeFloat(rayOriginY);
        buffer.writeFloat(rayOriginZ);
        buffer.writeFloat(rayDirectionX);
        buffer.writeFloat(rayDirectionY);
        buffer.writeFloat(rayDirectionZ);
        buffer.writeBoolean(isRightClick);
    }

    public static PhysicsClickPacket decode(FriendlyByteBuf buffer) {
        return new PhysicsClickPacket(
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readBoolean()
        );
    }

    public static void handle(PhysicsClickPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player != null) {
                PhysicsClickManager.processClick(msg, player);
            }
        });
    }
}