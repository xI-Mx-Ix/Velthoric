/*
This file is part of Velthoric.
Licensed under LGPL 3.0.
*/
package net.xmx.velthoric.mixin.impl.event.chunk;

import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.xmx.velthoric.event.api.VxChunkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkMap.class)
public class ChunkMapMixin_VxChunkLoadEvent {

    @Inject(method = "protoChunkToFullChunk", at = @At("RETURN"), cancellable = true)
    private void onProtoChunkToFullChunk(ChunkHolder holder, CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {
        CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> originalFuture = cir.getReturnValue();

        CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> newFuture = originalFuture.thenApply(either -> {
            either.ifLeft(chunkAccess -> {
                if (chunkAccess instanceof LevelChunk levelChunk) {
                    VxChunkEvent.Load.EVENT.invoker().onChunkLoad(new VxChunkEvent.Load(levelChunk));
                }
            });
            return either;
        });

        cir.setReturnValue(newFuture);
    }
}