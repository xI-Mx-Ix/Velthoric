package net.xmx.xbullet.item.physicsgun.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.xmx.xbullet.item.physicsgun.PhysicsGunManager;

import java.util.function.Supplier;

public class PhysicsGunActionPacket {

    private final ActionType actionType;
    private final float scrollDelta;

    public enum ActionType {
        START_GRAB_ATTEMPT,
        STOP_GRAB_ATTEMPT,
        UPDATE_SCROLL,
        FREEZE_OBJECT,
    }

    public PhysicsGunActionPacket(ActionType actionType) {
        this(actionType, 0);
    }

    public PhysicsGunActionPacket(float scrollDelta) {
        this(ActionType.UPDATE_SCROLL, scrollDelta);
    }

    private PhysicsGunActionPacket(ActionType actionType, float scrollDelta) {
        this.actionType = actionType;
        this.scrollDelta = scrollDelta;
    }

    public static void encode(PhysicsGunActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.actionType);
        if (msg.actionType == ActionType.UPDATE_SCROLL) {
            buf.writeFloat(msg.scrollDelta);
        }
    }

    public static PhysicsGunActionPacket decode(FriendlyByteBuf buf) {
        var actionType = buf.readEnum(ActionType.class);
        var scrollDelta = (actionType == ActionType.UPDATE_SCROLL) ? buf.readFloat() : 0;
        return new PhysicsGunActionPacket(actionType, scrollDelta);
    }

    public static void handle(PhysicsGunActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            var manager = PhysicsGunManager.getInstance();
            // GEÄNDERT: Neue Manager-Methoden aufrufen
            switch (msg.actionType) {
                case START_GRAB_ATTEMPT -> manager.startGrabAttempt(player);
                case STOP_GRAB_ATTEMPT -> manager.stopGrabAttempt(player);
                case UPDATE_SCROLL -> manager.updateScroll(player, msg.scrollDelta);
                case FREEZE_OBJECT -> manager.freezeObject(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}