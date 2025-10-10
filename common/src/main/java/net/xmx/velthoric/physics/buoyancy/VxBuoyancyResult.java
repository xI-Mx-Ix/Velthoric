/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.physics.buoyancy;

import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * A reusable data structure to hold the result of the broad-phase check.
 *
 * @author xI-Mx-Ix
 */
public final class VxBuoyancyResult {
    private final int[] bodyIds;
    private final Int2FloatMap surfaceHeights;
    private final Int2ObjectMap<VxFluidType> fluidTypes;

    VxBuoyancyResult(int[] bodyIds, Int2FloatMap surfaceHeights, Int2ObjectMap<VxFluidType> fluidTypes) {
        this.bodyIds = bodyIds;
        this.surfaceHeights = surfaceHeights;
        this.fluidTypes = fluidTypes;
    }

    public int[] getBodyIds() {
        return bodyIds;
    }

    public Int2FloatMap getSurfaceHeights() {
        return surfaceHeights;
    }

    public Int2ObjectMap<VxFluidType> getFluidTypes() {
        return fluidTypes;
    }
}