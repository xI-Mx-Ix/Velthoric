/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.ship.chunk;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.physics.world.VxPhysicsWorld;
import net.xmx.velthoric.ship.body.VxShipBody;
import org.apache.commons.lang3.mutable.MutableObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class VxChunkManager {

    private static final VxChunkManager INSTANCE = new VxChunkManager();
    private final Map<UUID, LongSet> playerTrackedShipChunks = new HashMap<>();
    private final LongSet currentlyForceLoadedChunks = new LongOpenHashSet();

    private VxChunkManager() {}

    public static VxChunkManager getInstance() {
        return INSTANCE;
    }

    public void tick(ServerLevel level) {
        VxPhysicsWorld physicsWorld = VxPhysicsWorld.get(level.dimension());
        if (physicsWorld == null || physicsWorld.getPlotManager() == null) {
            return;
        }

        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        Collection<VxShipBody> uniqueShips = physicsWorld.getPlotManager().getUniqueShips();

        Map<UUID, LongSet> newPlayerTrackedShipChunks = new HashMap<>();
        LongSet allRequiredChunksToForceLoad = new LongOpenHashSet();
        int viewDistance = level.getServer().getPlayerList().getViewDistance();

        for (ServerPlayer player : level.players()) {
            LongSet chunksToTrackForPlayer = new LongOpenHashSet();
            for (VxShipBody ship : uniqueShips) {
                var shipPosition = ship.getGameTransform().getTranslation();
                int shipChunkX = (int) shipPosition.x() >> 4;
                int shipChunkZ = (int) shipPosition.z() >> 4;

                if (ChunkMap.isChunkInRange(shipChunkX, shipChunkZ, player.chunkPosition().x, player.chunkPosition().z, viewDistance)) {
                    physicsWorld.getPlotManager().streamPlotChunks(ship.getPlotCenter(), ship.getPlotRadius())
                            .forEach(chunkPos -> {
                                long longPos = chunkPos.toLong();
                                chunksToTrackForPlayer.add(longPos);
                                allRequiredChunksToForceLoad.add(longPos);
                            });
                }
            }
            if (!chunksToTrackForPlayer.isEmpty()) {
                newPlayerTrackedShipChunks.put(player.getUUID(), chunksToTrackForPlayer);
            }
        }

        Set<UUID> allPlayerIds = new HashSet<>(playerTrackedShipChunks.keySet());
        allPlayerIds.addAll(newPlayerTrackedShipChunks.keySet());

        for (UUID playerId : allPlayerIds) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) continue;

            LongSet oldChunks = playerTrackedShipChunks.getOrDefault(playerId, LongSets.EMPTY_SET);
            LongSet newChunks = newPlayerTrackedShipChunks.getOrDefault(playerId, LongSets.EMPTY_SET);

            if (oldChunks.equals(newChunks)) continue;

            LongSet chunksToAdd = new LongOpenHashSet(newChunks);
            chunksToAdd.removeAll(oldChunks);

            LongSet chunksToRemove = new LongOpenHashSet(oldChunks);
            chunksToRemove.removeAll(newChunks);

            chunksToAdd.forEach(chunkPosLong -> chunkMap.updateChunkTracking(player, new ChunkPos(chunkPosLong), new MutableObject<>(), false, true));
            chunksToRemove.forEach(chunkPosLong -> chunkMap.updateChunkTracking(player, new ChunkPos(chunkPosLong), new MutableObject<>(), true, false));
        }

        this.playerTrackedShipChunks.clear();
        this.playerTrackedShipChunks.putAll(newPlayerTrackedShipChunks);

        LongSet chunksToForce = new LongOpenHashSet(allRequiredChunksToForceLoad);
        chunksToForce.removeAll(this.currentlyForceLoadedChunks);

        LongSet chunksToUnforce = new LongOpenHashSet(this.currentlyForceLoadedChunks);
        chunksToUnforce.removeAll(allRequiredChunksToForceLoad);

        chunksToForce.forEach(posLong -> level.getChunkSource().updateChunkForced(new ChunkPos(posLong), true));
        chunksToUnforce.forEach(posLong -> level.getChunkSource().updateChunkForced(new ChunkPos(posLong), false));

        this.currentlyForceLoadedChunks.clear();
        this.currentlyForceLoadedChunks.addAll(allRequiredChunksToForceLoad);
    }
}