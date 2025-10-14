/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.boxthrower.event;

import dev.architectury.event.EventResult;
import net.minecraft.client.Minecraft;
import net.xmx.velthoric.event.api.VxMouseEvent;
import net.xmx.velthoric.init.registry.ItemRegistry;
import net.xmx.velthoric.item.boxthrower.packet.VxBoxThrowerActionPacket;
import net.xmx.velthoric.network.VxPacketHandler;
import org.lwjgl.glfw.GLFW;

/**
 * Handles client-side input events for the Box Thrower.
 *
 * @author xI-Mx-Ix
 */
public class VxBoxThrowerClientEvents {

    /**
     * Registers the client-side event listeners.
     */
    public static void registerEvents() {
        VxMouseEvent.Press.EVENT.register(VxBoxThrowerClientEvents::onMousePress);
    }

    private static EventResult onMousePress(VxMouseEvent.Press event) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;

        if (player == null || minecraft.screen != null) {
            return EventResult.pass();
        }

        boolean isHoldingBoxThrower = player.getMainHandItem().is(ItemRegistry.BOX_THROWER.get())
                               || player.getOffhandItem().is(ItemRegistry.BOX_THROWER.get());

        if (!isHoldingBoxThrower) {
            return EventResult.pass();
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                VxPacketHandler.sendToServer(new VxBoxThrowerActionPacket(VxBoxThrowerActionPacket.ActionType.START_SHOOTING));
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                VxPacketHandler.sendToServer(new VxBoxThrowerActionPacket(VxBoxThrowerActionPacket.ActionType.STOP_SHOOTING));
            }
            // Cancel the event to prevent normal item use
            return EventResult.interruptFalse();
        }

        return EventResult.pass();
    }
}