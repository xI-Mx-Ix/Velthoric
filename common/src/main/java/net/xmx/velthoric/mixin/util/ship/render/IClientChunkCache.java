package net.xmx.velthoric.mixin.util.ship.render;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.chunk.LevelChunk;

public interface IClientChunkCache {
    Long2ObjectMap<LevelChunk> velthoric$getShipChunks();
}