/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.xmx.velthoric.mixin.impl.bridge.mounting.input.KeyMappingKeyAccessor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;


/**
 * A custom key mapping for Velthoric that extends Minecraft's {@link KeyMapping}.
 * <p>
 * Adds support for a fallback key in case the primary key is unknown or unassigned.
 * </p>
 *
 * @author timtaran
 */
public class VxKeyMapping extends KeyMapping {
    private final InputConstants.Key fallbackKey;

    /**
     * Creates a new key mapping with a fallback key.
     *
     * @param name           The translation key or name of this key mapping.
     * @param type           The input type (keyboard, mouse, etc.).
     * @param defaultKeyCode The default key code for this key mapping.
     * @param fallbackKeyCode The key code to use as a fallback if the main key is unknown.
     * @param category       The category this key mapping belongs to.
     */
    public VxKeyMapping(String name, InputConstants.Type type, int defaultKeyCode, int fallbackKeyCode, String category)
    {
        super(name, type, defaultKeyCode, category);
        this.fallbackKey = type.getOrCreate(fallbackKeyCode);
    }

    /**
     * Gets the original (vanilla) key assigned to this mapping.
     *
     * @return The underlying {@link InputConstants.Key} used by Minecraft.
     */
    public InputConstants.Key getVanillaKey() {
        return ((KeyMappingKeyAccessor) this).velthoric_getKey();
    }

    /**
     * Returns the translated key message to display in-game.
     * <p>
     * If the main key is unknown, the fallback key's display name will be returned.
     * </p>
     *
     * @return The display name of the key.
     */
    @Override
    public @NotNull Component getTranslatedKeyMessage() {
        if (getVanillaKey().getValue() == GLFW.GLFW_KEY_UNKNOWN)
            return this.fallbackKey.getDisplayName();
        else
            return getVanillaKey().getDisplayName();
    }


    /**
     * Checks whether this key mapping is currently pressed.
     * <p>
     * Uses the fallback key if the main key is unknown.
     * </p>
     *
     * @param window The window handle to check input for.
     * @return True if the key (or fallback) is pressed, false otherwise.
     */
    public boolean isDown(long window) {
        if (getVanillaKey().getValue() == GLFW.GLFW_KEY_UNKNOWN)
            return InputConstants.isKeyDown(window, this.fallbackKey.getValue());
        else
            return isDown();
    }
}
