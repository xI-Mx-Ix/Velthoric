package net.xmx.vortex.mixin.impl.terrain;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.xmx.vortex.physics.terrain.TerrainSystem;
import net.xmx.vortex.physics.world.VxPhysicsWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.CompletableFuture;

@Mixin(ChunkMap.class)
public abstract class ChunkUnload_ChunkMapMixin {

    @Shadow @Final
    ServerLevel level;

    @Inject(
            method = "scheduleUnload",
            at = @At("HEAD")
    )
    private void onScheduleUnload(long pChunkPos, ChunkHolder pChunkHolder, CallbackInfo ci) {
        CompletableFuture<ChunkAccess> chunkFuture = pChunkHolder.getChunkToSave();
        if (chunkFuture.isDone() && !chunkFuture.isCompletedExceptionally()) {
            ChunkAccess chunkAccess = chunkFuture.getNow(null);
            if (chunkAccess instanceof LevelChunk levelChunk) {
                ResourceKey<Level> dimensionKey = this.level.dimension();
                TerrainSystem terrainSystem = VxPhysicsWorld.getTerrainSystem(dimensionKey);

                if (terrainSystem != null) {
                    terrainSystem.onChunkUnloaded(levelChunk.getPos());
                }
            }
        }
    }
}