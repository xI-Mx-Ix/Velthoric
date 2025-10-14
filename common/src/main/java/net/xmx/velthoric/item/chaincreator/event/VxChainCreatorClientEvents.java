/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.chaincreator.event;

import dev.architectury.event.EventResult;
import net.minecraft.client.Minecraft;
import net.xmx.velthoric.event.api.VxMouseEvent;
import net.xmx.velthoric.init.registry.ItemRegistry;
import net.xmx.velthoric.item.chaincreator.packet.VxChainCreatorActionPacket;
import net.xmx.velthoric.network.VxPacketHandler;
import org.lwjgl.glfw.GLFW;

/**
 * Handles client-side input events for the Chain Creator.
 *
 * @author xI-Mx-Ix
 */
public class VxChainCreatorClientEvents {

    /**
     * Registers the client-side event listeners.
     */
    public static void registerEvents() {
        VxMouseEvent.Press.EVENT.register(VxChainCreatorClientEvents::onMousePress);
    }

    private static EventResult onMousePress(VxMouseEvent.Press event) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;

        if (player == null || minecraft.screen != null) {
            return EventResult.pass();
        }

        boolean isHoldingChainCreator = player.getMainHandItem().is(ItemRegistry.CHAIN_CREATOR.get())
                || player.getOffhandItem().is(ItemRegistry.CHAIN_CREATOR.get());

        if (!isHoldingChainCreator) {
            return EventResult.pass();
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                VxPacketHandler.sendToServer(new VxChainCreatorActionPacket(VxChainCreatorActionPacket.ActionType.START_CREATION));
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                VxPacketHandler.sendToServer(new VxChainCreatorActionPacket(VxChainCreatorActionPacket.ActionType.FINISH_CREATION));
            }
            // Cancel the event to prevent normal item use
            return EventResult.interruptFalse();
        }

        return EventResult.pass();
    }
}