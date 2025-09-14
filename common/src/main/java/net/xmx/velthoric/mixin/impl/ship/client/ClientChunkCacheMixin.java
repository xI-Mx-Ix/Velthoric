/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.mixin.impl.ship.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.xmx.velthoric.compat.SodiumCompatibility;
import net.xmx.velthoric.mixin.util.ship.render.IClientChunkCache;
import net.xmx.velthoric.ship.plot.VxClientPlotManager;
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
public abstract class ClientChunkCacheMixin implements IClientChunkCache {

    @Shadow @Final ClientLevel level;

    @Unique
    private final Long2ObjectMap<LevelChunk> velthoric$shipChunks = new Long2ObjectOpenHashMap<>();

    @Override
    public Long2ObjectMap<LevelChunk> velthoric$getShipChunks() {
        return this.velthoric$shipChunks;
    }

    @Inject(method = "replaceWithPacketData", at = @At("HEAD"), cancellable = true)
    private void velthoric$interceptShipChunkPacket(int x, int z, FriendlyByteBuf buf, CompoundTag tag, Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer, CallbackInfoReturnable<LevelChunk> cir) {
        if (VxClientPlotManager.getInstance().isShipChunk(x, z)) {
            long chunkPosLong = ChunkPos.asLong(x, z);
            LevelChunk chunk = this.velthoric$shipChunks.get(chunkPosLong);

            if (chunk == null) {
                chunk = new LevelChunk(this.level, new ChunkPos(x, z));
                this.velthoric$shipChunks.put(chunkPosLong, chunk);
            }

            chunk.replaceWithPacketData(buf, tag, consumer);
            this.level.onChunkLoaded(new ChunkPos(x, z));
            SodiumCompatibility.onChunkAdded(this.level, x, z);
            cir.setReturnValue(chunk);
        }
    }

    @Inject(method = "getChunk", at = @At("HEAD"), cancellable = true)
    private void velthoric$provideShipChunk(int x, int z, net.minecraft.world.level.chunk.ChunkStatus status, boolean load, CallbackInfoReturnable<LevelChunk> cir) {
        LevelChunk shipChunk = this.velthoric$shipChunks.get(ChunkPos.asLong(x, z));
        if (shipChunk != null) {
            cir.setReturnValue(shipChunk);
        }
    }

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void velthoric$preventShipChunkDrop(int x, int z, CallbackInfo ci) {
        if (VxClientPlotManager.getInstance().isShipChunk(x, z)) {
            if (this.velthoric$shipChunks.remove(ChunkPos.asLong(x, z)) != null) {
                SodiumCompatibility.onChunkRemoved(this.level, x, z);
            }
            ci.cancel();
        }
    }
}