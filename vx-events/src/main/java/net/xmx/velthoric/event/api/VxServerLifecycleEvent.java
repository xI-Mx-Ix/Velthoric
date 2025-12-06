/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.event.api;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import net.minecraft.server.MinecraftServer;

/**
 * A container for server lifecycle events.
 *
 * @author xI-Mx-Ix
 */
public class VxServerLifecycleEvent {

    /**
     * Fired when the server is starting.
     */
    public static class Starting {
        public static final Event<Listener> EVENT = EventFactory.createLoop();
        private final MinecraftServer server;

        public Starting(MinecraftServer server) {
            this.server = server;
        }

        public MinecraftServer getServer() {
            return server;
        }

        @FunctionalInterface
        public interface Listener {
            void onServerStarting(Starting event);
        }
    }

    /**
     * Fired when the server is stopping.
     */
    public static class Stopping {
        public static final Event<Listener> EVENT = EventFactory.createLoop();
        private final MinecraftServer server;

        public Stopping(MinecraftServer server) {
            this.server = server;
        }

        public MinecraftServer getServer() {
            return server;
        }

        @FunctionalInterface
        public interface Listener {
            void onServerStopping(Stopping event);
        }
    }
}