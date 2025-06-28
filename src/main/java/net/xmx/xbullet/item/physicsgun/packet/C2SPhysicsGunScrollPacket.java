package net.xmx.xbullet.item.physicsgun.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.xmx.xbullet.item.physicsgun.server.PhysicsGunServerHandler;

import java.util.function.Supplier;

public class C2SPhysicsGunScrollPacket {

    private final float scrollDelta;

    public C2SPhysicsGunScrollPacket(float scrollDelta) {
        this.scrollDelta = scrollDelta;
    }

    public static void encode(C2SPhysicsGunScrollPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.scrollDelta);
    }

    public static C2SPhysicsGunScrollPacket decode(FriendlyByteBuf buf) {
        return new C2SPhysicsGunScrollPacket(buf.readFloat());
    }

    public static void handle(C2SPhysicsGunScrollPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            PhysicsGunServerHandler.handleScrollUpdate(player, msg.scrollDelta);
        });
        ctx.get().setPacketHandled(true);
    }
}