/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.event.api;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import dev.architectury.event.EventResult;

/**
 * A container for client-side mouse input events.
 *
 * @author xI-Mx-Ix
 */
public class VxMouseEvent {

    /**
     * Fired when a mouse button is pressed or released.
     * Returning EventResult.interruptFalse() will cancel the original mouse press action.
     */
    public static class Press {
        public static final Event<Listener> EVENT = EventFactory.createEventResult();
        private final long window;
        private final int button;
        private final int action;
        private final int mods;

        public Press(long window, int button, int action, int mods) {
            this.window = window;
            this.button = button;
            this.action = action;
            this.mods = mods;
        }

        public long getWindow() {
            return window;
        }

        public int getButton() {
            return button;
        }

        public int getAction() {
            return action;
        }

        public int getMods() {
            return mods;
        }

        @FunctionalInterface
        public interface Listener {
            EventResult onMousePress(Press event);
        }
    }

    /**
     * Fired when the mouse wheel is scrolled.
     * Returning EventResult.interruptFalse() will cancel the original mouse scroll action.
     */
    public static class Scroll {
        public static final Event<Listener> EVENT = EventFactory.createEventResult();
        private final long window;
        private final double horizontal;
        private final double vertical;

        public Scroll(long window, double horizontal, double vertical) {
            this.window = window;
            this.horizontal = horizontal;
            this.vertical = vertical;
        }

        public long getWindow() {
            return window;
        }

        public double getHorizontal() {
            return horizontal;
        }

        public double getVertical() {
            return vertical;
        }

        @FunctionalInterface
        public interface Listener {
            EventResult onMouseScroll(Scroll event);
        }
    }

    /**
     * Fired when the player's view is turned via the mouse, before the player's rotation is updated.
     * This corresponds to mouse movement when not in a UI.
     * Returning EventResult.interruptFalse() will prevent the player's view from changing.
     */
    public static class Turn {
        public static final Event<Listener> EVENT = EventFactory.createEventResult();
        private final double dx;
        private final double dy;

        public Turn(double dx, double dy) {
            this.dx = dx;
            this.dy = dy;
        }

        public double getDx() {
            return dx;
        }

        public double getDy() {
            return dy;
        }

        @FunctionalInterface
        public interface Listener {
            EventResult onPlayerTurn(Turn event);
        }
    }
}