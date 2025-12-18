/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.timtaran.interactivemc.physics.item.tool.event;

import dev.architectury.event.EventResult;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.timtaran.interactivemc.physics.init.registry.KeyMappings;
import net.timtaran.interactivemc.physics.event.api.VxKeyEvent;
import net.timtaran.interactivemc.physics.event.api.VxMouseEvent;
import net.timtaran.interactivemc.physics.item.tool.VxToolMode;
import net.timtaran.interactivemc.physics.item.tool.gui.VxToolConfigScreen;
import net.timtaran.interactivemc.physics.item.tool.packet.VxToolActionPacket;
import net.timtaran.interactivemc.physics.item.tool.registry.VxToolRegistry;
import net.timtaran.interactivemc.physics.network.VxPacketHandler;
import org.lwjgl.glfw.GLFW;

/**
 * Handles client-side input events for the Tool API, including mouse clicks
 * and keyboard interactions.
 *
 * @author xI-Mx-Ix
 */
public class VxToolClientEvents {

    /**
     * Registers the necessary event listeners for mouse and keyboard input.
     */
    public static void registerEvents() {
        VxMouseEvent.Press.EVENT.register(VxToolClientEvents::onMousePress);
        VxKeyEvent.EVENT.register(VxToolClientEvents::onKeyPress);
    }

    /**
     * Handles keyboard events to trigger the tool configuration screen.
     * This method checks if the configured keybinding is pressed while holding a valid tool.
     * It ensures the menu only opens if no other GUI is currently active.
     *
     * @param event The keyboard event data.
     * @return The result of the event handling.
     */
    private static EventResult onKeyPress(VxKeyEvent event) {
        Minecraft minecraft = Minecraft.getInstance();

        // Ensure the player exists and no GUI screen is currently open
        if (minecraft.player == null || minecraft.screen != null) {
            return EventResult.pass();
        }

        // Check if the event action is a key press
        if (event.getAction() == GLFW.GLFW_PRESS) {
            // Check if the pressed key matches the configured tool config keybinding
            if (KeyMappings.OPEN_TOOL_CONFIG.matches(event.getKey(), event.getScanCode())) {
                Item heldItem = minecraft.player.getMainHandItem().getItem();
                VxToolMode mode = VxToolRegistry.get(heldItem);

                if (mode != null) {
                    // Open the configuration screen for the held tool
                    minecraft.setScreen(new VxToolConfigScreen(heldItem, mode));
                    return EventResult.interruptFalse();
                }
            }
        }

        return EventResult.pass();
    }

    /**
     * Handles mouse press events to trigger tool actions (Primary/Secondary).
     * Prevents tool usage if a GUI screen is currently active.
     *
     * @param event The mouse press event data.
     * @return The result of the event handling.
     */
    private static EventResult onMousePress(VxMouseEvent.Press event) {
        Minecraft minecraft = Minecraft.getInstance();

        // Prevent tool usage if a screen is open or player is null
        if (minecraft.player == null || minecraft.screen != null) {
            return EventResult.pass();
        }

        Item heldItem = minecraft.player.getMainHandItem().getItem();
        if (VxToolRegistry.get(heldItem) == null) {
            return EventResult.pass();
        }

        // Map mouse buttons to tool action states
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

        // If an action was determined, send it to the server and consume the event
        if (action != VxToolMode.ActionState.IDLE || event.getAction() == GLFW.GLFW_RELEASE) {
            VxPacketHandler.sendToServer(new VxToolActionPacket(action));
            return EventResult.interruptFalse();
        }

        return EventResult.pass();
    }
}