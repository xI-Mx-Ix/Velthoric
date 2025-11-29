/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.item.tool.event;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.xmx.velthoric.event.api.VxMouseEvent;
import net.xmx.velthoric.item.tool.VxToolMode;
import net.xmx.velthoric.item.tool.gui.VxToolConfigScreen;
import net.xmx.velthoric.item.tool.packet.VxToolActionPacket;
import net.xmx.velthoric.item.tool.registry.VxToolRegistry;
import net.xmx.velthoric.network.VxPacketHandler;
import org.lwjgl.glfw.GLFW;

/**
 * Handles client-side input for the Tool API.
 *
 * @author xI-Mx-Ix
 */
public class VxToolClientEvents {

    public static void registerEvents() {
        VxMouseEvent.Press.EVENT.register(VxToolClientEvents::onMousePress);
        ClientTickEvent.CLIENT_POST.register(VxToolClientEvents::onClientTick);
    }

    private static void onClientTick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.screen != null) return;

        // Check for TAB press
        if (GLFW.glfwGetKey(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_TAB) == GLFW.GLFW_PRESS) {
            Item heldItem = minecraft.player.getMainHandItem().getItem();
            VxToolMode mode = VxToolRegistry.get(heldItem);
            
            if (mode != null) {
                // Open the config UI
                minecraft.setScreen(new VxToolConfigScreen(heldItem, mode));
            }
        }
    }

    private static EventResult onMousePress(VxMouseEvent.Press event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) return EventResult.pass();

        Item heldItem = minecraft.player.getMainHandItem().getItem();
        if (VxToolRegistry.get(heldItem) == null) return EventResult.pass();

        // Map mouse buttons to actions
        VxToolMode.ActionState action = VxToolMode.ActionState.IDLE;
        
        if (event.getAction() == GLFW.GLFW_PRESS) {
            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                action = VxToolMode.ActionState.PRIMARY_ACTIVE;
            } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                action = VxToolMode.ActionState.SECONDARY_ACTIVE;
            }
        } else if (event.getAction() == GLFW.GLFW_RELEASE) {
            action = VxToolMode.ActionState.IDLE;
        }

        if (action != VxToolMode.ActionState.IDLE || event.getAction() == GLFW.GLFW_RELEASE) {
            VxPacketHandler.sendToServer(new VxToolActionPacket(action));
            // Interrupt to prevent vanilla usage
            return EventResult.interruptFalse();
        }

        return EventResult.pass();
    }
}