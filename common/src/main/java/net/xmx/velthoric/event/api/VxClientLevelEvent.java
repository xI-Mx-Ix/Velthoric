/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.event.api;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * @author xI-Mx-Ix
 */
@Environment(EnvType.CLIENT)
public class VxClientLevelEvent {

    /**
     * Fired when a ClientLevel is loaded and set on the Minecraft client.
     * This occurs on world join and dimension change.
     */
    public static class Load {
        public static final Event<Listener> EVENT = EventFactory.createLoop();
        private final ClientLevel level;

        public Load(ClientLevel level) {
            this.level = level;
        }

        public ClientLevel getLevel() {
            return level;
        }

        @FunctionalInterface
        public interface Listener {
            void onLevelLoad(Load event);
        }
    }

    /**
     * Fired when a ClientLevel is about to be unloaded.
     * This occurs just before a new level is set (e.g., on disconnect or dimension change).
     */
    public static class Unload {
        public static final Event<Listener> EVENT = EventFactory.createLoop();
        private final ClientLevel level;

        public Unload(ClientLevel level) {
            this.level = level;
        }

        public ClientLevel getLevel() {
            return level;
        }

        @FunctionalInterface
        public interface Listener {
            void onLevelUnload(Unload event);
        }
    }
}