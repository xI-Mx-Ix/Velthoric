package net.xmx.xbullet.command.xbullet.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.xmx.xbullet.init.XBullet;

import java.util.function.Supplier;

public record ClientPhysicsObjectCountResponsePacket(int rigidCount, int softCount) {

    public static ClientPhysicsObjectCountResponsePacket read(FriendlyByteBuf buf) {
        int rigidCount = buf.readInt();
        int softCount = buf.readInt();
        return new ClientPhysicsObjectCountResponsePacket(rigidCount, softCount);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(rigidCount);
        buf.writeInt(softCount);
    }

    public static class Handler {
        public static void handle(ClientPhysicsObjectCountResponsePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection().getReceptionSide().isServer()) {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) {
                        int receivedRigidCount = msg.rigidCount();
                        int receivedSoftCount = msg.softCount();
                        int totalCount = receivedRigidCount + receivedSoftCount;

                        XBullet.LOGGER.debug("Server received ClientPhysicsObjectCountResponsePacket from {} with counts: rigid={}, soft={}",
                                sender.getName().getString(), receivedRigidCount, receivedSoftCount);

                        String response = String.format("Client '%s' has %d rigid bodies and %d soft bodies (Total: %d).",
                                sender.getName().getString(), receivedRigidCount, receivedSoftCount, totalCount);
                        sender.sendSystemMessage(Component.literal(response));
                    } else {
                        XBullet.LOGGER.warn("Received ClientPhysicsObjectCountResponsePacket but sender is null.");
                    }
                } else {
                    XBullet.LOGGER.warn("ClientPhysicsObjectCountResponsePacket received on wrong side: {}", ctx.get().getDirection().getReceptionSide());
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}