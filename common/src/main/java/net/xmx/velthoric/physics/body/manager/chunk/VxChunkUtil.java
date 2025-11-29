/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.body.manager.chunk;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * Utility class for chunk-based operations.
 * <p>
 * This class provides helper methods for chunk-based operations, such as checking
 * if a player is tracking a specific chunk. It abstracts version-specific logic
 * to ensure compatibility across different Minecraft versions.
 *
 * @author xI-Mx-Ix
 */
public class VxChunkUtil {

    /**
     * Helper method to calculate if a chunk is within range of another chunk.
     *
     * @param x1 Target chunk X
     * @param z1 Target chunk Z
     * @param x2 Player chunk X
     * @param z2 Player chunk Z
     * @param maxDistance The view distance
     * @return True if in range.
     */
    public static boolean isChunkInRange(int x1, int z1, int x2, int z2, int maxDistance) {
        int i = Math.max(0, Math.abs(x1 - x2) - 1);
        int j = Math.max(0, Math.abs(z1 - z2) - 1);
        long l = Math.max(0, Math.max(i, j) - 1);
        long m = Math.min(i, j);
        long n = m * m + l * l;
        int k = maxDistance * maxDistance;
        return n < (long)k;
    }

    /**
     * Determines if a player should be tracking a specific chunk.
     *
     * @param player   The player to check.
     * @param chunkPos The target chunk position.
     * @return True if the chunk is within the player's view distance.
     */
    public static boolean isPlayerWatchingChunk(ServerPlayer player, ChunkPos chunkPos) {
        // Use the server's global view distance as the baseline for entity/body tracking.
        // In some setups, players might have individual view distances, but this is the safe fallback.
        int viewDistance = player.level().getServer().getPlayerList().getViewDistance();

        ChunkPos playerPos = player.chunkPosition();
        return isChunkInRange(chunkPos.x, chunkPos.z, playerPos.x, playerPos.z, viewDistance);
    }
}