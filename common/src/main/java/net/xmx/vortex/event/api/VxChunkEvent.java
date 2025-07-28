package net.xmx.vortex.event.api;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

public class VxChunkEvent {
    protected final LevelChunk chunk;

    public VxChunkEvent(LevelChunk chunk) {
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

    /**
     * Fired when a chunk is loaded on the server.
     */
    public static class Load extends VxChunkEvent {
        public static final Event<Listener> EVENT = EventFactory.createLoop();

        public Load(LevelChunk chunk) {
            super(chunk);
        }

        @FunctionalInterface
        public interface Listener {
            void onChunkLoad(Load event);
        }
    }

    /**
     * Fired when a chunk is unloaded on the server.
     */
    public static class Unload extends VxChunkEvent {
        public static final Event<Listener> EVENT = EventFactory.createLoop();

        public Unload(LevelChunk chunk) {
            super(chunk);
        }

        @FunctionalInterface
        public interface Listener {
            void onChunkUnload(Unload event);
        }
    }

    /**
     * Fired when a chunk is watched by a player.
     */
    public static class Watch extends VxChunkEvent {
        private final ServerPlayer player;
        public static final Event<Listener> EVENT = EventFactory.createLoop();

        public Watch(LevelChunk chunk, ServerPlayer player) {
            super(chunk);
            this.player = player;
        }

        public ServerPlayer getPlayer() {
            return player;
        }

        @FunctionalInterface
        public interface Listener {
            void onChunkWatch(Watch event);
        }
    }

    /**
     * Fired when a chunk is unwatched by a player.
     */
    public static class Unwatch extends VxChunkEvent {
        private final ServerPlayer player;
        public static final Event<Listener> EVENT = EventFactory.createLoop();

        public Unwatch(LevelChunk chunk, ServerPlayer player) {
            super(chunk);
            this.player = player;
        }

        public ServerPlayer getPlayer() {
            return player;
        }

        @FunctionalInterface
        public interface Listener {
            void onChunkUnwatch(Unwatch event);
        }
    }
}