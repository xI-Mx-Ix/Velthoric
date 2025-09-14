/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.util.ship;

import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

public interface IVxMinecraft {
    void velthoric$setOriginalCrosshairTarget(@Nullable HitResult hitResult);
}