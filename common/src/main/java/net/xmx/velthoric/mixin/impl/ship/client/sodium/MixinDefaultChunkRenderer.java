/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.ship.client.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.xmx.velthoric.ship.plot.VxClientPlotManager;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(DefaultChunkRenderer.class)
public abstract class MixinDefaultChunkRenderer {

    @Inject(
            method = "getVisibleFaces",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void cancelCulling(final int originX, final int originY, final int originZ, final int chunkX, final int chunkY, final int chunkZ, final CallbackInfoReturnable<Integer> cir) {
        if (VxClientPlotManager.getInstance().isShipChunk(chunkX, chunkZ))
            cir.setReturnValue(ModelQuadFacing.ALL);
    }
}