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
     * Registers the keymappings with the game engine.
     * This method should be called during client initialization.
     */
    public static void register() {
        KeyMappingRegistry.register(OPEN_TOOL_CONFIG);
    }
}