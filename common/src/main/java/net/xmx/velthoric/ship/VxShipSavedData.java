/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.ship;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.xmx.velthoric.init.VxMainClass;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VxShipSavedData extends SavedData {

    private static final String NAME = "vx_ship_plots";
    private final Map<UUID, ChunkPos> plotCenters = new HashMap<>();
    private final Map<UUID, Integer> plotRadii = new HashMap<>();


    public VxShipSavedData() {
        super();
    }

    public VxShipSavedData(CompoundTag tag) {
        CompoundTag plotsTag = tag.getCompound("plots");
        for (String key : plotsTag.getAllKeys()) {
            try {
                UUID plotId = UUID.fromString(key);
                CompoundTag plotData = plotsTag.getCompound(key);
                int[] posArray = plotData.getIntArray("center");
                int radius = plotData.getInt("radius");

                if (posArray.length == 2) {
                    plotCenters.put(plotId, new ChunkPos(posArray[0], posArray[1]));
                    plotRadii.put(plotId, radius);
                } else {
                    VxMainClass.LOGGER.warn("Invalid position array for plot {} in saved data.", key);
                }
            } catch (IllegalArgumentException e) {
                VxMainClass.LOGGER.error("Invalid UUID key '{}' in plot saved data.", key);
            }
        }
        VxMainClass.LOGGER.info("Loaded {} ship plots from saved data.", plotCenters.size());
    }

    @Override
    public CompoundTag save(CompoundTag compoundTag) {
        CompoundTag plotsTag = new CompoundTag();
        plotCenters.forEach((plotId, chunkPos) -> {
            CompoundTag plotData = new CompoundTag();
            plotData.putIntArray("center", new int[]{chunkPos.x, chunkPos.z});
            plotData.putInt("radius", plotRadii.getOrDefault(plotId, 0));
            plotsTag.put(plotId.toString(), plotData);
        });
        compoundTag.put("plots", plotsTag);
        VxMainClass.LOGGER.info("Saving {} ship plots to disk.", plotCenters.size());
        return compoundTag;
    }

    public Map<UUID, ChunkPos> getPlotCenters() {
        return plotCenters;
    }

    public Map<UUID, Integer> getPlotRadii() {
        return plotRadii;
    }

    public void addPlot(UUID plotId, ChunkPos center, int radius) {
        plotCenters.put(plotId, center);
        plotRadii.put(plotId, radius);
        setDirty();
    }

    public void removePlot(UUID plotId) {
        plotCenters.remove(plotId);
        plotRadii.remove(plotId);
        setDirty();
    }

    public static VxShipSavedData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(VxShipSavedData::new, VxShipSavedData::new, NAME);
    }
}