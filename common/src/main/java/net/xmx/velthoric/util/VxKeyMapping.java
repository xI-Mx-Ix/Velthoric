/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.xmx.velthoric.mixin.impl.mounting.input.KeyMappingAccessor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * A custom key mapping implementation for Velthoric that extends
 * Minecraft's {@link KeyMapping}.
 * <p>
 * Adds support for a fallback key in case the primary key is unknown
 * or unassigned.
 *
 * @author timtaran
 */
public class VxKeyMapping extends KeyMapping {

    /**
     * Fallback key used when the primary key is unknown.
     */
    private final InputConstants.Key fallbackKey;

    /**
     * Creates a new key mapping with a fallback key.
     *
     * @param name            The translation key of this key mapping.
     * @param type            The input type (keyboard, mouse, etc.).
     * @param defaultKeyCode  The default key code for this mapping.
     * @param fallbackKeyCode The key code to use as a fallback if the main key is unknown.
     * @param category        The category this key mapping belongs to.
     */
    public VxKeyMapping(
            String name,
            InputConstants.Type type,
            int defaultKeyCode,
            int fallbackKeyCode,
            String category
    ) {
        super(name, type, defaultKeyCode, category);
        this.fallbackKey = type.getOrCreate(fallbackKeyCode);
    }

    /**
     * Returns the underlying vanilla key assigned to this mapping.
     *
     * @return The {@link InputConstants.Key} used internally by Minecraft.
     */
    public InputConstants.Key getVanillaKey() {
        return ((KeyMappingAccessor) this).velthoric_getKey();
    }

    /**
     * Returns the translated key message to display in-game.
     * <p>
     * If the primary key is unknown, the fallback key's display name is used.
     *
     * @return The display name of the active key.
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
     * Uses the fallback key if the primary key is unknown.
     *
     * @param window The GLFW window handle to query input for.
     * @return {@code true} if the key (or fallback) is pressed, otherwise {@code false}.
     */
    public boolean isDown(long window) {
        if (getVanillaKey().getValue() == GLFW.GLFW_KEY_UNKNOWN)
            return InputConstants.isKeyDown(window, this.fallbackKey.getValue());
        else
            return isDown();
    }
}