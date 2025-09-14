/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.ship.feat.clip;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.xmx.velthoric.ship.VxShipUtil;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Level.class)
public abstract class MixinLevel implements BlockGetter {

    /**
     * @author xI-Mx-Ix
     */
    @Override
    public @NotNull BlockHitResult clip(ClipContext context) {
        return VxShipUtil.clipIncludeShips((Level) (Object) this, context);
    }
}