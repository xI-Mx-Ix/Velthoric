package net.xmx.velthoric.ship.plot;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class VxClientPlotManager {

    private static final VxClientPlotManager INSTANCE = new VxClientPlotManager();
    private final Map<Integer, VxPlot> plots = new ConcurrentHashMap<>();

    private VxClientPlotManager() {}

    public static VxClientPlotManager getInstance() {
        return INSTANCE;
    }

    public void addOrUpdatePlot(int id, BoundingBox bounds) {
        plots.put(id, new VxPlot(id, bounds));
    }

    public Optional<VxPlot> getPlot(int id) {
        return Optional.ofNullable(plots.get(id));
    }

    public void clear() {
        plots.clear();
    }
}