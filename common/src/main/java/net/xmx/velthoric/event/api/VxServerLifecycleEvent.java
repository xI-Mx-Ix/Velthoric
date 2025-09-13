/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.event.api;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import net.minecraft.server.MinecraftServer;

public abstract class VxServerLifecycleEvent {
    protected final MinecraftServer server;

    public VxServerLifecycleEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }

    /**
     * Fired when the server is starting.
     */
    public static class Starting extends VxServerLifecycleEvent {
        public static final Event<Listener> EVENT = EventFactory.createLoop();

        public Starting(MinecraftServer server) {
            super(server);
        }

        @FunctionalInterface
        public interface Listener {
            void onServerStarting(Starting event);
        }
    }

    /**
     * Fired when the server is stopping.
     */
    public static class Stopping extends VxServerLifecycleEvent {
        public static final Event<Listener> EVENT = EventFactory.createLoop();

        public Stopping(MinecraftServer server) {
            super(server);
        }

        @FunctionalInterface
        public interface Listener {
            void onServerStopping(Stopping event);
        }
    }
}
