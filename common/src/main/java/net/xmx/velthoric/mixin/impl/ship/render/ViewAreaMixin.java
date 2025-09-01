package net.xmx.velthoric.mixin.impl.ship.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.xmx.velthoric.mixin.impl.ship.accessor.ViewAreaAccessor;
import net.xmx.velthoric.ship.plot.VxPlotManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ViewArea.class)
public abstract class ViewAreaMixin {

    @Shadow @Final protected Level level;
    @Shadow protected int chunkGridSizeX;
    @Shadow protected int chunkGridSizeZ;

    @Inject(method = "getRenderChunkAt", at = @At("HEAD"), cancellable = true)
    private void onGetRenderChunkAt(BlockPos pos, CallbackInfoReturnable<ChunkRenderDispatcher.RenderChunk> cir) {
        if (pos.getX() >= VxPlotManager.PLOT_AREA_X_BLOCKS) {
            int relativeChunkX = (pos.getX() - VxPlotManager.PLOT_AREA_X_BLOCKS) >> 4;
            int relativeChunkZ = (pos.getZ() - VxPlotManager.PLOT_AREA_Z_BLOCKS) >> 4;

            BlockPos cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getBlockPosition();
            int playerChunkX = cameraPos.getX() >> 4;
            int playerChunkZ = cameraPos.getZ() >> 4;

            int targetChunkX = playerChunkX + relativeChunkX;
            int targetChunkZ = playerChunkZ + relativeChunkZ;

            int gridX = Mth.positiveModulo(targetChunkX, this.chunkGridSizeX);
            int gridZ = Mth.positiveModulo(targetChunkZ, this.chunkGridSizeZ);
            int gridY = Mth.positiveModulo(Mth.floorDiv(pos.getY() - this.level.getMinBuildHeight(), 16), ((ViewAreaAccessor) this).getChunkGridSizeY());

            int index = (gridZ * this.chunkGridSizeX + gridX) * ((ViewAreaAccessor) this).getChunkGridSizeY() + gridY;

            cir.setReturnValue(((ViewAreaAccessor) this).getChunks()[index]);
        }
    }
}