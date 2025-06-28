package net.xmx.xbullet.item.physicsgun.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.xmx.xbullet.item.physicsgun.server.PhysicsGunServerHandler;

import java.util.function.Supplier;

public class C2SRequestPhysicsGunActionPacket {

    public enum ActionType {
        GRAB,
        RELEASE,
        FREEZE
    }

    private final ActionType actionType;

    public C2SRequestPhysicsGunActionPacket(ActionType actionType) {
        this.actionType = actionType;
    }

    public static void encode(C2SRequestPhysicsGunActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.actionType);
    }

    public static C2SRequestPhysicsGunActionPacket decode(FriendlyByteBuf buf) {
        return new C2SRequestPhysicsGunActionPacket(buf.readEnum(ActionType.class));
    }

    public static void handle(C2SRequestPhysicsGunActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            switch (msg.actionType) {
                case GRAB -> PhysicsGunServerHandler.handleGrabRequest(player);
                case RELEASE -> PhysicsGunServerHandler.handleReleaseRequest(player);
                case FREEZE -> PhysicsGunServerHandler.handleFreezeRequest(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}