/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.event.chunk;

import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.xmx.velthoric.event.api.VxChunkEvent;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * @author xI-Mx-Ix
 */
@Mixin(ChunkMap.class)
public class MixinChunkMap_VxChunkEvent {

    @Shadow @Final ServerLevel level;

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

    @Inject(method = "scheduleUnload", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;thenAcceptAsync(Ljava/util/function/Consumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private void onScheduleUnload(long chunkPos, ChunkHolder chunkHolder, CallbackInfo ci) {
        CompletableFuture<ChunkAccess> future = chunkHolder.getChunkToSave();

        future.thenAcceptAsync(chunkAccess -> {
            if (chunkAccess instanceof LevelChunk levelChunk) {
                level.getServer().execute(() -> {
                    VxChunkEvent.Unload.EVENT.invoker().onChunkUnload(new VxChunkEvent.Unload(levelChunk));
                });
            }
        }, level.getServer());
    }

    @Inject(method = "updateChunkTracking", at = @At("HEAD"))
    private void onUpdateChunkTracking(ServerPlayer player, ChunkPos pos, MutableObject<?> packetRef, boolean watch, boolean currentlyWatching, CallbackInfo ci) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk != null) {
            if (watch && !currentlyWatching) {
                VxChunkEvent.Watch.EVENT.invoker().onChunkWatch(new VxChunkEvent.Watch(chunk, player));
            } else if (!watch && currentlyWatching) {
                VxChunkEvent.Unwatch.EVENT.invoker().onChunkUnwatch(new VxChunkEvent.Unwatch(chunk, player));
            }
        }
    }
}
