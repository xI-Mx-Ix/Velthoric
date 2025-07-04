package net.xmx.xbullet.physics.object.riding.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.xmx.xbullet.physics.object.riding.RidingManager;
import net.xmx.xbullet.physics.world.PhysicsWorld;

import java.util.function.Supplier;

public class DismountRequestPacket {

    public DismountRequestPacket() {

    }

    public static void encode(DismountRequestPacket msg, FriendlyByteBuf buf) {}

    public static DismountRequestPacket decode(FriendlyByteBuf buf) {
        return new DismountRequestPacket();
    }

    public static void handle(DismountRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            PhysicsWorld world = PhysicsWorld.get(sender.level().dimension());
            if (world != null) {
                RidingManager.dismount(sender.getUUID(), world);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}