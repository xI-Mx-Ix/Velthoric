/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.config.subsystems;

import net.xmx.velthoric.config.VxConfigSpec;
import net.xmx.velthoric.config.VxConfigValue;

/**
 * Configuration container for the Terrain Tracking and Clustering system.
 *
 * @author xI-Mx-Ix
 */
public class VxTerrainConfig {

    public final VxConfigValue<Integer> gridCellSize;
    public final VxConfigValue<Integer> activationRadius;
    public final VxConfigValue<Integer> preloadRadius;
    public final VxConfigValue<Double> predictionSeconds;
    public final VxConfigValue<Integer> maxChunksPerCluster;
    public final VxConfigValue<Integer> maxGenHeight;
    public final VxConfigValue<Integer> minGenHeight;

    public VxTerrainConfig(VxConfigSpec.Builder builder) {
        this.gridCellSize = builder.defineInRange("grid_cell_size_chunks", 4, 1, 16, "Size of the coarse grid cells used for clustering bodies.");
        this.activationRadius = builder.defineInRange("activation_radius_chunks", 1, 0, 8, "Radius around moving bodies to keep physics active.");
        this.preloadRadius = builder.defineInRange("preload_radius_chunks", 3, 1, 12, "Radius to preload terrain around clusters based on velocity.");
        this.predictionSeconds = builder.define("prediction_seconds", 0.5d, "Time in seconds to predict body movement for loading terrain ahead.");
        this.maxChunksPerCluster = builder.define("max_chunks_per_cluster_safety", 4096, "Safety brake: Max chunks a single cluster can request before ignoring.");
        this.maxGenHeight = builder.define("max_generation_height", 500, "Max Y-level for terrain tracking.");
        this.minGenHeight = builder.define("min_generation_height", -250, "Min Y-level for terrain tracking.");
    }
}