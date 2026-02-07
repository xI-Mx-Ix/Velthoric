/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.physicsgun.packet;

import dev.architectury.networking.NetworkManager;
import net.minecraft.server.level.ServerPlayer;
import net.xmx.velthoric.item.physicsgun.manager.VxPhysicsGunServerManager;
import net.xmx.velthoric.network.IVxNetPacket;
import net.xmx.velthoric.network.VxByteBuf;

/**
 * A packet sent from the client to the server to perform actions with the physics gun.
 * <p>
 * Actions include grabbing, scrolling distance, rotating objects, and freezing bodies.
 * </p>
 *
 * @author xI-Mx-Ix
 */
public class VxPhysicsGunActionPacket implements IVxNetPacket {

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

    public VxPhysicsGunActionPacket(ActionType actionType) {
        this(actionType, 0, 0);
    }

    public VxPhysicsGunActionPacket(float scrollDelta) {
        this(ActionType.UPDATE_SCROLL, scrollDelta, 0);
    }

    public VxPhysicsGunActionPacket(float deltaX, float deltaY) {
        this(ActionType.UPDATE_ROTATION, deltaX, deltaY);
    }

    private VxPhysicsGunActionPacket(ActionType actionType, float value1, float value2) {
        this.actionType = actionType;
        this.value1 = value1;
        this.value2 = value2;
    }

    /**
     * Decodes the packet from the buffer.
     *
     * @param buf The network buffer.
     * @return The decoded packet.
     */
    public static VxPhysicsGunActionPacket decode(VxByteBuf buf) {
        ActionType actionType = buf.readEnum(ActionType.class);
        float value1 = 0, value2 = 0;
        if (actionType == ActionType.UPDATE_SCROLL) {
            value1 = buf.readFloat();
        } else if (actionType == ActionType.UPDATE_ROTATION) {
            value1 = buf.readFloat();
            value2 = buf.readFloat();
        }
        return new VxPhysicsGunActionPacket(actionType, value1, value2);
    }

    @Override
    public void encode(VxByteBuf buf) {
        buf.writeEnum(this.actionType);
        if (this.actionType == ActionType.UPDATE_SCROLL) {
            buf.writeFloat(this.value1);
        } else if (this.actionType == ActionType.UPDATE_ROTATION) {
            buf.writeFloat(this.value1);
            buf.writeFloat(this.value2);
        }
    }

    @Override
    public void handle(NetworkManager.PacketContext context) {
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player == null) return;

            VxPhysicsGunServerManager manager = VxPhysicsGunServerManager.getInstance();
            switch (this.actionType) {
                case START_GRAB_ATTEMPT -> manager.startGrabAttempt(player);
                case STOP_GRAB_ATTEMPT -> manager.stopGrabAttempt(player);
                case UPDATE_SCROLL -> manager.updateScroll(player, this.value1);
                case UPDATE_ROTATION -> manager.updateRotation(player, this.value1, this.value2);
                case FREEZE_OBJECT -> manager.freezeBody(player);
                case START_ROTATION_MODE -> manager.startRotationMode(player);
                case STOP_ROTATION_MODE -> manager.stopRotationMode(player);
            }
        });
    }
}