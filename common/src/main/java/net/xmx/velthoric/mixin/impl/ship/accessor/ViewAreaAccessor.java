package net.xmx.velthoric.mixin.impl.ship.accessor;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ViewArea.class)
public interface ViewAreaAccessor {

    @Accessor("chunks")
    ChunkRenderDispatcher.RenderChunk[] getChunks();

    @Accessor("chunkGridSizeX")
    int getChunkGridSizeX();

    @Accessor("chunkGridSizeY")
    int getChunkGridSizeY();

    @Accessor("chunkGridSizeZ")
    int getChunkGridSizeZ();

    @Invoker("getRenderChunkAt")
    ChunkRenderDispatcher.RenderChunk callGetRenderChunkAt(BlockPos pos);
}