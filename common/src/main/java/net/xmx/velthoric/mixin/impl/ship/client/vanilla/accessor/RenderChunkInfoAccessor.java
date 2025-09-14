package net.xmx.velthoric.mixin.impl.ship.client.vanilla.accessor;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.RenderChunkInfo.class)
public interface RenderChunkInfoAccessor {

    @Invoker(value = "<init>")
    static LevelRenderer.RenderChunkInfo velthoric$new(
            ChunkRenderDispatcher.RenderChunk chunk,
            @Nullable Direction direction,
            int propagationLevel
    ) {
        throw new AssertionError("RenderChunkInfoAccessor Mixin failed to apply");
    }

    @Accessor("chunk")
    ChunkRenderDispatcher.RenderChunk getChunk();
}