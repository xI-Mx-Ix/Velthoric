/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.event.api;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import org.jetbrains.annotations.Nullable;

/**
 * @author xI-Mx-Ix
 */
public class VxClientPlayerNetworkEvent {

    public static class Disconnect {
        public static final Event<Listener> EVENT = EventFactory.createLoop();
        private final ClientLevel level;

        public Disconnect(ClientLevel level) {
            this.level = level;
        }

        public ClientLevel getLevel() {
            return level;
        }

        @FunctionalInterface
        public interface Listener {
            void onClientDisconnect(Disconnect event);
        }
    }

    public static class LoggingIn {
        public static final Event<Listener> EVENT = EventFactory.createLoop();
        private final MultiPlayerGameMode multiPlayerGameMode;
        private final LocalPlayer player;
        private final Connection connection;

        public LoggingIn(final MultiPlayerGameMode multiPlayerGameMode, final LocalPlayer player, final Connection connection) {
            this.multiPlayerGameMode = multiPlayerGameMode;
            this.player = player;
            this.connection = connection;
        }

        public MultiPlayerGameMode getMultiPlayerGameMode() {
            return multiPlayerGameMode;
        }

        public LocalPlayer getPlayer() {
            return player;
        }

        public Connection getConnection() {
            return connection;
        }

        @FunctionalInterface
        public interface Listener {
            void onClientLoggingIn(LoggingIn event);
        }
    }

    public static class LoggingOut {
        public static final Event<Listener> EVENT = EventFactory.createLoop();
        private final MultiPlayerGameMode multiPlayerGameMode;
        private final LocalPlayer player;
        private final Connection connection;

        public LoggingOut(@Nullable final MultiPlayerGameMode multiPlayerGameMode, @Nullable final LocalPlayer player, @Nullable final Connection connection) {
            this.multiPlayerGameMode = multiPlayerGameMode;
            this.player = player;
            this.connection = connection;
        }

        @Nullable
        public MultiPlayerGameMode getMultiPlayerGameMode() {
            return multiPlayerGameMode;
        }

        @Nullable
        public LocalPlayer getPlayer() {
            return player;
        }

        @Nullable
        public Connection getConnection() {
            return connection;
        }

        @FunctionalInterface
        public interface Listener {
            void onClientLoggingOut(LoggingOut event);
        }
    }

    public static class Clone {
        public static final Event<Listener> EVENT = EventFactory.createLoop();
        private final MultiPlayerGameMode multiPlayerGameMode;
        private final LocalPlayer oldPlayer;
        private final LocalPlayer newPlayer;
        private final Connection connection;

        public Clone(final MultiPlayerGameMode multiPlayerGameMode, final LocalPlayer oldPlayer, final LocalPlayer newPlayer, final Connection connection) {
            this.multiPlayerGameMode = multiPlayerGameMode;
            this.oldPlayer = oldPlayer;
            this.newPlayer = newPlayer;
            this.connection = connection;
        }

        public MultiPlayerGameMode getMultiPlayerGameMode() {
            return multiPlayerGameMode;
        }

        public LocalPlayer getOldPlayer() {
            return oldPlayer;
        }

        public LocalPlayer getNewPlayer() {
            return newPlayer;
        }

        public Connection getConnection() {
            return connection;
        }

        @FunctionalInterface
        public interface Listener {
            void onClientPlayerClone(Clone event);
        }
    }
}
