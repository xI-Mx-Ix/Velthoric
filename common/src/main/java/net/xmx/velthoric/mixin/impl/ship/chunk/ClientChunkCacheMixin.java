package net.xmx.velthoric.mixin.impl.ship.chunk;

import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData.BlockEntityTagOutput;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.xmx.velthoric.init.VxMainClass;
import net.xmx.velthoric.ship.plot.VxPlotManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {

    @Shadow @Final ClientLevel level;
    @Unique private final LongObjectMap<LevelChunk> velthoric$plotChunks = new LongObjectHashMap<>();

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;", at = @At("HEAD"), cancellable = true)
    public void preGetChunk(int x, int z, ChunkStatus status, boolean load, CallbackInfoReturnable<LevelChunk> cir) {
        if (x >= VxPlotManager.PLOT_AREA_X_CHUNKS) {
            LevelChunk plotChunk = velthoric$plotChunks.get(ChunkPos.asLong(x, z));
            if (plotChunk != null) {
                cir.setReturnValue(plotChunk);
            }
        }
    }

    @Inject(method = "replaceWithPacketData", at = @At("HEAD"), cancellable = true)
    private void onLoadChunkFromPacket(int x, int z, FriendlyByteBuf buf, CompoundTag tag, Consumer<BlockEntityTagOutput> consumer, CallbackInfoReturnable<LevelChunk> cir) {
        if (x >= VxPlotManager.PLOT_AREA_X_CHUNKS) {
            VxMainClass.LOGGER.info("[CLIENT] Received and processing plot chunk packet at {}, {}", x, z);
            long posLong = ChunkPos.asLong(x, z);
            LevelChunk chunk = velthoric$plotChunks.get(posLong);
            if (chunk == null) {
                chunk = new LevelChunk(this.level, new ChunkPos(x, z));
                velthoric$plotChunks.put(posLong, chunk);
            }
            chunk.replaceWithPacketData(buf, tag, consumer);
            this.level.onChunkLoaded(new ChunkPos(x, z));
            cir.setReturnValue(chunk);
        }
    }

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    public void onUnload(int x, int z, CallbackInfo ci) {
        if (x >= VxPlotManager.PLOT_AREA_X_CHUNKS) {
            long posLong = ChunkPos.asLong(x, z);
            if (velthoric$plotChunks.remove(posLong) != null) {
                ci.cancel();
            }
        }
    }
}