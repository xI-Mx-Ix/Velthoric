package net.xmx.xbullet.item.physicsgun.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.xmx.xbullet.item.physicsgun.client.PhysicsGunClientHandler;

import java.util.function.Supplier;

public class S2CConfirmGrabPacket {

    private final boolean success;

    public S2CConfirmGrabPacket(boolean success) {
        this.success = success;
    }

    public static void encode(S2CConfirmGrabPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.success);
    }

    public static S2CConfirmGrabPacket decode(FriendlyByteBuf buf) {
        return new S2CConfirmGrabPacket(buf.readBoolean());
    }

    public static void handle(S2CConfirmGrabPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            PhysicsGunClientHandler.setHoldingObject(msg.success);
        });
        ctx.get().setPacketHandled(true);
    }
}