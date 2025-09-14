/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.compat;

import me.jellysquid.mods.sodium.client.render.chunk.map.ChunkStatus;
import me.jellysquid.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.xmx.velthoric.mixin.VelthoricMixinPlugin;

public class SodiumCompatibility {

    public static void onChunkAdded(ClientLevel level, int x, int z) {
        if (VelthoricMixinPlugin.isSodiumPresent) {
            ChunkTrackerHolder.get(level).onChunkStatusAdded(x, z, ChunkStatus.FLAG_ALL);
        }
    }

    public static void onChunkRemoved(ClientLevel level, int x, int z) {
        if (VelthoricMixinPlugin.isSodiumPresent) {
            ChunkTrackerHolder.get(level).onChunkStatusRemoved(x, z, ChunkStatus.FLAG_ALL);
        }
    }
}