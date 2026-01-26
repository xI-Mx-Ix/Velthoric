/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.init.registry;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Manages the registration of client-side keybindings for the mod.
 *
 * @author xI-Mx-Ix
 */
public class KeyMappings {

    /**
     * The keybinding used to open the tool configuration screen.
     * Default key: TAB.
     */
    public static final KeyMapping OPEN_TOOL_CONFIG = new KeyMapping(
            "key.velthoric.tool_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_TAB,
            "category.velthoric"
    );

    /**
     * The keybinding used shift down vehicle.
     * Default key: UNKNOWN (if UNKNOWN -> directly access F).
     */
    public static final KeyMapping VEHICLE_SHIFT_DOWN = new KeyMapping(
            "key.velthoric.vehicle.shift_down",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "category.velthoric.vehicle"
    );

    /**
     * The keybinding used shift up vehicle.
     * Default key: UNKNOWN (if UNKNOWN -> directly access R).
     */
    public static final KeyMapping VEHICLE_SHIFT_UP = new KeyMapping(
            "key.velthoric.vehicle.shift_up",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "category.velthoric.vehicle"
    );

    /**
     * The keybinding used shift down vehicle.
     * Default key: UNKNOWN (if UNKNOWN -> directly access H).
     */
    public static final KeyMapping VEHICLE_SPECIAL = new KeyMapping(
            "key.velthoric.vehicle.special",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "category.velthoric.vehicle"
    );

    /**
     * Registers the keymappings with the game engine.
     * This method should be called during client initialization.
     */
    public static void register() {
        KeyMappingRegistry.register(OPEN_TOOL_CONFIG);
        KeyMappingRegistry.register(VEHICLE_SHIFT_DOWN);
        KeyMappingRegistry.register(VEHICLE_SHIFT_UP);
        KeyMappingRegistry.register(VEHICLE_SPECIAL);
    }
}