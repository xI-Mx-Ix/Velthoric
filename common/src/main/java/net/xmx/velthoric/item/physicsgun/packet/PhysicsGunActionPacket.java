/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.physicsgun.packet;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.item.physicsgun.manager.PhysicsGunServerManager;

import java.util.function.Supplier;

public class PhysicsGunActionPacket {

    private final ActionType actionType;
    private final float value1;
    private final float value2;

    public enum ActionType {
        START_GRAB_ATTEMPT,
        STOP_GRAB_ATTEMPT,
        UPDATE_SCROLL,
        UPDATE_ROTATION,
        FREEZE_OBJECT,
        START_ROTATION_MODE,
        STOP_ROTATION_MODE
    }

    public PhysicsGunActionPacket(ActionType actionType) {
        this(actionType, 0, 0);
    }

    public PhysicsGunActionPacket(float scrollDelta) {
        this(ActionType.UPDATE_SCROLL, scrollDelta, 0);
    }

    public PhysicsGunActionPacket(float deltaX, float deltaY) {
        this(ActionType.UPDATE_ROTATION, deltaX, deltaY);
    }

    private PhysicsGunActionPacket(ActionType actionType, float value1, float value2) {
        this.actionType = actionType;
        this.value1 = value1;
        this.value2 = value2;
    }

    public static void encode(PhysicsGunActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.actionType);
        if (msg.actionType == ActionType.UPDATE_SCROLL) {
            buf.writeFloat(msg.value1);
        } else if (msg.actionType == ActionType.UPDATE_ROTATION) {
            buf.writeFloat(msg.value1);
            buf.writeFloat(msg.value2);
        }
    }

    public static PhysicsGunActionPacket decode(FriendlyByteBuf buf) {
        ActionType actionType = buf.readEnum(ActionType.class);
        float value1 = 0, value2 = 0;
        if (actionType == ActionType.UPDATE_SCROLL) {
            value1 = buf.readFloat();
        } else if (actionType == ActionType.UPDATE_ROTATION) {
            value1 = buf.readFloat();
            value2 = buf.readFloat();
        }
        return new PhysicsGunActionPacket(actionType, value1, value2);
    }

    public static void handle(PhysicsGunActionPacket msg, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player == null) return;

            PhysicsGunServerManager manager = PhysicsGunServerManager.getInstance();
            switch (msg.actionType) {
                case START_GRAB_ATTEMPT -> manager.startGrabAttempt(player);
                case STOP_GRAB_ATTEMPT -> manager.stopGrabAttempt(player);
                case UPDATE_SCROLL -> manager.updateScroll(player, msg.value1);
                case UPDATE_ROTATION -> manager.updateRotation(player, msg.value1, msg.value2);
                case FREEZE_OBJECT -> manager.freezeObject(player);
                case START_ROTATION_MODE -> manager.startRotationMode(player);
                case STOP_ROTATION_MODE -> manager.stopRotationMode(player);
            }
        });
    }
}
