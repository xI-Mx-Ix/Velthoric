/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.magnetizer.event;

import dev.architectury.event.EventResult;
import net.minecraft.client.Minecraft;
import net.xmx.velthoric.event.api.VxMouseEvent;
import net.xmx.velthoric.init.registry.ItemRegistry;
import net.xmx.velthoric.item.magnetizer.packet.VxMagnetizerActionPacket;
import net.xmx.velthoric.network.VxPacketHandler;
import org.lwjgl.glfw.GLFW;

/**
 * Handles client-side input events for the Magnetizer.
 *
 * @author xI-Mx-Ix
 */
public class VxMagnetizerClientEvents {

    /**
     * Registers the client-side event listeners.
     */
    public static void registerEvents() {
        VxMouseEvent.Press.EVENT.register(VxMagnetizerClientEvents::onMousePress);
    }

    private static EventResult onMousePress(VxMouseEvent.Press event) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;

        if (player == null || minecraft.screen != null) {
            return EventResult.pass();
        }

        boolean isHoldingMagnetizer = player.getMainHandItem().is(ItemRegistry.MAGNETIZER.get())
                || player.getOffhandItem().is(ItemRegistry.MAGNETIZER.get());

        if (!isHoldingMagnetizer) {
            return EventResult.pass();
        }

        boolean eventHandled = false;

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                VxPacketHandler.sendToServer(new VxMagnetizerActionPacket(VxMagnetizerActionPacket.ActionType.START_ATTRACT));
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                VxPacketHandler.sendToServer(new VxMagnetizerActionPacket(VxMagnetizerActionPacket.ActionType.STOP_ACTION));
            }
            eventHandled = true;
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                VxPacketHandler.sendToServer(new VxMagnetizerActionPacket(VxMagnetizerActionPacket.ActionType.START_REPEL));
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                VxPacketHandler.sendToServer(new VxMagnetizerActionPacket(VxMagnetizerActionPacket.ActionType.STOP_ACTION));
            }
            eventHandled = true;
        }

        if (eventHandled) {
            // Cancel the event to prevent normal item use
            return EventResult.interruptFalse();
        }

        return EventResult.pass();
    }
}