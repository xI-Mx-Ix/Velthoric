/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.event.api;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import net.minecraft.server.level.ServerLevel;

/**
 * @author xI-Mx-Ix
 */
public class VxLevelEvent {

    /**
     * Fired when a ServerLevel is being saved.
     */
    public static class Save {
        public static final Event<Listener> EVENT = EventFactory.createLoop();
        private final ServerLevel level;

        public Save(ServerLevel level) {
            this.level = level;
        }

        public ServerLevel getLevel() {
            return level;
        }

        @FunctionalInterface
        public interface Listener {
            void onLevelSave(Save event);
        }
    }

    /**
     * Fired when a ServerLevel is loaded (ready).
     */
    public static class Load {
        public static final Event<Listener> EVENT = EventFactory.createLoop();
        private final ServerLevel level;

        public Load(ServerLevel level) {
            this.level = level;
        }

        public ServerLevel getLevel() {
            return level;
        }

        @FunctionalInterface
        public interface Listener {
            void onLevelLoad(Load event);
        }
    }

    /**
     * Fired when a ServerLevel is about to unload.
     */
    public static class Unload {
        public static final Event<Listener> EVENT = EventFactory.createLoop();
        private final ServerLevel level;

        public Unload(ServerLevel level) {
            this.level = level;
        }

        public ServerLevel getLevel() {
            return level;
        }

        @FunctionalInterface
        public interface Listener {
            void onLevelUnload(Unload event);
        }
    }
}

