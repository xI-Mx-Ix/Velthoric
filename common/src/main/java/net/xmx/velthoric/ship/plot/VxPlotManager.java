/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.ship.plot;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.ship.chunk.VxChunkManager;
import net.xmx.velthoric.ship.VxShipSavedData;
import net.xmx.velthoric.ship.body.VxShipBody;

import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class VxPlotManager {

    private final ServerLevel level;
    private final VxShipSavedData savedData;
    private final Long2ObjectMap<VxShipBody> plotChunkToShipMap = new Long2ObjectOpenHashMap<>();
    private int nextPlotX = 0;

    public VxPlotManager(ServerLevel level) {
        this.level = level;
        this.savedData = VxShipSavedData.get(level);
        recalculateNextPlotX();
        VxMainClass.LOGGER.info("PlotManager initialized. Next plot X coordinate starts at {}.", nextPlotX);
    }

    public void tick(ServerLevel level) {
        VxChunkManager.getInstance().tick(level);
    }

    private void recalculateNextPlotX() {
        int maxX = 0;
        for (var entry : savedData.getPlotCenters().entrySet()) {
            UUID plotId = entry.getKey();
            ChunkPos center = entry.getValue();
            int radius = savedData.getPlotRadii().getOrDefault(plotId, 0);
            int plotMaxX = center.x + radius;
            if (plotMaxX > maxX) {
                maxX = plotMaxX;
            }
        }
        this.nextPlotX = maxX + 1;
    }

    public UUID createNewPlot(int radius) {
        UUID plotId = UUID.randomUUID();
        ChunkPos center = new ChunkPos(nextPlotX + radius, 0);
        int lastUsedChunkX = center.x + radius;
        nextPlotX = lastUsedChunkX + 1;
        savedData.addPlot(plotId, center, radius);
        VxMainClass.LOGGER.info("Created and saved new plot {} with center {} and radius {}.", plotId, center, radius);
        return plotId;
    }

    public void associateShipWithPlot(VxShipBody ship) {
        UUID plotId = ship.getPlotId();
        if (plotId == null) {
            VxMainClass.LOGGER.error("Cannot associate ship {} with a null plotId.", ship.getPhysicsId());
            return;
        }
        ChunkPos center = savedData.getPlotCenters().get(plotId);
        int radius = savedData.getPlotRadii().getOrDefault(plotId, 0);
        if (center != null) {
            AtomicInteger chunkCount = new AtomicInteger(0);
            streamPlotChunks(center, radius).forEach(pos -> {
                plotChunkToShipMap.put(pos.toLong(), ship);
                chunkCount.getAndIncrement();
            });
            VxMainClass.LOGGER.info("Associated ship {} with plot {}. Force-loading {} chunks.", ship.getPhysicsId(), plotId, chunkCount.get());
        } else {
            VxMainClass.LOGGER.warn("Attempted to associate ship {} with non-existent plot {}.", ship.getPhysicsId(), plotId);
        }
    }

    public void disassociateShipFromPlot(VxShipBody ship) {
        UUID plotId = ship.getPlotId();
        if (plotId == null) return;
        ChunkPos center = savedData.getPlotCenters().get(plotId);
        int radius = savedData.getPlotRadii().getOrDefault(plotId, 0);
        if (center != null) {
            AtomicInteger chunkCount = new AtomicInteger(0);
            streamPlotChunks(center, radius).forEach(pos -> {
                plotChunkToShipMap.remove(pos.toLong());
                chunkCount.getAndIncrement();
            });
            VxMainClass.LOGGER.info("Disassociated ship {} from plot {}. Releasing {} force-loaded chunks.", ship.getPhysicsId(), plotId, chunkCount.get());
        }
    }

    public VxShipBody getShipManaging(ChunkPos pos) {
        return plotChunkToShipMap.get(pos.toLong());
    }

    public boolean isPlotChunk(ChunkPos pos) {
        return this.plotChunkToShipMap.containsKey(pos.toLong());
    }

    public ChunkPos getPlotCenter(UUID plotId) {
        return savedData.getPlotCenters().get(plotId);
    }

    public int getPlotRadius(UUID plotId) {
        return savedData.getPlotRadii().getOrDefault(plotId, 0);
    }

    public Stream<ChunkPos> streamPlotChunks(ChunkPos center, int radius) {
        return ChunkPos.rangeClosed(
                new ChunkPos(center.x - radius, center.z - radius),
                new ChunkPos(center.x + radius, center.z + radius)
        );
    }

    public Collection<VxShipBody> getUniqueShips() {
        return new HashSet<>(plotChunkToShipMap.values());
    }
}