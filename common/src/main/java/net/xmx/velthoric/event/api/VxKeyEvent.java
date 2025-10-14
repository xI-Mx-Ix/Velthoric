/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.event.api;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import dev.architectury.event.EventResult;

/**
 * Fired when a keyboard key is pressed, released, or repeats.
 * This event is fired on the client side.
 * Returning EventResult.interruptFalse() will cancel the original key press action.
 *
 * @author xI-Mx-Ix
 */
public class VxKeyEvent {
    public static final Event<Listener> EVENT = EventFactory.createEventResult();

    private final long window;
    private final int key;
    private final int scanCode;
    private final int action;
    private final int modifiers;

    public VxKeyEvent(long window, int key, int scanCode, int action, int modifiers) {
        this.window = window;
        this.key = key;
        this.scanCode = scanCode;
        this.action = action;
        this.modifiers = modifiers;
    }

    /**
     * @return The window handle.
     */
    public long getWindow() {
        return window;
    }

    /**
     * @return The key code (e.g., GLFW.GLFW_KEY_E).
     */
    public int getKey() {
        return key;
    }

    /**
     * @return The system-specific scancode of the key.
     */
    public int getScanCode() {
        return scanCode;
    }

    /**
     * @return The key action (e.g., GLFW.GLFW_PRESS, GLFW.GLFW_RELEASE).
     */
    public int getAction() {
        return action;
    }

    /**
     * @return A bitfield describing which modifier keys were held down.
     */
    public int getModifiers() {
        return modifiers;
    }

    @FunctionalInterface
    public interface Listener {
        EventResult onKey(VxKeyEvent event);
    }
}