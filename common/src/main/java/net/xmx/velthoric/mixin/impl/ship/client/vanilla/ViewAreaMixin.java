package net.xmx.velthoric.mixin.impl.ship.client.vanilla;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.xmx.velthoric.mixin.util.ship.render.vanilla.IViewArea;
import net.xmx.velthoric.ship.plot.VxClientPlotManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ViewArea.class)
public class ViewAreaMixin implements IViewArea {

    @Shadow @Final protected Level level;
    @Shadow protected int chunkGridSizeY;

    @Unique private ChunkRenderDispatcher velthoric$chunkRenderDispatcher;
    @Unique private final Long2ObjectMap<ChunkRenderDispatcher.RenderChunk[]> velthoric$shipRenderChunks = new Long2ObjectOpenHashMap<>();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void velthoric$onInit(ChunkRenderDispatcher chunkRenderDispatcher, Level level, int i, LevelRenderer levelRenderer, CallbackInfo ci) {
        this.velthoric$chunkRenderDispatcher = chunkRenderDispatcher;
    }

    @Inject(method = "setDirty", at = @At("HEAD"), cancellable = true)
    private void velthoric$onSetDirty(int sectionX, int sectionY, int sectionZ, boolean important, CallbackInfo ci) {
        if (VxClientPlotManager.getInstance().isShipChunk(sectionX, sectionZ)) {
            int yIndex = sectionY - this.level.getMinSection();
            if (yIndex < 0 || yIndex >= this.chunkGridSizeY) {
                return;
            }

            long posAsLong = ChunkPos.asLong(sectionX, sectionZ);
            ChunkRenderDispatcher.RenderChunk[] renderChunks = velthoric$shipRenderChunks.computeIfAbsent(
                    posAsLong, k -> new ChunkRenderDispatcher.RenderChunk[this.chunkGridSizeY]
            );

            ChunkRenderDispatcher.RenderChunk renderChunk = renderChunks[yIndex];
            if (renderChunk == null) {
                int chunkId = -1;
                renderChunk = this.velthoric$chunkRenderDispatcher.new RenderChunk(
                        chunkId, sectionX << 4, sectionY << 4, sectionZ << 4
                );
                renderChunks[yIndex] = renderChunk;
            }

            renderChunk.setDirty(important);
            RenderRegionCache regionCache = new RenderRegionCache();
            ChunkRenderDispatcher.RenderChunk.ChunkCompileTask compileTask = renderChunk.createCompileTask(regionCache);
            this.velthoric$chunkRenderDispatcher.schedule(compileTask);

            ci.cancel();
        }
    }

    @Inject(method = "getRenderChunkAt", at = @At("HEAD"), cancellable = true)
    private void velthoric$getShipRenderChunk(BlockPos pos, CallbackInfoReturnable<ChunkRenderDispatcher.RenderChunk> cir) {
        int chunkX = Mth.floorDiv(pos.getX(), 16);
        int chunkZ = Mth.floorDiv(pos.getZ(), 16);

        if (VxClientPlotManager.getInstance().isShipChunk(chunkX, chunkZ)) {
            int chunkY = Mth.floorDiv(pos.getY() - this.level.getMinBuildHeight(), 16);
            if (chunkY < 0 || chunkY >= this.chunkGridSizeY) {
                cir.setReturnValue(null);
                return;
            }

            ChunkRenderDispatcher.RenderChunk[] renderChunks = velthoric$shipRenderChunks.get(ChunkPos.asLong(chunkX, chunkZ));
            cir.setReturnValue(renderChunks != null ? renderChunks[chunkY] : null);
        }
    }

    @Override
    public void velthoric$unloadChunk(int chunkX, int chunkZ) {
        long pos = ChunkPos.asLong(chunkX, chunkZ);
        ChunkRenderDispatcher.RenderChunk[] chunks = velthoric$shipRenderChunks.remove(pos);
        if (chunks != null) {
            for (ChunkRenderDispatcher.RenderChunk chunk : chunks) {
                if (chunk != null) {
                    chunk.releaseBuffers();
                }
            }
        }
    }

    @Inject(method = "releaseAllBuffers", at = @At("TAIL"))
    private void velthoric$releaseShipBuffers(CallbackInfo ci) {
        for (ChunkRenderDispatcher.RenderChunk[] chunks : velthoric$shipRenderChunks.values()) {
            for (ChunkRenderDispatcher.RenderChunk chunk : chunks) {
                if (chunk != null) {
                    chunk.releaseBuffers();
                }
            }
        }
        velthoric$shipRenderChunks.clear();
    }
}