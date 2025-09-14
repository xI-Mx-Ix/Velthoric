/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.ship.plot;

import net.minecraft.world.level.ChunkPos;
import java.util.UUID;

public record ShipPlotInfo(UUID shipId, ChunkPos plotCenter) {
}