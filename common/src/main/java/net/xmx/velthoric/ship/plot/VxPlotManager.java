package net.xmx.velthoric.ship.plot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class VxPlotManager {

    public static final int PLOT_DIAMETER_CHUNKS = 16;
    public static final int PLOT_DIAMETER_BLOCKS = PLOT_DIAMETER_CHUNKS * 16;
    public static final int PLOT_AREA_X_CHUNKS = 1875000;
    public static final int PLOT_AREA_X_BLOCKS = PLOT_AREA_X_CHUNKS * 16;
    public static final int PLOT_AREA_Z_BLOCKS = 0;

    private final ServerLevel level;
    private final VxShipSavedData savedData;
    private final ConcurrentHashMap<ChunkPos, Integer> chunkToPlotIdMap = new ConcurrentHashMap<>();

    public VxPlotManager(ServerLevel level) {
        this.level = level;
        this.savedData = VxShipSavedData.get(level);
        loadChunkMap();

        registerAllPlotTickets();
    }

    private void loadChunkMap() {
        chunkToPlotIdMap.clear();
        savedData.getAllPlots().values().forEach(plot -> {
            BoundingBox box = plot.getBounds();
            ChunkPos minChunk = new ChunkPos(box.minX() >> 4, box.minZ() >> 4);
            ChunkPos maxChunk = new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4);
            for (int x = minChunk.x; x <= maxChunk.x; x++) {
                for (int z = minChunk.z; z <= maxChunk.z; z++) {
                    chunkToPlotIdMap.put(new ChunkPos(x, z), plot.getId());
                }
            }
        });
    }

    public void registerAllPlotTickets() {
        savedData.getAllPlots().values().forEach(this::registerTicketsForPlot);
    }

    private void registerTicketsForPlot(VxPlot plot) {
        ServerChunkCache chunkCache = level.getChunkSource();
        BoundingBox box = plot.getBounds();
        ChunkPos minChunk = new ChunkPos(box.minX() >> 4, box.minZ() >> 4);
        ChunkPos maxChunk = new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4);

        for (int x = minChunk.x; x <= maxChunk.x; x++) {
            for (int z = minChunk.z; z <= maxChunk.z; z++) {
                ChunkPos pos = new ChunkPos(x, z);

                chunkCache.addRegionTicket(VxTicketManager.PLOT_TICKET, pos, 2, pos);
            }
        }
    }

    public VxPlot assignShipToAvailablePlot(UUID shipId) {
        VxPlot plot = savedData.findAvailablePlot().orElseGet(() -> {
            int newPlotIndex = savedData.getAllPlots().size();
            int plotX = PLOT_AREA_X_BLOCKS + (newPlotIndex * PLOT_DIAMETER_BLOCKS);
            int plotZ = PLOT_AREA_Z_BLOCKS;
            BlockPos min = new BlockPos(plotX, level.getMinBuildHeight(), plotZ);
            BlockPos max = new BlockPos(plotX + PLOT_DIAMETER_BLOCKS - 1, level.getMaxBuildHeight(), plotZ + PLOT_DIAMETER_BLOCKS - 1);
            BoundingBox newBox = BoundingBox.fromCorners(min, max);
            VxPlot newPlot = savedData.createNewPlot(newBox);

            registerTicketsForPlot(newPlot);
            return newPlot;
        });

        plot.setAssignedShipId(shipId);
        savedData.setDirty();
        loadChunkMap();
        return plot;
    }

    public VxPlot getPlotById(int plotId) {
        return savedData.getPlot(plotId).orElse(null);
    }

    public VxPlot getPlotForShip(UUID shipId) {
        return savedData.getPlotForShip(shipId).orElse(null);
    }

    public Integer getPlotIdForChunk(ChunkPos pos) {
        return chunkToPlotIdMap.get(pos);
    }

    public Collection<BoundingBox> getAllPlotBoxes() {
        return Collections.unmodifiableCollection(
                savedData.getAllPlots().values().stream()
                        .map(VxPlot::getBounds)
                        .collect(Collectors.toList())
        );
    }
}