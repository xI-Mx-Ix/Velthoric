package net.xmx.velthoric.ship.plot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class VxShipSavedData extends SavedData {

    private static final String DATA_NAME = "vx_ship_plots";

    private final Map<Integer, VxPlot> plots = new ConcurrentHashMap<>();
    private int nextPlotId = 0;

    public VxShipSavedData() {
        super();
    }

    public static VxShipSavedData load(CompoundTag tag) {
        VxShipSavedData savedData = new VxShipSavedData();
        savedData.nextPlotId = tag.getInt("nextPlotId");
        ListTag plotsTag = tag.getList("plots", Tag.TAG_COMPOUND);
        for (int i = 0; i < plotsTag.size(); i++) {
            CompoundTag plotTag = plotsTag.getCompound(i);
            VxPlot plot = VxPlot.fromNbt(plotTag);
            savedData.plots.put(plot.getId(), plot);
        }
        return savedData;
    }

    @Override
    @NotNull
    public CompoundTag save(@NotNull CompoundTag tag) {
        tag.putInt("nextPlotId", nextPlotId);
        ListTag plotsTag = new ListTag();
        for (VxPlot plot : plots.values()) {
            plotsTag.add(plot.toNbt());
        }
        tag.put("plots", plotsTag);
        return tag;
    }

    public static VxShipSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(VxShipSavedData::load, VxShipSavedData::new, DATA_NAME);
    }

    public VxPlot createNewPlot(BoundingBox box) {
        int id = nextPlotId++;
        VxPlot plot = new VxPlot(id, box);
        plots.put(id, plot);
        setDirty();
        return plot;
    }

    public Optional<VxPlot> findAvailablePlot() {
        return plots.values().stream()
                .filter(plot -> plot.getAssignedShipId().isEmpty())
                .findFirst();
    }

    public Optional<VxPlot> getPlot(int plotId) {
        return Optional.ofNullable(plots.get(plotId));
    }

    public Optional<VxPlot> getPlotForShip(UUID shipId) {
        return plots.values().stream()
                .filter(plot -> plot.getAssignedShipId().isPresent() && plot.getAssignedShipId().get().equals(shipId))
                .findFirst();
    }

    public Map<Integer, VxPlot> getAllPlots() {
        return plots;
    }
}