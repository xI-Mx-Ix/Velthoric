/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.ship.plot;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VxClientPlotManager {

    private static final VxClientPlotManager INSTANCE = new VxClientPlotManager();
    private final Long2ObjectMap<ShipPlotInfo> chunkToShipMap = new Long2ObjectOpenHashMap<>();
    private final Map<UUID, ShipPlotInfo> shipIdToInfoMap = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> shipIdToRadiusMap = new ConcurrentHashMap<>();

    private VxClientPlotManager() {}

    public static VxClientPlotManager getInstance() {
        return INSTANCE;
    }

    public void addShipPlot(UUID shipId, ChunkPos center, int radius) {
        ShipPlotInfo plotInfo = new ShipPlotInfo(shipId, center);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                chunkToShipMap.put(new ChunkPos(center.x + x, center.z + z).toLong(), plotInfo);
            }
        }
        shipIdToInfoMap.put(shipId, plotInfo);
        shipIdToRadiusMap.put(shipId, radius);
    }

    public void removeShipPlot(ChunkPos center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                chunkToShipMap.remove(new ChunkPos(center.x + x, center.z + z).toLong());
            }
        }
        UUID shipToRemove = null;
        for (Map.Entry<UUID, ShipPlotInfo> entry : shipIdToInfoMap.entrySet()) {
            if (entry.getValue().plotCenter().equals(center)) {
                shipToRemove = entry.getKey();
                break;
            }
        }
        if (shipToRemove != null) {
            shipIdToInfoMap.remove(shipToRemove);
            shipIdToRadiusMap.remove(shipToRemove);
        }
    }

    @Nullable
    public ShipPlotInfo getShipInfoForChunk(ChunkPos pos) {
        return chunkToShipMap.get(pos.toLong());
    }

    @Nullable
    public ShipPlotInfo getShipInfoForShip(UUID shipId) {
        return shipIdToInfoMap.get(shipId);
    }

    public int getPlotRadius(UUID shipId) {
        return shipIdToRadiusMap.getOrDefault(shipId, 0);
    }

    public Set<UUID> getAllShipIds() {
        return Collections.unmodifiableSet(shipIdToInfoMap.keySet());
    }

    public boolean isShipChunk(int chunkX, int chunkZ) {
        return chunkToShipMap.containsKey(ChunkPos.asLong(chunkX, chunkZ));
    }

    public void clear() {
        chunkToShipMap.clear();
        shipIdToInfoMap.clear();
        shipIdToRadiusMap.clear();
    }
}