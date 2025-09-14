/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.util.ship.render.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.lists.SortedRenderLists;

import java.util.Map;
import java.util.UUID;

public interface IRenderSectionManager {
    Map<UUID, SortedRenderLists> velthoric$getShipRenderLists();
}