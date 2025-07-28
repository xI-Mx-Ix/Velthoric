package net.xmx.vortex.mixin.impl.event.chunk;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.xmx.vortex.event.api.VxChunkEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkMap.class)
public class VxChunkUnloadEvent_ChunkMapMixin {

    @Shadow @Final private ServerLevel level;

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
}