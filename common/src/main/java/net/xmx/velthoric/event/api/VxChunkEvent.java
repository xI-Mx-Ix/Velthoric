/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.event.api;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * A container for server-side chunk events.
 *
 * @author xI-Mx-Ix
 */
public class VxChunkEvent {

    /**
     * Fired when a chunk is loaded on the server.
     */
    public static class Load {
        public static final Event<Listener> EVENT = EventFactory.createLoop();
        private final LevelChunk chunk;

        public Load(LevelChunk chunk) {
            this.chunk = chunk;
        }

        public LevelChunk getLevelChunk() {
            return chunk;
        }

        public ChunkPos getChunkPos() {
            return chunk.getPos();
        }

        public ServerLevel getLevel() {
            return (ServerLevel) chunk.getLevel();
        }

        @FunctionalInterface
        public interface Listener {
            void onChunkLoad(Load event);
        }
    }

    /**
     * Fired when a chunk is unloaded on the server.
     */
    public static class Unload {
        public static final Event<Listener> EVENT = EventFactory.createLoop();
        private final LevelChunk chunk;

        public Unload(LevelChunk chunk) {
            this.chunk = chunk;
        }

        public LevelChunk getLevelChunk() {
            return chunk;
        }

        public ChunkPos getChunkPos() {
            return chunk.getPos();
        }

        public ServerLevel getLevel() {
            return (ServerLevel) chunk.getLevel();
        }

        @FunctionalInterface
        public interface Listener {
            void onChunkUnload(Unload event);
        }
    }

    /**
     * Fired when a chunk becomes watched by a player.
     */
    public static class Watch {
        public static final Event<Listener> EVENT = EventFactory.createLoop();
        private final LevelChunk chunk;
        private final ServerPlayer player;

        public Watch(LevelChunk chunk, ServerPlayer player) {
            this.chunk = chunk;
            this.player = player;
        }

        public ServerPlayer getPlayer() {
            return player;
        }

        public LevelChunk getLevelChunk() {
            return chunk;
        }

        public ChunkPos getChunkPos() {
            return chunk.getPos();
        }

        public ServerLevel getLevel() {
            return (ServerLevel) chunk.getLevel();
        }

        @FunctionalInterface
        public interface Listener {
            void onChunkWatch(Watch event);
        }
    }

    /**
     * Fired when a chunk is no longer watched by a player.
     */
    public static class Unwatch {
        public static final Event<Listener> EVENT = EventFactory.createLoop();
        private final LevelChunk chunk;
        private final ServerPlayer player;

        public Unwatch(LevelChunk chunk, ServerPlayer player) {
            this.chunk = chunk;
            this.player = player;
        }

        public ServerPlayer getPlayer() {
            return player;
        }

        public LevelChunk getLevelChunk() {
            return chunk;
        }

        public ChunkPos getChunkPos() {
            return chunk.getPos();
        }

        public ServerLevel getLevel() {
            return (ServerLevel) chunk.getLevel();
        }

        @FunctionalInterface
        public interface Listener {
            void onChunkUnwatch(Unwatch event);
        }
    }
}