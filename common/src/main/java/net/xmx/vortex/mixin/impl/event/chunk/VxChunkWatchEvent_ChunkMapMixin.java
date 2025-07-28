package net.xmx.vortex.mixin.impl.event.chunk;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.xmx.vortex.event.api.VxChunkEvent;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public class VxChunkWatchEvent_ChunkMapMixin {

    @Shadow @Final private ServerLevel level;

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

